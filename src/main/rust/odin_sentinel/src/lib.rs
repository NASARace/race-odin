/*
 * Copyright (c) 2023, United States Government, as represented by the
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
#![feature(trait_alias)]

use std::{collections::{VecDeque,HashMap},fmt::{self,Debug},cmp::Ordering,future::Future, ops::RangeBounds, time::Duration};
use actor::SentinelConnectorMsg;
use odin_actor::MsgReceiver;
use odin_macro::define_algebraic_type;
use serde::{Deserialize,Serialize};
use serde_json;
use ron;
use chrono::{DateTime,Utc};
use odin_common::angle::{LatAngle, LonAngle, Angle};
use odin_actor::tokio_kanal::ActorHandle;
use uom::si::f64::{Velocity,ThermodynamicTemperature,ElectricCurrent,ElectricPotential};
use reqwest::Client;
use paste::paste;

pub mod actor;
pub mod ws;

mod errors;
pub use errors::*;


/* #region snesor record  ***************************************************************************/

pub trait CapabilityProvider {
    fn capability()->SensorCapability;
}

macro_rules! assoc_capability {
    ($rec_type:ty : $cap:ident) => {
        impl CapabilityProvider for $rec_type {
            fn capability()->SensorCapability { SensorCapability::$cap }
        }
    };
}

pub type DeviceId = String;
pub trait RecordDataBounds = CapabilityProvider + Serialize + for<'de2> Deserialize<'de2> + Debug + Clone + 'static;

#[derive(Serialize,Deserialize,Debug,Clone)]
#[serde(bound = "T: Serialize, for<'de2> T: Deserialize<'de2>")]
#[serde(rename_all="camelCase")]
pub struct SensorRecord <T> where T: RecordDataBounds {   
    pub id: String, 

    pub time_recorded: DateTime<Utc>,
    pub sensor_no: u32,
    pub device_id: DeviceId,

    pub evidences: Vec<RecordId>, 
    pub claims: Vec<RecordId>,

    // here is the crux - we get this as different properties ("gps" etc - it depends on T)
    // TODO - check if we can rename this - it is redundant to the 'type' property in the response JSON anyways
    #[serde(
        alias = "accelerometer",
        alias = "anemometer",
        alias = "cloudcover",
        alias = "fire",
        alias = "image",
        alias = "gas",
        alias = "gps",
        alias = "gyroscope",
        alias = "magnetometer",
        alias = "orientation",
        alias = "person",
        alias = "power",
        alias = "smoke",
        alias = "thermometer",
        alias = "valve",
        alias = "voc"
    )]
    pub data: T,
}

impl<T> Ord for SensorRecord<T> where T: RecordDataBounds {
    fn cmp(&self, other: &Self) -> Ordering {
        self.time_recorded.cmp(&other.time_recorded)
    }
}

impl<T> PartialOrd for SensorRecord<T> where T: RecordDataBounds {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.time_recorded.cmp(&other.time_recorded))
    }
}

impl<T> PartialEq for SensorRecord<T> where T: RecordDataBounds {
    fn eq(&self, other: &Self) -> bool {
        self.id == other.id
    }
}
impl<T> Eq for SensorRecord<T> where T: RecordDataBounds {}

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
pub struct RecordId {
    pub id: String,
}

/// enum to give us a single non-generic type we can use to wrap any record so that we can publish it through a single msg/callback slot
/// note this also defined respective From<SensorRecord<..>> impls
define_algebraic_type!{ pub SentinelUpdate =
    SensorRecord<AccelerometerData> |
    SensorRecord<AnemometerData> |
    SensorRecord<CloudcoverData> |
    SensorRecord<FireData> |
    SensorRecord<GasData> |
    SensorRecord<GpsData> |
    SensorRecord<GyroscopeData> |
    SensorRecord<ImageData> |
    SensorRecord<MagnetometerData> |
    SensorRecord<OrientationData> |
    SensorRecord<PersonData> |
    SensorRecord<PowerData> |
    SensorRecord<SmokeData> |
    SensorRecord<ThermometerData> |
    SensorRecord<ValveData> |
    SensorRecord<VocData>
}

