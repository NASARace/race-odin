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
var sentinelImagesView = undefined;

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
        if (this.sentinel.fire) {
            if (this.sentinel.fire.fireProb > 0.5) {
                return ui.createImage("sentinel-asset/fire");
            }
        }
        return "";
    }

    fireStatus() {
        return (this.sentinel.fire) ? util.f_1.format(this.sentinel.fire.fireProb) : "";
    }

    imageStatus() {
        return (this.sentinel.images) ? "⧉" : "";
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
}


ui.registerLoadFunction(function initialize() {
    uiCesium.addDataSource(sentinelDataSource);
    sentinelView = initSentinelView();
    sentinelImagesView = initSentinelImagesView();

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
        ui.setListItemDisplayColumns(view, ["fit"], [
            { name: "show", width: "2rem", attrs: [], map: e => ui.createCheckBox(e.showDetails, toggleShowDetails, null) },
            { name: "id", width: "4rem", attrs: ["alignLeft"], map: e => e.id },
            { name: "alert", width: "1.5rem", attrs: [], map: e => e.alertStatus() },
            { name: "prob", width: "3rem", attrs: ["fixed"], map: e => e.fireStatus() },
            { name: "images", width: "2rem", attrs: [], map: e => e.imageStatus() },
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

function initSentinelImagesView() {
    let view = ui.getList("sentinel.image.list");
    if (view) {
        ui.setListItemDisplayColumns(view, ["fit", "header"], [
            { name: "", width: "2rem", attrs: [], map: e => ui.createCheckBox(e.window, toggleShowImage, null) },
            { name: "sen", width: "2rem", attrs: [], map: e => e.sensorNo },
            { name: "type", width: "2rem", attrs: [], map: e => e.isInfrared ? "ir" : "vis" },
            { name: "date", width: "12rem", attrs: ["fixed", "alignRight"], map: e => util.toLocalDateTimeString(e.timeRecorded) }
        ]);
    }
    return view;
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
                    let w = ui.createWindow(e.filename, false, () => {
                        e.window = undefined;
                        ui.updateListItem(sentinelImagesView, e);
                    });
                    let img = ui.createImage(e.filename, "waiting for image..", config.sentinel.imageWidth);
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
            Object.keys(r).forEach(k => {
                switch (k) {
                    case 'fire':
                        sentinel.fire = r.fire;
                        sentinel.timeRecorded = r.timeRecorded;
                        checkFireAsset(e);
                        if (e == selectedSentinelEntry) setFireData(sentinel);
                        break;
                    case 'smoke':
                        sentinel.smoke = r.smoke;
                        sentinel.timeRecorded = r.timeRecorded;
                        //checkFireAsset(e);
                        if (e == selectedSentinelEntry) setSmokeData(sentinel);
                        break;
                    case 'image':
                        //console.log("@@@ " + JSON.stringify(r));
                        let img = r.image;
                        img.sensorNo = r.sensorNo;
                        img.timeRecorded = r.timeRecorded;

                        sentinel.images = (sentinel.images) ? util.prependElement(img, sentinel.images) : [img];
                        sentinel.timeRecorded = r.timeRecorded;
                        if (e == selectedSentinelEntry) ui.setListItems(sentinelImagesView, sentinel.images);
                        break;
                    case 'anemometer':
                        sentinel.anemometer = r.anemometer;
                        sentinel.timeRecorded = r.timeRecorded;
                        if (e == selectedSentinelEntry) setAnemoData(sentinel);
                        break;
                    case 'gas':
                        sentinel.gas = r.gas;
                        sentinel.timeRecorded = r.timeRecorded;
                        if (e == selectedSentinelEntry) setGasData(sentinel);
                        break;
                    case 'thermometer':
                        sentinel.thermometer = r.thermometer;
                        sentinel.timeRecorded = r.timeRecorded;
                        if (e == selectedSentinelEntry) setThermoData(sentinel);
                        break;
                    case 'voc':
                        sentinel.voc = r.voc;
                        sentinel.timeRecorded = r.timeRecorded;
                        if (e == selectedSentinelEntry) setVocData(sentinel);
                        break;
                    case 'gps':
                        if (!sentinel.gps || (sentinel.gps.lat != r.gps.lat) || (sentinel.gps.lon != r.gps.lon)) {
                            sentinel.gps = r.gps;
                            sentinel.timeRecorded = r.timeRecorded;
                            if (sentinel.assets) sentinel.assets.updatePosition(r.gps.lat, r.gps.lon);
                        }

                }
            });
            ui.updateListItem(sentinelView, e);
        }
    });
}

function updateSentinelEntry(e, sentinel) {
    let old = e.sentinel;

    //console.log(JSON.stringify(sentinel));
    e.sentinel = sentinel;
    ui.updateListItem(sentinelView, e);

    if (!old.gps && sentinel.gps) {
        e.assets = createAssets(e);
    }
    checkFireAsset(e);
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
        position: Cesium.Cartesian3.fromDegrees(sentinel.gps.longitude, sentinel.gps.latitude),
        billboard: {
            image: 'sentinel-asset/sentinel',
            distanceDisplayCondition: config.sentinel.billboardDC,
            color: config.sentinel.color,
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        },
        label: {
            text: sentinel.deviceId.toString(),
            scale: 0.8,
            horizontalOrigin: Cesium.HorizontalOrigin.LEFT,
            verticalOrigin: Cesium.VerticalOrigin.TOP,
            font: config.track.labelFont,
            fillColor: config.sentinel.color,
            showBackground: true,
            backgroundColor: config.track.abelBackground,
            pixelOffset: config.sentinel.labelOffset,
            distanceDisplayCondition: config.sentinel.billboardDC,
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
        position: Cesium.Cartesian3.fromDegrees(sentinel.gps.longitude, sentinel.gps.latitude),
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
        position: Cesium.Cartesian3.fromDegrees(sentinel.gps.longitude - 0.002, sentinel.gps.latitude - 0.002, 0),
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
        setDataFields(sentinel);
        setImagesView(sentinel);
    }
});

