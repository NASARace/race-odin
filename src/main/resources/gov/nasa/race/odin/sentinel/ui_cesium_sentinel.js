/*
 * Copyright (c) 2022, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The RACE - Runtime for Airspace Concept Evaluation platform is licensed
 * under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* this is the client-side code to display Sentinel related information in a Cesium route */

import * as config from "./config.js";
import * as ws from "./ws.js";
import { SkipList, CircularBuffer } from "./ui_data.js";
import * as util from "./ui_util.js";
import * as ui from "./ui.js";
import * as uiCesium from "./ui_cesium.js";


var sentinelDataSource = new Cesium.CustomDataSource("sentinel");
var sentinelView = undefined;
var sentinelEntries = new Map();
var sentinelList = new SkipList( // id-sorted display list for trackEntryView
    3, // max depth
    (a, b) => a.id < b.id, // sort function
    (a, b) => a.id == b.id // identity function
);

var selectedSentinelEntry = undefined;

var sentinelImageView = undefined;
var sentinelFireView = undefined;
var sentinelSmokeView = undefined;
var sentinelGasView = undefined;
var sentinelThermoView = undefined;
var sentinelAnemoView = undefined;
var sentinelVocView = undefined;
var sentinelAccelView = undefined;
var sentinelGpsView = undefined;

var maxHistory = config.sentinel.maxHistory;

class SentinelAssets {
    constructor(symbol, details) {
        this.symbol = symbol; // billboard
        this.details = details; // gas coverage, camera-coverage, wind
    }

    updatePosition(lat,lon) {
        let pos = Cesium.Cartesian3.fromDegrees(lon, lat)
        if (this.symbol) this.symbol.position = pos;
        if (this.details) this.details.position = pos;
    }

    showAssets (cond) {
        // TODO - is this right? we should just add/remove the sentinelDataSource
        if (this.symbol) this.symbol.show = cond;
        if (this.details) this.details.show = cond;
    }
}

class SentinelEntry {
    constructor(sentinel) {
        this.id = sentinel.deviceId;
        this.sentinel = sentinel;
        this.assets = null;
    }

    hasFire() {
        let fire = this.sentinel.fire;
        return (fire && fire.length > 0 && fire[0].fire.fireProb > 0.5);
    }

    hasSmoke() {
        let smoke = this.sentinel.smoke;
        (smoke && smoke.length > 0 && smoke[0].smoke.smokeProb > 0.5);
    }

    alertStatus() {
        if (this.hasFire()){
            if (this.hasSmoke()) return ui.createImage("sentinel-asset/fire-smoke");
            else return  ui.createImage("sentinel-asset/fire");
        } else if (this.hasSmoke()) {
            return ui.createImage("sentinel-asset/smoke");
        } else {
            return "";
        }
    }

    fireStatus() {
        let fire = this.sentinel.fire;
        //return (fire && fire.length > 0) ? util.f_1.format(fire[0].fire.fireProb) : "-";
        return (fire && fire.length > 0) ? fire[0].fire.fireProb.toFixed(2) : "-";
    }

    smokeStatus() {
        let smoke = this.sentinel.smoke;
        return (smoke && smoke.length > 0) ? smoke[0].smoke.smokeProb.toFixed(2) : "-";
    }

    imageStatus() {
        let images = this.sentinel.image;
        return (images && images.length > 0) ? images.length : "-";
    }

    showAssets(cond) {
        if (this.assets) this.assets.showAssets(cond);
    }

    position() {
        let gps = this.sentinel.gps;
        if (gps && gps.length > 0) {
            let lat = gps[0].gps.latitude;
            let lon = gps[0].gps.longitude;
            return Cesium.Cartesian3.fromDegrees(lon, lat);
        } else {
            return Cesium.Cartesian3.fromDegrees(0, 0);  // TODO - we should move this out of sight
        }
    }

    lastCartographic (height=0.0) {
        let gps = this.sentinel.gps;
        if (gps && gps.length > 0) {
            let lat = util.toRadians(gps[0].gps.latitude);
            let lon = util.toRadians(gps[0].gps.longitude);
            return new Cesium.Cartographic(lon,lat,height);
        } else {
            return null;
        }
    }

    temperature() {
        let thermo = this.sentinel.thermometer;
        if (thermo && thermo.length > 0) {
            return thermo[0].thermometer.temperature;
        }
    }

    humidity() {
        let gas = this.sentinel.gas;
        if (gas && gas.length > 0) {
            return gas[0].gas.humidity;
        }
    }

    windSpeed() {
        let anemo = this.sentinel.anemometer;
        if (anemo && anemo.length > 0) {
            return anemo[0].anemometer.speed;
        }
    }

