/*
 * Copyright (c) 2024, United States Government, as represented by the
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

#![allow(unused)]

use std::{collections::HashMap,sync::{Arc,atomic::AtomicU64}};
use futures::stream::{StreamExt,SplitSink};
use tokio_tungstenite::tungstenite::protocol::Message;
use odin_actor::prelude::*;
use odin_actor::tokio_kanal::{ActorSystem,ActorSystemHandle,Actor,ActorHandle,AbortHandle,JoinHandle,spawn, MpscSender,MpscReceiver,create_mpsc_sender_receiver};
use reqwest::{Client};
use crate::*;
use crate::ws::{WsStream,WsCmd, init_websocket, run_websocket, send_ws_text_msg, read_next_ws_msg};

const PING_TIMER: i64 = 1;

macro_rules! define_update {
    ($f:ident <- $r:ty) => { paste!{
        async fn [<update_ $f>] (&mut self, rec: $r)->Result<()> {
            let sentinel = self.sentinels.sentinel_of(&rec.device_id)?;

            // only convert if there are clients for it
            let json = if !self.json_update_callbacks.is_empty() { Some(Arc::new(serde_json::to_string(&rec)?)) } else { None };
            let update_rec = if !self.update_callbacks.is_empty() { Some( Arc::<SentinelUpdate>::new(rec.clone().into())) } else { None };

            sort_in_record(&mut sentinel.$f, rec); // this consumes the rec

            if let Some(json) = json { self.json_update_callbacks.trigger(json).await; } // we don't propagate errors here
            if let Some(update_rec) = update_rec { self.update_callbacks.trigger(update_rec).await; }

            Ok(())
        }
    }}
}

/// the actor state
pub struct SentinelConnector {
    config: Arc<SentinelConfig>,
    sentinels: SentinelStore,

    last_recv_epoch: Arc<AtomicU64>, // in millis

    ping_timer: Option<AbortHandle>,
    websocket_task: Option<JoinHandle<Result<()>>>,
    ws_write: Option<SplitSink<WsStream,Message>>,  // the sender end of the channel to send commands to the acquisition task

    //-- callbacks 
    init_callbacks: CallbackList<()>,  // triggered when sentinels are initialized

    //-- callbacks triggered upon receiving a new record
    update_callbacks: CallbackList<Arc<SentinelUpdate>>,  // triggered by new SensorRecords
    json_update_callbacks: CallbackList<Arc<String>>, // triggered by new SensorRecords
}

impl SentinelConnector {
    pub fn new (config: SentinelConfig)->Self {
        SentinelConnector {
            config: Arc::new(config),
            sentinels: SentinelStore::new(),

            last_recv_epoch: Arc::new(AtomicU64::new(0)),

            ping_timer: None,
            websocket_task: None,
            ws_write: None,

            init_callbacks: CallbackList::new(),
            update_callbacks: CallbackList::new(),
            json_update_callbacks: CallbackList::new(),
        }
    }

    async fn run_init_task (hself: ActorHandle<SentinelConnectorMsg>, config: Arc<SentinelConfig>)->Result<()> {
        let http_client = Client::new();

        let sentinel_store = init_sentinel_store_from_config( &http_client, &config).await?; // this might take a while so we shouldn't await it in receive()
        Ok(hself.send_msg( sentinel_store).await?)
    }

    async fn send_ws_cmd (&mut self, cmd: WsCmd)->Result<()> {
        if let Some(mut tx) = self.ws_write.as_mut() { 
            let json = serde_json::to_string(&cmd)?;
            send_ws_text_msg( &mut tx, json).await

        } else {
            Err( op_failed("no websocket"))
        }
    }

    async fn set_sentinels (&mut self, sentinels: SentinelStore) {
        self.sentinels = sentinels;
        self.init_callbacks.trigger(()).await; // let other actors know we have data
    }

    async fn open_websocket (&mut self, hself: ActorHandle<SentinelConnectorMsg>) {
        let device_ids = self.sentinels.get_device_ids();

        if !device_ids.is_empty() {
            let hself = hself.clone();
            let config = self.config.clone();

            if let Ok(ws_stream) = init_websocket(self.config.clone(), device_ids).await {
                let (ws_write, ws_read) = ws_stream.split();
                self.ws_write = Some(ws_write);

                self.websocket_task = Some( spawn( run_websocket( hself.clone(), config, ws_read)) );
                if let Some(interval) = self.config.ping_interval {
                    self.ping_timer = Some( hself.start_repeat_timer( PING_TIMER, interval) )
                }
            }
        }
    }

    fn cleanup_websocket (&mut self) {
        self.ws_write = None;

        if let Some(abort_handle) = &self.ping_timer {
            abort_handle.abort()
        }

        if let Some(join_handle) = &self.websocket_task {
            if !join_handle.is_finished() {
                join_handle.abort();
            }
            self.websocket_task = None;
        }

    }

    define_update! { accel       <- SensorRecord<AccelerometerData> }
    define_update! { anemo       <- SensorRecord<AnemometerData> }
    define_update! { cloudcover  <- SensorRecord<CloudcoverData> }
    define_update! { fire        <- SensorRecord<FireData> }
    define_update! { gas         <- SensorRecord<GasData> }
    define_update! { gps         <- SensorRecord<GpsData> }
    define_update! { gyro        <- SensorRecord<GyroscopeData> }
    define_update! { image       <- SensorRecord<ImageData> }
    define_update! { mag         <- SensorRecord<MagnetometerData> }
    define_update! { orientation <- SensorRecord<OrientationData> }
    define_update! { person      <- SensorRecord<PersonData> }
    define_update! { power       <- SensorRecord<PowerData> }
    define_update! { smoke       <- SensorRecord<SmokeData> }
    define_update! { thermo      <- SensorRecord<ThermometerData> }
    define_update! { valve       <- SensorRecord<ValveData> }
    define_update! { voc         <- SensorRecord<VocData> }

}


#[derive(Debug)] pub struct AddInitCallback { pub id: String, pub action: Callback<()> }

#[derive(Debug)] pub struct AddUpdateCallback { pub id: String, pub action: Callback<Arc<SentinelUpdate>> }

#[derive(Debug)] pub struct AddJsonUpdateCallback { pub id: String, pub action: Callback<Arc<String>> }

/// message to request a single callback execution with the current Sentinel snapshot in JSON format
/// (since this is a single execution there is no point transmitting this as an Arc<String>) 
#[derive(Debug)] pub struct TriggerJsonSnapshot(pub Callback<String>);

define_actor_msg_type! { pub SentinelConnectorMsg = 
    // messages we get from other actors
    AddInitCallback |
    AddUpdateCallback |
    AddJsonUpdateCallback |
    TriggerJsonSnapshot |

    // messages we get from ourself (spawned tasks)
    SentinelStore |
    SensorRecord<AccelerometerData> |
    SensorRecord<AnemometerData> |
    SensorRecord<CloudcoverData> |
    SensorRecord<FireData> |
    SensorRecord<ImageData> |
    SensorRecord<GasData> |
    SensorRecord<GpsData> |
    SensorRecord<GyroscopeData> |
    SensorRecord<OrientationData> |
    SensorRecord<MagnetometerData> |
    SensorRecord<PersonData> |
    SensorRecord<PowerData> |
    SensorRecord<SmokeData> |
    SensorRecord<ThermometerData> |
    SensorRecord<ValveData> |
    SensorRecord<VocData> |
    OdinSentinelError
}

impl_actor! { match msg for Actor<SentinelConnector,SentinelConnectorMsg> as 
    _Start_ => cont! { 
        let hself = self.hself.clone();
        let config = self.config.clone();

        spawn( SentinelConnector::run_init_task( hself, config)); // this can take some time so we have to spawn
    }
    AddInitCallback => cont! {
        self.init_callbacks.add( msg.id, msg.action )
    }
    AddUpdateCallback => cont! {
        self.update_callbacks.add( msg.id, msg.action )
    }
    AddJsonUpdateCallback => cont! {
        self.json_update_callbacks.add( msg.id, msg.action )
    }
    TriggerJsonSnapshot => cont! {
        if let Ok(s) = self.sentinels.to_json(false) {
            msg.0.trigger(s).await;
        }
    }
    SentinelStore => cont! { // we get this once run_init_task() is completed
        let hself = self.hself.clone();
        self.set_sentinels(msg).await;
        self.open_websocket( hself).await
    }
    _Timer_ => cont! { 
        match msg.id {
            PING_TIMER => { 
                self.send_ws_cmd( WsCmd::new_ping("ping")).await; 
            }
            _ => {}
        }
    }
    OdinSentinelError => cont! {
        match msg {
            OdinSentinelError::WsClosedError => {
                eprintln!("@@ websocket closed by server");
                self.cleanup_websocket();
                // TODO - check if we should restart the init here
            }
            OdinSentinelError::JsonError(e) => {
                eprintln!("@@ {:?}", e);
            }
            OdinSentinelError::WsError(e) => {
                eprintln!("@@ {:?}", e);
            }
            _ => {} // ignore the rest
        }
    }
    _Terminate_ => stop! {
        self.cleanup_websocket()
    }
    SensorRecord<AccelerometerData> => cont! { self.update_accel(msg).await }
    SensorRecord<AnemometerData>    => cont! { self.update_anemo(msg).await }
    SensorRecord<CloudcoverData>    => cont! { self.update_cloudcover(msg).await }
    SensorRecord<FireData>          => cont! { self.update_fire(msg).await }
    SensorRecord<ImageData>         => cont! { self.update_image(msg).await }
    SensorRecord<GasData>           => cont! { self.update_gas(msg).await }
    SensorRecord<GpsData>           => cont! { self.update_gps(msg).await }
    SensorRecord<GyroscopeData>     => cont! { self.update_gyro(msg).await }
    SensorRecord<OrientationData>   => cont! { self.update_orientation(msg).await }
    SensorRecord<MagnetometerData>  => cont! { self.update_mag(msg).await }
    SensorRecord<PersonData>        => cont! { self.update_person(msg).await }
    SensorRecord<PowerData>         => cont! { self.update_power(msg).await }
    SensorRecord<SmokeData>         => cont! { self.update_smoke(msg).await }
    SensorRecord<ThermometerData>   => cont! { self.update_thermo(msg).await }
    SensorRecord<ValveData>         => cont! { self.update_valve(msg).await }
    SensorRecord<VocData>           => cont! { self.update_voc(msg).await }
}