/* #endregion sensor record */

/* #region record payload data *********************************************************************************/

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct AccelerometerData {
    pub ax: f32,
    pub ay: f32,
    pub az: f32,
}
assoc_capability!( AccelerometerData: Accelerometer);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct AnemometerData {
    pub angle: Angle,
    pub speed: Velocity 
}
assoc_capability!(AnemometerData: Anemometer);

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct CloudcoverData {
    pub percent: f32,
}
assoc_capability!(CloudcoverData: Cloudcover);

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct FireData {
    pub fire_prob: f64
}
assoc_capability!(FireData: Fire);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)] // check this
#[serde(rename_all="camelCase")]
pub struct ImageData {
    pub filename: String,
    pub is_infrared: bool,
    pub orientation_record: Option<RecordId>, // nested orientation record?
}
assoc_capability!(ImageData: Image);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]  
pub struct GasData {
    pub gas: i32, // long
    pub humidity: f64,
    pub pressure: f64,
    pub altitude: f64
}
assoc_capability!(GasData: Gas);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]  
#[serde(rename_all="camelCase")]
pub struct GpsData {
    pub latitude: LatAngle, //f64,
    pub longitude: LonAngle,//f64
    pub altitude: Option<f64>, // update to uom
    pub quality: Option<f64>,
    pub number_of_satellites: Option<i32>,
    #[serde(alias = "HDOP")] pub hdop: Option<f32>
}
assoc_capability!(GpsData: Gps);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]  
#[serde(rename_all="camelCase")]
pub struct GyroscopeData {
    pub gx: f64,
    pub gy: f64,
    pub gz: f64
}
assoc_capability!(GyroscopeData: Gyroscope);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]  
#[serde(rename_all="camelCase")]
pub struct OrientationData {
    pub w: f64,
    pub qx: f64,
    pub qy: f64,
    pub qz: f64
}
assoc_capability!(OrientationData: Orientation);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]  
#[serde(rename_all="camelCase")]
pub struct MagnetometerData {
    pub mx: f64,
    pub my: f64,
    pub mz: f64
}
assoc_capability!(MagnetometerData: Magnetometer);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct PersonData {
    pub person_prob: f64
}
assoc_capability!(PersonData: Person);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct PowerData { // can use uom here for current, volatage, temp?
    pub battery_voltage: ElectricPotential,
    pub battery_current: ElectricCurrent,
    pub solar_voltage:ElectricPotential,
    pub solar_current: ElectricCurrent,
    pub load_voltage: ElectricPotential,
    pub load_current: ElectricCurrent,
    pub soc: f64,
    pub battery_temp: ThermodynamicTemperature, // temp
    pub controller_temp: ThermodynamicTemperature, //temp
    pub battery_status: String,
    pub charging_volatage_status: String,
    pub charging_status: String,
    pub load_volatage_status: String,
    pub load_status: String
}
assoc_capability!(PowerData: Power);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct SmokeData {
    pub smoke_prob: f64
}
assoc_capability!(SmokeData: Smoke);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct ThermometerData {
    pub temperature: ThermodynamicTemperature
}
assoc_capability!(ThermometerData: Thermometer);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct ValveData {
    pub valve_open: bool,
    pub external_light_on: bool,
    pub internal_light_on: bool,
}
assoc_capability!(ValveData: Valve);


#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)] 
pub struct VocData {
   #[serde(alias = "TVOC")] pub tvoc: i32,
   #[serde(alias = "eCO2")] pub e_co2: i32,
}
assoc_capability!(VocData: Voc);