function tRec(sentinelReading) {
    return "";
    //return util.toLocalTimeString(sentinelReading.timeRecorded);
}

function setFireData(sentinel) {
    ui.setField("sentinel.data.fire", sentinel.fire ?
        `${tRec(sentinel.fire)} prob: ${(sentinel.fire.fireProb * 100).toFixed(0)}%` : "");
}

function setSmokeData(sentinel) {
    ui.setField("sentinel.data.smoke", sentinel.smoke ?
        `${tRec(sentinel.smoke)} prob: ${(sentinel.smoke.smokeProb * 100).toFixed(0)}%` : "");
}

function setAnemoData(sentinel) {
    ui.setField("sentinel.data.anemo", sentinel.anemometer ?
        `${tRec(sentinel.anemometer)} spd: ${sentinel.anemometer.speed.toFixed(1)}m/s, dir: ${sentinel.anemometer.angle.toFixed(0)}°` : "");
}

function setGasData(sentinel) {
    ui.setField("sentinel.data.gas", sentinel.gas ?
        `${tRec(sentinel.gas)} hum: ${sentinel.gas.hummidity.toFixed(0)}%, pres: ${sentinel.gas.pressure.toFixed(1)}hPa` : "");
}

function setThermoData(sentinel) {
    ui.setField("sentinel.data.thermo", sentinel.thermometer ?
        `${tRec(sentinel.thermometer)} temp: ${sentinel.thermometer.temperature.toFixed(1)}°C` : "");
}

function setVocData(sentinel) {
    ui.setField("sentinel.data.voc", sentinel.voc ?
        `${tRec(sentinel.voc)} TVOC: ${sentinel.voc.TVOC}ppb, eCO2: ${sentinel.voc.eCO2}ppm` : "");
}

function setDataFields(sentinel) {
    setFireData(sentinel);
    setSmokeData(sentinel);
    setAnemoData(sentinel);
    setGasData(sentinel);
    setThermoData(sentinel);
    setVocData(sentinel);
}

function setImagesView(sentinel) {
    if (sentinel.images) {
        ui.setListItems(sentinelImagesView, sentinel.images)
    } else {
        ui.clearList(sentinelImagesView);
    }
}

ui.exportToMain(function selectImage(event) {
    let e = event.detail.curSelection;
    if (e) {
        if (e.window) {
            ui.raiseWindowToTop(e.window);
        }
    }
});