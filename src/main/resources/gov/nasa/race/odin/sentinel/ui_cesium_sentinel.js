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
        this.fire = undefined;
    }

    updatePosition(lat,lon) {
        if (this.symbol) {
            this.symbol.position = Cesium.Cartesian3.fromDegrees(lon, lat)
        }
    }

    showAssets (cond) {
        // TODO - is this right? we should just add/remove the sentinelDataSource
        if (this.symbol) this.symbol.show = cond;
        if (this.details) this.details.show = cond;
        if (this.fire) this.fire.show = cond;
    }
}

class SentinelEntry {
    constructor(sentinel) {
        this.id = sentinel.deviceId;
        this.sentinel = sentinel;
        this.showDetails = false;

        this.assets = null;
    }

    alertStatus() {
        let fire = this.sentinel.fire;
        let smoke = this.sentinel.smoke;

        let hasFire = (fire && fire.length > 0 && fire[0].fire.fireProb > 0.5);
        let hasSmoke = (smoke && smoke.length > 0 && smoke[0].smoke.smokeProb > 0.5);

        if (hasFire){
            if (hasSmoke) return ui.createImage("sentinel-asset/fire-smoke");
            else return  ui.createImage("sentinel-asset/fire");
        } else if (hasSmoke) {
            return ui.createImage("sentinel-asset/smoke");
        } else {
            return "";
        }
    }

    fireStatus() {
        let fire = this.sentinel.fire;
        //return (fire && fire.length > 0) ? util.f_1.format(fire[0].fire.fireProb) : "-";
        return (fire && fire.length > 0) ? Math.abs(fire[0].fire.fireProb.toFixed(2)) : "-";
    }

    smokeStatus() {
        let smoke = this.sentinel.smoke;
        return (smoke && smoke.length > 0) ? Math.abs(smoke[0].smoke.smokeProb.toFixed(2)) : "-";
    }

    imageStatus() {
        let images = this.sentinel.image;
        return (images && images.length > 0) ? images.length : "-";
    }

    setShowDetails(showIt) {
        this.showDetails = showIt;

        let assets = this.assets;
        if (assets) {
            if (assets.details) assets.details.show = showIt;
            if (assets.fire) assets.fire.show = showIt;
        }
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

    firePosition() {  // TODO - this is just a mockup
        let gps = this.sentinel.gps;
        if (gps && gps.length > 0) {
            let lat = gps[0].gps.latitude - 0.002;
            let lon = gps[0].gps.longitude - 0.002;
            return Cesium.Cartesian3.fromDegrees(lon, lat);
        } else {
            return Cesium.Cartesian3.fromDegrees(0, 0);  // TODO - we should move this out of sight
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
            { name: "", width: "2rem", attrs: [], map: e => ui.createCheckBox(e.showDetails, toggleShowDetails, null) },
            { name: "id", width: "4rem", attrs: ["alignLeft"], map: e => e.sentinel.deviceName },
            { name: "", width: "1.5rem", attrs: [], map: e => e.alertStatus() },
            { name: "fire", width: "4rem", attrs: ["fixed", "alignRight"], map: e => e.fireStatus() },
            { name: "smoke", width: "4rem", attrs: ["fixed", "alignRight"], map: e => e.smokeStatus() },
            { name: "img", width: "4rem", attrs: ["fixed", "alignRight"], map: e => e.imageStatus() },
            { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.sentinel.timeRecorded) }
        ]);
    }
    return view;
}