// set of supported capabilities. We don't care much about upper/lowercase since they are
// only used in constructing http queries (which are case-insensitive)
#[derive(Serialize,Deserialize,Debug,PartialEq,Copy,Clone)] 
#[serde(rename_all="lowercase")]
pub enum SensorCapability {
    Accelerometer,
    Anemometer,
    Cloudcover,
    Fire,
    Gas,
    Gps,
    Gyroscope,
    Image,
    Magnetometer,
    Orientation,
    Person,
    Power,
    Smoke,
    Thermometer,
    Valve,
    Voc
}

/* #endregion record payload data */

/* #region other query responses **********************************************************************/

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
pub struct Device {
    pub id: String,
    pub info: Option<String>
}

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct DeviceList {
   pub data: Vec<Device>,
   // we assume we always query all devices i.e. don't need pagination
}
impl DeviceList {
    pub fn get_device_ids (&self)->Vec<String> {
        self.data.iter().map(|d| d.id.clone()).collect()
    }
}

#[derive(Serialize,Deserialize,Debug, PartialEq, Clone)]
#[serde(rename_all="camelCase")]
pub struct SensorData {
    pub no: u32,
    pub device_id: String,
    pub part_no: Option<String>,
    pub capabilities: Vec<SensorCapability>
}

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(rename_all="camelCase")]
pub struct SensorList {
   pub data: Vec<SensorData>,
// we assume we always query all sensors i.e. don't need pagination
}

#[derive(Serialize,Deserialize,Debug,PartialEq,Clone)]
#[serde(bound = "T: RecordDataBounds")]
pub struct RecordList<T> where T: RecordDataBounds {
    pub data: Vec<SensorRecord<T>>,
}

/* #endregion other query responses */

/* #region internal data store ************************************************************************/

/// the struct that stores sentinel values and provides access to them through their device_ids
#[derive(Debug)]
pub struct SentinelStore {
    sentinels: HashMap<DeviceId,Sentinel>
}
impl SentinelStore {
    pub fn new ()->Self {
        SentinelStore { sentinels: HashMap::new() }
    }
    pub fn insert (&mut self, k: String, v: Sentinel)->Option<Sentinel> {
        self.sentinels.insert( k, v)
    }

    pub fn get (&self, k: &String)->Option<&Sentinel> {
        self.sentinels.get(k)
    }

    pub fn get_mut (&mut self, k: &String)->Option<&mut Sentinel> {
        self.sentinels.get_mut(k)
    }

    pub fn sentinel_of (&mut self, k: &String)->Result<&mut Sentinel> {
        self.sentinels.get_mut( k).ok_or( OdinSentinelError::NoSuchDeviceError( k.to_string()))
    }

    pub fn values (&self)->Vec<&Sentinel> {
        self.sentinels.values().collect()
    }

    pub fn to_json (&self, pretty: bool)->Result<String> {
        let list = SentinelList { sentinels: self.values() };
        if pretty {
            Ok(serde_json::to_string_pretty( &list)?)
        } else {
            Ok(serde_json::to_string( &list)?)
        }
    }

    pub fn to_ron (&self, pretty: bool)->Result<String> {
        let list = SentinelList { sentinels: self.values() };
        if pretty {
            Ok(ron::ser::to_string_pretty( &list, ron::ser::PrettyConfig::default())?)
        } else {
            Ok(ron::to_string(&list)?)
        }
    }
}

/// helper type so that we can serialize the Sentinel values as a list
#[derive(Serialize)]
struct SentinelList<'a>  {
    sentinels: Vec<&'a Sentinel>
}

/// the current sentinel state. This needs to be serializable to JSON so that we
/// can send it to connected clients (field names have to map into what our javascript module expects)
#[derive(Serialize,Deserialize,Debug)]
#[serde(rename_all="camelCase")]
pub struct Sentinel {
    pub device_id: DeviceId,
    pub device_name: String,
    pub date: Option<DateTime<Utc>>, // last update