    windDirection() {
        let anemo = this.sentinel.anemometer;
        if (anemo && anemo.length > 0) {
            return anemo[0].anemometer.angle;
        }
    }
}


ui.registerLoadFunction(function initialize() {
    uiCesium.addDataSource(sentinelDataSource);
    sentinelView = initSentinelView();

    sentinelImageView = initSentinelImagesView();
    sentinelAccelView = initSentinelAccelView();
    sentinelAnemoView = initSentinelAnemoView();
    sentinelThermoView = initSentinelThermoView();
    sentinelFireView = initSentinelFireView();
    sentinelSmokeView = initSentinelSmokeView();
    sentinelGasView = initSentinelGasView();
    sentinelVocView = initSentinelVocView();
    sentinelGpsView = initSentinelGpsView();

    initSentinelCmdList();

    uiCesium.setEntitySelectionHandler(sentinelSelection);
    ws.addWsHandler(config.wsUrl, handleWsSentinelMessages);

    uiCesium.initLayerPanel("sentinel", config.sentinel, showSentinels);
    console.log("ui_cesium_sentinel initialized");
});

function showSentinels (cond) {
    sentinelEntries.forEach( e=> e.showAssets(cond));
    uiCesium.requestRender();
}

function initSentinelView() {
    let view = ui.getList("sentinel.list");
    if (view) {
        ui.setListItemDisplayColumns(view, ["fit", "header"], [
            { name: "", width: "2rem", attrs: [], map: e => e.alertStatus() },
            { name: "id", width: "4rem", attrs: ["alignLeft"], map: e => e.sentinel.deviceName },
            { name: "fire", tip: "fire probability [0..1]", width: "4rem", attrs: ["fixed", "alignRight"], map: e => e.fireStatus() },
            { name: "smoke", tip: "smoke probability [0..1]", width: "4rem", attrs: ["fixed", "alignRight"], map: e => e.smokeStatus() },
            { name: "img", tip: "number of available images", width: "4rem", attrs: ["fixed", "alignRight"], map: e => e.imageStatus() },
            ui.listItemSpacerColumn(),
            { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.sentinel.timeRecorded) }
        ]);
    }
    return view;
}

