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

use std::{collections::HashMap,sync::Arc};
use odin_actor::prelude::*;
use odin_actor::tokio_kanal::{ActorSystem,ActorSystemHandle,Actor,ActorHandle,AbortHandle,JoinHandle,spawn};
use reqwest::{Client};
use crate::*;

macro_rules! define_update {
    ($f:ident <- $r:ty) => { paste!{
        async fn [<update_ $f>] (&mut self, rec: $r)->Result<()> {
            let sentinel = self.sentinels.sentinel_of(&rec.device_id)?;
            let json = Arc::new(serde_json::to_string(&rec)?);
            sort_in_record(&mut sentinel.$f, rec);
            Ok(self.json_update_callbacks.trigger(json).await?)
        }
    }}
}

/// the actor state
pub struct SentinelConnector {
    config: Arc<SentinelConfig>,
    sentinels: SentinelStore,

    timer: Option<AbortHandle>,
    acquisition_task: Option<JoinHandle<()>>,

    /// callbacks to be triggered upon receiving a new record
    json_update_callbacks: CallbackList<Arc<String>>,
}

impl SentinelConnector {
    pub fn new (config: SentinelConfig)->Self {
        SentinelConnector {
            config: Arc::new(config),
            sentinels: SentinelStore::new(),
            timer: None,
            acquisition_task: None,
            json_update_callbacks: CallbackList::new(),
        }
    }

    async fn run_acquisition_task (hself: ActorHandle<SentinelConnectorMsg>, config: Arc<SentinelConfig>)->Result<()> {
        let http_client = Client::new();

        // obtain the initial record set for our sentinel devices
        let sentinel_store = init_sentinel_store_from_config( &http_client, config.as_ref()).await?;
        hself.send_msg( sentinel_store).await?;

        // now register for record update and open a websocket to get update notifications

        Ok(())
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


#[derive(Debug)] pub struct AddJsonUpdateCallback { id: String, action: Callback<Arc<String>> }

/// message to request a single callback execution with the current Sentinel snapshot in JSON format
/// (since this is a single execution there is no point transmitting this as an Arc<String>) 
#[derive(Debug)] pub struct TriggerJsonSnapshot(Callback<String>);


define_actor_msg_type! { pub SentinelConnectorMsg = 
    // client interface
    AddJsonUpdateCallback |
    TriggerJsonSnapshot |

    // messages we get from our acquisition task
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
    SensorRecord<VocData>
}

impl_actor! { match msg for Actor<SentinelConnector,SentinelConnectorMsg> as 
    _Start_ => cont! { 
        let hself = self.hself.clone();
        let config = self.config.clone();

        self.acquisition_task = Some(
            spawn( async move {
                SentinelConnector::run_acquisition_task( hself, config).await;
            })
        );
    }
    _Timer_ => cont! { 

    }
    _Terminate_ => stop! {
        if let Some(task) = &self.acquisition_task {
            task.abort();
            self.acquisition_task = None;
        }
    }
    AddJsonUpdateCallback => cont! {
        self.json_update_callbacks.add( msg.id, msg.action )
    }
    TriggerJsonSnapshot => cont! {
        if let Ok(s) = self.sentinels.to_json(false) {
            msg.0.trigger(s).await;
        }
    }
    SentinelStore => cont! {
        self.sentinels = msg;
        println!("Sentinels initialized");
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