    // the last N records for each capability/sensor
    pub accel:         VecDeque< SensorRecord<AccelerometerData> >,
    pub anemo:         VecDeque< SensorRecord<AnemometerData> >,
    pub cloudcover:    VecDeque< SensorRecord<CloudcoverData> >,
    pub fire:          VecDeque< SensorRecord<FireData> >,
    pub gas:           VecDeque< SensorRecord<GasData> >,
    pub gps:           VecDeque< SensorRecord<GpsData> >,
    pub gyro:          VecDeque< SensorRecord<GyroscopeData> >,
    pub image:         VecDeque< SensorRecord<ImageData> >,
    pub mag:           VecDeque< SensorRecord<MagnetometerData> >,
    pub orientation:   VecDeque< SensorRecord<OrientationData> >,
    pub person:        VecDeque< SensorRecord<PersonData> >,
    pub power:         VecDeque< SensorRecord<PowerData> >,
    pub smoke:         VecDeque< SensorRecord<SmokeData> >,
    pub thermo:        VecDeque< SensorRecord<ThermometerData> >,
    pub valve:         VecDeque< SensorRecord<ValveData> >,
    pub voc:           VecDeque< SensorRecord<VocData> >    
}

impl Sentinel {
    pub fn new (device_id: DeviceId, device_name: String)->Self {
        Sentinel { 
            device_id,
            device_name,
            date: None,

            accel:         VecDeque::new(),
            anemo:         VecDeque::new(),
            cloudcover:    VecDeque::new(),
            fire:          VecDeque::new(),
            gas:           VecDeque::new(),
            gps:           VecDeque::new(),
            gyro:          VecDeque::new(),
            image:         VecDeque::new(),
            mag:           VecDeque::new(),
            orientation:   VecDeque::new(),
            person:        VecDeque::new(),
            power:         VecDeque::new(),
            smoke:         VecDeque::new(),
            thermo:        VecDeque::new(),
            valve:         VecDeque::new(),
            voc:           VecDeque::new(),
        }
    }