function initListView (id, colSpecs) {
    let view = ui.getList(id);
    if (view) {
        ui.setListItemDisplayColumns(view, ["fit", "header"], colSpecs);
    }
    return view;
}
function initSentinelFireView() {
    return initListView( "sentinel.fire.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "prob", tip: "fire probability [0..1]", width: "6rem", attrs: ["fixed", "alignRight"], map: e => e.fire.fireProb.toFixed(2) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);
}
function initSentinelSmokeView() {
    return initListView( "sentinel.smoke.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "prob", tip: "smoke probability [0..1]", width: "6rem", attrs: ["fixed", "alignRight"], map: e => e.smoke.smokeProb.toFixed(2) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);
}
function initSentinelGasView() {
    return initListView( "sentinel.gas.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "gas", tip: "gas resistance [Ω]", width: "3rem", attrs: ["fixed", "alignRight"], map: e => e.gas.gas },
        { name: "hum", tip: "humidity [%]", width: "4.5rem", attrs: ["fixed", "alignRight"], map: e => util.f_2.format(e.gas.humidity) },
        { name: "pres", tip: "pressure [hPa]", width: "4.5rem", attrs: ["fixed", "alignRight"], map: e => util.f_1.format(e.gas.pressure) },
        { name: "alt", tip: "altitude [ft]", width: "3rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.gas.altitude) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);
}
function initSentinelThermoView() {
    return initListView( "sentinel.thermo.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "temp", tip: "temperature [°C]", width: "6rem", attrs: ["fixed", "alignRight"], map: e => util.f_1.format(e.thermometer.temperature) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);   
}
function initSentinelAnemoView() {
    return initListView( "sentinel.anemo.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "dir", tip: "wind direction [°]", width: "4rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.anemometer.angle) },
        { name: "spd", tip: "wind speed [m/s]", width: "6rem", attrs: ["fixed", "alignRight"], map: e => util.f_2.format(e.anemometer.speed) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);  
}
function initSentinelVocView() {
    return initListView( "sentinel.voc.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "tvoc", tip: "total volatile organic compounds [ppb]", width: "3rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.voc.TVOC) },
        { name: "eCO2", tip: "estimated CO₂ concentration [ppm]", width: "4rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.voc.eCO2) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);   
}
function initSentinelAccelView() {
    return initListView( "sentinel.accel.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "ax", tip: "x-acceleration [m/s²]", width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_3.format(e.accelerometer.ax) },
        { name: "ay", tip: "y-acceleration [m/s²]",width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_3.format(e.accelerometer.ay) },
        { name: "az", tip: "z-acceleration [m/s²]",width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_3.format(e.accelerometer.az) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]); 
}
function initSentinelGpsView() {
    return initListView( "sentinel.gps.list", [
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "lat", tip: "latitude", width: "7rem", attrs: ["fixed", "alignRight"], map: e => util.f_5.format(e.gps.latitude) },
        { name: "lon", tip: "longitude", width: "7rem", attrs: ["fixed", "alignRight"], map: e => util.f_5.format(e.gps.longitude) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);  
}
function initSentinelImagesView() {
    return initListView( "sentinel.image.list", [
        { name: "", width: "2rem", attrs: [], map: e => ui.createCheckBox(e.window, toggleShowImage, null) },
        { name: "sen", tip: "sensor number", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "type", tip: "ir: infrared, vis: visible", width: "2rem", attrs: [], map: e => e.image.isInfrared ? "ir" : "vis" },
        ui.listItemSpacerColumn(),
        { name: "date", width: "9rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalMDHMSString(e.timeRecorded) }
    ]);
}

function initSentinelCmdList() {
    let view = ui.getList("sentinel.diag.cmdList");
    if (view) {
        ui.setListItemDisplayColumns( view, ["fit"], [
            { name: "template", tip: "name of command to instantiate", width: "26rem", attrs:[], map: e => e }
        ]);

        ui.setListItems(view, Array.from(diagnosticCommands.keys()));
    }
}

function toggleShowImage(event) {
    let cb = ui.getCheckBox(event.target);
    if (cb) {
        let e = ui.getListItemOfElement(cb);
        if (e) {
            if (e.window) {
                ui.removeWindow(e.window);
                e.window = null;
            } else {
                setTimeout(() => { // otherwise the mouseUp will put the focus back on sentinelsView
                    let w = ui.createWindow(e.image.filename, false, () => {
                        e.window = undefined;
                        ui.updateListItem(sentinelImageView, e);
                    });
                    let img = ui.createImage(e.image.filename, "waiting for image..", config.sentinel.imageWidth);
                    ui.addWindowContent(w, img);
                    //ui.setWindowResizable(w, true);
                    ui.addWindow(w);
                    ui.setWindowLocation(w, event.clientX + 10, event.clientY + 10);
                    ui.showWindow(w);

                    e.window = w;
                }, 0);
            }
        }
    }
}

function sentinelSelection() {
    let sel = uiCesium.getSelectedEntity();
    if (sel && sel._uiSentinelEntry) {
        if (sel._uiSentinelEntry !== selectedSentinelEntry) {
            ui.setSelectedListItem(sentinelView, sel._uiSentinelEntry);
        }
    }
}

function handleWsSentinelMessages(msgType, msg) {
    switch (msgType) {
        case "sentinels":
            handleSentinelsMessage(msg.sentinels);
            return true;
        case "sentinelUpdates":
            handleSentinelUpdatesMessage(msg.sentinelUpdates);
            return true;
        case "cmdResponse":
            logResponse(msg.cmdResponse);
        default:
            return false;
    }
}

function handleSentinelsMessage(sentinels) {
    sentinelEntries.clear();
    sentinels.forEach(sentinel => addSentinelEntry(sentinel));
}

function addSentinelEntry(sentinel) {
    let e = new SentinelEntry(sentinel);

    sentinelEntries.set(sentinel.deviceId, e);
    let idx = sentinelList.insert(e);
    ui.insertListItem(sentinelView, e, idx);

    if (sentinel.gps) e.assets = createAssets(e);
    checkFireAsset(e);
}

function handleSentinelUpdatesMessage(sentinelUpdates) {
    sentinelUpdates.forEach(su => {
        let r = su.sentinelReading;
        let id = r.deviceId;
        let e = sentinelEntries.get(id);

        if (e) {
            let sentinel = e.sentinel;
            if (r.fire) {
                updateSentinelReadings(e, 'fire', r, sentinelFireView);
                checkFireAsset(e);
            }
            else if (r.smoke) updateSentinelReadings(e, 'smoke', r, sentinelSmokeView);
            else if (r.image) updateSentinelReadings(e, 'image', r, sentinelImageView);
            else if (r.anemometer) {
                updateSentinelReadings(e, 'anemometer', r, sentinelAnemoView);
                updateDetails(e);
            }
            else if (r.gas) {
                updateSentinelReadings(e, 'gas', r, sentinelGasView);
                updateDetails(e);
            }
            else if (r.voc) updateSentinelReadings(e, 'voc', r, sentinelVocView);
            else if (r.accelerometer) updateSentinelReadings(e, 'accelerometer', r, sentinelAccelView);
            else if (r.gps) updateSentinelReadings(e, 'gps', r, sentinelGpsView);
            else if (r.thermometer) {
                updateSentinelReadings(e, 'thermometer', r, sentinelThermoView);
                updateDetails(e);
            }
        }
    });
}


function updateSentinelReadings (sentinelEntry, memberName, newReading, view) {
    let sentinel = sentinelEntry.sentinel;
    let readings = sentinel[memberName];

    sentinel.timeRecorded = newReading.timeRecorded;

    if (readings) {
        if (readings.length >= maxHistory) {
            readings.copyWithin(1,0,readings.length-1);
            readings[0] = newReading;
        } else {
            readings.unshift(newReading);
        }
        readings.sort( (a,b) => b.timeRecorded - a.timeRecorded); // in case records come out of order
    } else {
        readings = [newReading];
        sentinel[memberName] = readings;
    }

    if (sentinelEntry == selectedSentinelEntry)  ui.setListItems(view, readings);
    ui.updateListItem(sentinelView, sentinelEntry);
}

function checkFireAsset(e) {
    if (e.hasFire() || e.hasSmoke()) {
        if (e.assets) {
            e.assets.symbol.billboard.color = config.sentinel.alertColor;

            /*
            if (!e.assets.fire) {
                e.assets.fire = createFireAsset(e);
                if (e.assets.fire) e.assets.fire.show = true;
            } else {
                // update fire location/probability
            }
            */
            uiCesium.requestRender();
        }
    }
}

function createAssets(sentinelEntry) {
    return new SentinelAssets(
        createSymbolAsset(sentinelEntry),
        createDetailAsset(sentinelEntry)
    );
}

function createSymbolAsset(sentinelEntry) {
    let sentinel = sentinelEntry.sentinel;

    let entity = new Cesium.Entity({
        id: sentinel.deviceId,
        position: sentinelEntry.position(),
        billboard: {
            image: 'sentinel-asset/sentinel',
            distanceDisplayCondition: config.sentinel.billboardDC,
            color: config.sentinel.color,
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        },
        label: {
            text: sentinel.deviceName,
            scale: 0.8,
            horizontalOrigin: Cesium.HorizontalOrigin.LEFT,
            verticalOrigin: Cesium.VerticalOrigin.TOP,
            font: config.sentinel.labelFont,
            fillColor: config.sentinel.color,
            showBackground: true,
            backgroundColor: config.sentinel.labelBackground,
            pixelOffset: config.sentinel.labelOffset,
            distanceDisplayCondition: config.sentinel.labelDC,
        },
        point: {
            pixelSize: config.sentinel.pointSize,
            color: config.sentinel.color,
            outlineColor: config.sentinel.pointOutlineColor,
            outlineWidth: config.sentinel.pointOutlineWidth,
            distanceDisplayCondition: config.sentinel.pointDC, 
        }
    });
    entity._uiSentinelEntry = sentinelEntry; // backlink

    sentinelDataSource.entities.add(entity);
    return entity;
}


function createDetailAsset (sentinelEntry) {
    let cfg = config.sentinel;

    let entity = new Cesium.Entity({
        id: sentinelEntry.id + "-info",
        position: sentinelEntry.position(),
        label: {
            text: sentinelInfoText(sentinelEntry),
            font: cfg.infoFont,
            scale: 0.8,
            horizontalOrigin: Cesium.HorizontalOrigin.LEFT,
            verticalOrigin: Cesium.VerticalOrigin.TOP,
            fillColor: cfg.color,
            showBackground: true,
            backgroundColor: cfg.labelBackground, // alpha does not work against model
            outlineColor: cfg.color,
            outlineWidth: 1,
            pixelOffset: cfg.infoOffset,
            distanceDisplayCondition: cfg.infoDC
        }
    });

    sentinelDataSource.entities.add(entity);
    return entity;
}

function sentinelInfoText (se) {
    //console.log(se);
    let value = (v,f) => v ? f(v) : '-';

    let temp = value(se.temperature(), (v)=>Math.round(v));
    let humidity = value(se.humidity(), (v)=>Math.round(v));
    let windDir = value(se.windDirection(), (v)=>Math.round(v));
    let windSpd = value(se.windSpeed(), (v)=>util.f_1.format(v));

    return `${temp} °C\n${humidity} %\n${windDir} °\n${windSpd} m/s`
}

function updateDetails (se) {
    if (se.assets.details){
        se.assets.details.label.text = sentinelInfoText(se);
        uiCesium.requestRender();
    }
}

ui.exportToMain(function selectSentinel(event) {
    let e = event.detail.curSelection;
    if (e) {
        selectedSentinelEntry = e;
        let sentinel = e.sentinel;
        setDataViews(sentinel);
    } else {
        selectedSentinelEntry = undefined;
        clearDataViews();
    }
});

ui.exportToMain(function zoomToSentinel(event) {
    let lv = ui.getList(event);
    if (lv) {
        let se = ui.getSelectedListItem(lv);
        if (se) {
            let pos = se.lastCartographic(config.sentinel.zoomHeight);
            uiCesium.zoomTo( Cesium.Cartographic.toCartesian(pos));
            uiCesium.setSelectedEntity(se.assets.symbol);
        }
    }
});

function setDataViews(sentinel) {
    ui.setListItems(sentinelImageView, sentinel.image);
    ui.setListItems(sentinelAccelView, sentinel.accelerometer);
    ui.setListItems(sentinelAnemoView, sentinel.anemometer);
    ui.setListItems(sentinelThermoView, sentinel.thermometer);
    ui.setListItems(sentinelFireView, sentinel.fire);
    ui.setListItems(sentinelSmokeView, sentinel.smoke);
    ui.setListItems(sentinelGasView, sentinel.gas);
    ui.setListItems(sentinelVocView, sentinel.voc);
    ui.setListItems(sentinelGpsView, sentinel.gps);
}

function clearDataViews() {
    ui.clearList(sentinelImageView);
    ui.clearList(sentinelAccelView);
    ui.clearList(sentinelAnemoView);
    ui.clearList(sentinelThermoView);
    ui.clearList(sentinelFireView);
    ui.clearList(sentinelSmokeView);
    ui.clearList(sentinelGasView);
    ui.clearList(sentinelVocView);
    ui.clearList(sentinelGpsView);
}

ui.exportToMain(function selectImage(event) {
    let e = event.detail.curSelection;
    if (e) {
        if (e.window) {
            ui.raiseWindowToTop(e.window);
        }
    }
});

//--- diagnostics

let diagnosticCommands = new Map([
    ["upload fire image", uploadFireImageCmd], 
    ["turn lights on", turnLightsOnCmd], 
    ["turn lights off", turnLightsOffCmd]
]);


function uploadFireImageCmd() {
    if (selectedSentinelEntry) {
        return `{"event": "command",\n  "data":{ "action": "inject",\n   "deviceIds": ["${selectedSentinelEntry.id}"]}}`;
    } else {
        alert("please select device");
    }
}

function turnLightsOnCmd() {
    if (selectedSentinelEntry) {
        return `{"event": "command",\n  "data":{ "action": "switch",\n   "subject": "external-lights",\n  "state": "on",\n     "deviceIds": ["${selectedSentinelEntry.id}"]}}`;
    } else {
        alert("please select device");
    }
}

function turnLightsOffCmd() {
    if (selectedSentinelEntry) {
        return `{"event": "command",\n  "data":{ "action": "switch",\n   "subject": "external-lights",\n  "state": "off",\n     "deviceIds": ["${selectedSentinelEntry.id}"]}}`;
    } else {
        alert("please select device");
    }
}

ui.exportToMain(function selectSentinelCmd(event) {
    let cmdDescr = ui.getSelectedListItem(event.target);

    let cmdFunc = diagnosticCommands.get(cmdDescr);
    if (cmdFunc) {
        let cmd = cmdFunc();
        if (cmd) {
            ui.setTextAreaContent("sentinel.diag.cmd", cmd);
        }
    } else {
        console.log("ignoring unknown command: ", cmdDescr);
    }
});

ui.exportToMain(function sendSentinelCmd() {
    let cmd = ui.getTextAreaContent("sentinel.diag.cmd");
    if (cmd){
        try {
            let o = JSON.parse(cmd);
            let json = JSON.stringify(o);
            console.log("sending command: ", json);
            ws.sendWsMessage(json);

        } catch (error) {
            alert("malformed command: ", error);
        }
    }
});

ui.exportToMain(function clearSentinelHistory() {
    ui.setTextAreaContent("sentinel.diag.log", null);
});


function logResponse (response) {
    if (response) {
        let logView = ui.getTextArea("sentinel.diag.log");
        let text = ui.getTextAreaContent(logView);
        let now = Date.now();

        if (text) {
            text = `${util.toLocalTimeString(now)} : ${response}\n${text}`;
        } else {
            text = `${util.toLocalTimeString(now)} : ${response}`;
        }

        ui.setTextAreaContent(logView, text);
    }
}