function toggleShowDetails(event) {
    let cb = ui.getCheckBox(event.target);
    if (cb) {
        let e = ui.getListItemOfElement(cb);
        if (e) {
            e.setShowDetails(ui.isCheckBoxSelected(cb));
        }
    }
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
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "prob", width: "6rem", attrs: ["fixed", "alignRight"], map: e => util.f_2.format(e.fire.fireProb) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);
}
function initSentinelSmokeView() {
    return initListView( "sentinel.smoke.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "prob", width: "6rem", attrs: ["fixed", "alignRight"], map: e => util.f_2.format(e.smoke.smokeProb) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);
}
function initSentinelGasView() {
    return initListView( "sentinel.gas.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "hum", width: "3rem", attrs: ["fixed", "alignRight"], map: e => util.f_2.format(e.gas.hummidity) }, // SIC! humidity
        { name: "pres", width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_1.format(e.gas.pressure) },
        { name: "alt", width: "4rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.gas.altitude) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);
}
function initSentinelThermoView() {
    return initListView( "sentinel.thermo.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "temp", width: "6rem", attrs: ["fixed", "alignRight"], map: e => util.f_1.format(e.thermometer.temperature) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);   
}
function initSentinelAnemoView() {
    return initListView( "sentinel.anemo.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "dir", width: "4rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.anemometer.angle) },
        { name: "spd", width: "6rem", attrs: ["fixed", "alignRight"], map: e => util.f_2.format(e.anemometer.speed) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);  
}
function initSentinelVocView() {
    return initListView( "sentinel.voc.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "tvoc", width: "3rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.voc.TVOC) },
        { name: "eco2", width: "4rem", attrs: ["fixed", "alignRight"], map: e => util.f_0.format(e.voc.eCO2) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);   
}
function initSentinelAccelView() {
    return initListView( "sentinel.accel.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "ax", width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_3.format(e.accelerometer.ax) },
        { name: "ay", width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_3.format(e.accelerometer.ay) },
        { name: "az", width: "5rem", attrs: ["fixed", "alignRight"], map: e => util.f_3.format(e.accelerometer.az) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]); 
}
function initSentinelGpsView() {
    return initListView( "sentinel.gps.list", [
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "lat", width: "7rem", attrs: ["fixed", "alignRight"], map: e => util.f_5.format(e.gps.latitude) },
        { name: "lon", width: "7rem", attrs: ["fixed", "alignRight"], map: e => util.f_5.format(e.gps.longitude) },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);  
}
function initSentinelImagesView() {
    return initListView( "sentinel.image.list", [
        { name: "", width: "2rem", attrs: [], map: e => ui.createCheckBox(e.window, toggleShowImage, null) },
        { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
        { name: "type", width: "2rem", attrs: [], map: e => e.image.isInfrared ? "ir" : "vis" },
        ui.listItemSpacerColumn(),
        { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
    ]);
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
            else if (r.anemometer) updateSentinelReadings(e, 'anemometer', r, sentinelAnemoView);
            else if (r.gas) updateSentinelReadings(e, 'gas', r, sentinelGasView);
            else if (r.voc) updateSentinelReadings(e, 'voc', r, sentinelVocView);
            else if (r.accelerometer) updateSentinelReadings(e, 'accelerometer', r, sentinelAccelView);
            else if (r.gps) updateSentinelReadings(e, 'gps', r, sentinelGpsView);
            else if (r.thermometer) updateSentinelReadings(e, 'thermometer', r, sentinelThermoView);
        }
    });
}


function updateSentinelReadings (sentinelEntry, memberName, newReading, view) {
    let sentinel = sentinelEntry.sentinel;
    let readings = sentinel[memberName];

    sentinel.timeRecorded = newReading.timeRecorded;
    ui.updateListItem(sentinelView, sentinelEntry);

    if (readings) {
        if (readings.length >= maxHistory) {
            readings.copyWithin(1,0,readings.length-1);
            readings[0] = newReading;
        } else {
            readings.unshift(newReading);
        }
    } else {
        readings = [newReading];
        sentinel[memberName] = readings;
    }

    if (sentinelEntry == selectedSentinelEntry)  ui.setListItems(view, readings);
}

function checkFireAsset(e) {
    let sentinel = e.sentinel;

    if (sentinel.fire && sentinel.fire.fireProb > 0.5) {
        if (e.assets) {
            e.assets.symbol.billboard.color = config.sentinel.alertColor;

            if (!e.assets.fire) {
                e.assets.fire = createFireAsset(e);
                if (e.assets.fire) e.assets.fire.show = true;
            } else {
                // update fire location/probability
            }
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

// TODO - this is a mockup
function createDetailAsset(sentinelEntry) {
    let sentinel = sentinelEntry.sentinel;

    let entity = new Cesium.Entity({
        id: sentinel.deviceId + "-details",
        distanceDisplayCondition: config.sentinel.billboardDC,
        position: sentinelEntry.position(),
        ellipse: {
            semiMinorAxis: 500,
            semiMajorAxis: 1000,
            rotation: Cesium.Math.toRadians(45),
            material: Cesium.Color.ALICEBLUE.withAlpha(0.3),
        },
        show: false // only when selected
    });
    sentinelDataSource.entities.add(entity);
    return entity;
}

// TODO - this is a mockup
function createFireAsset(sentinelEntry) {
    let sentinel = sentinelEntry.sentinel;

    let entity = new Cesium.Entity({
        id: sentinel.deviceId + "-fire",
        distanceDisplayCondition: config.sentinel.billboardDC,
        position: sentinelEntry.firePosition(),
        ellipse: {
            semiMinorAxis: 25,
            semiMajorAxis: 50,
            rotation: Cesium.Math.toRadians(-45),
            material: Cesium.Color.RED.withAlpha(0.7),
            outline: true,
            outlineColor: Cesium.Color.RED

        }
    });
    sentinelDataSource.entities.add(entity);
    return entity;
}

ui.exportToMain(function selectSentinel(event) {
    let e = event.detail.curSelection;
    if (e) {
        selectedSentinelEntry = e;
        let sentinel = e.sentinel;
        setDataViews(sentinel);
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

ui.exportToMain(function selectImage(event) {
    let e = event.detail.curSelection;
    if (e) {
        if (e.window) {
            ui.raiseWindowToTop(e.window);
        }
    }
});