    pub async fn get_and_store_records( &mut self, client: &Client, base_uri: &str, access_token: &str, 
                                                 sensor_no: u32, capability: SensorCapability, n_last: usize) -> Result<()> {
        let device_id = &self.device_id.as_str();
        use SensorCapability::*;
        match capability {
            Accelerometer => sort_in_records( &mut self.accel, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Anemometer    => sort_in_records( &mut self.anemo, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Cloudcover    => sort_in_records( &mut self.cloudcover, get_records(client, base_uri, access_token, device_id, sensor_no,  n_last).await?),
            Fire          => sort_in_records( &mut self.fire, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Gas           => sort_in_records( &mut self.gas, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Gps           => sort_in_records( &mut self.gps, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Gyroscope     => sort_in_records( &mut self.gyro, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Image         => sort_in_records( &mut self.image, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Magnetometer  => sort_in_records( &mut self.mag, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Orientation   => sort_in_records( &mut self.orientation, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Person        => sort_in_records( &mut self.person, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Power         => sort_in_records( &mut self.power, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Smoke         => sort_in_records( &mut self.smoke, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Thermometer   => sort_in_records( &mut self.thermo, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Valve         => sort_in_records( &mut self.valve, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
            Voc           => sort_in_records( &mut self.voc, get_records( client, base_uri, access_token, device_id, sensor_no, n_last).await?),
        }
        Ok(())
    }
}

pub fn sort_in_records<T> (list: &mut VecDeque<SensorRecord<T>>, recs: Vec<SensorRecord<T>>) where T: RecordDataBounds {
    for rec in recs {
        sort_in_record( list, rec)
    }
}

pub fn sort_in_record<T> (list: &mut VecDeque<SensorRecord<T>>, rec: SensorRecord<T>) where T: RecordDataBounds {
    let mut i=0;
    for r in list.iter() {
        if (rec.time_recorded > r.time_recorded) {
            list.insert( i, rec);
            return
        }
        i += 1;
    }
    list.push_back( rec);
}

/* #endregion internal data store */

/* #region config  ************************************************************************************/

#[derive(Deserialize)]
pub struct SentinelConfig {
    pub base_uri: String,
    pub ws_uri: String,
    pub(crate) access_token: String, // TODO - should probably be a [u8;N]
    pub max_history: usize,

    pub ping_interval: Option<Duration>, // interval duration for sending Ping messages on the websocket

    //... and a lot more to come

    // TODO - add optional device_id -> device_name map 
}

/* #endregion config */

/* #region initial query ******************************************************************************/

pub async fn init_sentinel_store (client: &Client, base_uri: &str, access_token: &str, n_last: usize)->Result<SentinelStore> {
    let mut sentinel_store = SentinelStore::new();

    let device_list = get_device_list( client, base_uri, access_token).await?;
    for device in &device_list.data {
        let device_name = if let Some(info) = &device.info { info.clone() } else { "unknown".to_string() };
        let mut sentinel = Sentinel::new( device.id.clone(), device_name);

        let sensor_list = get_sensor_list( client, base_uri, access_token, device.id.as_str()).await?;
        for sensor_data in &sensor_list.data {
            for capability in &sensor_data.capabilities {
                sentinel.get_and_store_records(client, base_uri, access_token, sensor_data.no, *capability, n_last).await?;
            }
        }

        sentinel_store.insert( sentinel.device_id.clone(), sentinel);
    }

    Ok(sentinel_store)
}

pub async fn init_sentinel_store_from_config (client: &Client, config: &SentinelConfig)->Result<SentinelStore> {
    init_sentinel_store(client, config.base_uri.as_str(), config.access_token.as_str(), config.max_history).await
}

/* #endregion initial query */

/* #region basic http getters *************************************************************************************************/

pub async fn get_device_list (client: &Client, base_uri: &str, access_token: &str)->Result<DeviceList> {
    let uri = format!("{base_uri}/devices");
    let response = client.get(uri).bearer_auth(access_token).send().await?;
    let device_list: DeviceList = response.json().await?;
    Ok(device_list)
}

pub async fn get_device_list_from_config (client: &Client, config: &SentinelConfig)->Result<DeviceList> {
    get_device_list( client, &config.base_uri, &config.access_token).await
}

pub async fn get_sensor_list (client: &Client, base_uri: &str, access_token: &str, device_id: &str) -> Result<SensorList> {
    let uri =  format!("{base_uri}/devices/{device_id}/sensors");
    let response = client.get(uri).bearer_auth(access_token).send().await?;
    let sensor_list: SensorList = response.json().await?;
    Ok(sensor_list)
}

pub async fn get_records <T> (client: &Client, base_uri: &str, access_token: &str, 
                              device_id: &str, sensor_no:u32, n_last: usize) -> Result<Vec<SensorRecord<T>>> 
    where T: RecordDataBounds
{ 
    let capability = T::capability();
    let uri = format!("{base_uri}/devices/{device_id}/sensors/{sensor_no}/{capability:?}?sort=timeRecorded,DESC&limit={n_last}");
    let response = client.get(uri).bearer_auth(access_token).send().await?;
    let record_list: RecordList<T> = response.json().await?;
    Ok(record_list.data)
} 

pub async fn get_latest_record <T> (client: &Client, base_uri: &str, access_token: &str, 
                                    device_id: &str, sensor_no:u32) -> Result<SensorRecord<T>> 
    where T: RecordDataBounds
{
    let mut recs = get_records::<T>( client, base_uri, access_token, device_id, sensor_no, 1).await?;
    if recs.is_empty() {
        Err(no_data(format!("for device: {}, sensor: {}, capability: {:?}", device_id, sensor_no, T::capability())))
    } else {
        Ok(recs.remove(0))
    }
}

/* #endregion basic http getters */