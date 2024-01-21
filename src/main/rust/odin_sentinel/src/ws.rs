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

use std::{sync::Arc};
use futures::{SinkExt,StreamExt};
use chrono::Utc;
use tokio_tungstenite::{
    connect_async, WebSocketStream, MaybeTlsStream, 
    tungstenite::{self,
        protocol::Message, 
        http::{Request,header::{AUTHORIZATION,HeaderValue}}, 
        handshake::client::{Response,generate_key}, 
        client::IntoClientRequest
    }
};
use tokio::{net::TcpStream,io::{AsyncRead,AsyncReadExt,AsyncWrite,AsyncWriteExt}};
use reqwest::Client;
use serde::{Deserialize,Serialize};
use serde_json;
use odin_actor::prelude::*;
use odin_actor::tokio_kanal::ActorHandle;
use crate::*;
use crate::actor::*;

pub type WsStream = WebSocketStream<MaybeTlsStream<TcpStream>>;

pub async fn run_websocket (hself: ActorHandle<SentinelConnectorMsg>, config: Arc<SentinelConfig>, http_client: &Client, device_ids: Vec<String>)->Result<()> {
    let mut msg_id = 0;

    let (mut ws,_) = connect( config.as_ref()).await?;
    expect_connected_response(&mut ws).await?;

    request_join( &mut ws, device_ids.clone(), get_next_msg_id(&mut msg_id)).await?;
    expect_join_response( &mut ws).await?;

    loop {
        match read_next_ws_msg( &mut ws).await? {
            WsMsg::Record { device_id, sensor_no, rec_type } => {
                get_and_send_record( &hself, http_client, config.base_uri.as_str(), config.access_token.as_str(), device_id.as_str(), sensor_no, rec_type).await?;
            },
            _ => {} // TODO - need to handle Pong and Error here
        }
    }

    Ok(())
}

pub async fn connect (config: &SentinelConfig)->Result<(WsStream, Response)> {
    
    let mut request = config.ws_uri.as_str().into_client_request()?;
    let mut hdrs = request.headers_mut();

    let auth_val = format!("Bearer {}", config.access_token);
    hdrs.append( AUTHORIZATION, HeaderValue::from_str(auth_val.as_str())?);

    /* explicit request construction with Request
    let url = url::Url::parse(&config.ws_uri)?;
    let host = url.host_str().ok_or(op_failed(url::ParseError::EmptyHost))?;

    let request = Request::builder()
        .uri( config.ws_uri.as_str())
        .header("Host", host)
        .header("connection", "Upgrade")
        .header("upgrade", "websocket")
        .header("sec-websocket-version", "13")
        .header("sec-websocket-key", tokio_tungstenite::tungstenite::handshake::client::generate_key())
        .header( "Authorization", format!("Bearer {}", config.access_token))
        .body(())?;
    */

    Ok(connect_async(request).await?)
}

pub async fn expect_connected_response (ws: &mut WsStream)->Result<()> {
    let resp = read_next_ws_msg(ws).await?;
    if let WsMsg::Connected{message} = resp {
        Ok(())
    } else {
        Err( OdinSentinelError::WsProtocolError(format!("expected 'connected' message, got {:?}",resp)))
    }
}

pub async fn request_join (ws: &mut WsStream, device_ids: Vec<String>, message_id: String)->Result<()> {
    let msg = WsMsg::Join{device_ids, message_id};
    let json = serde_json::to_string(&msg)?;
    Ok(ws.send( Message::Text(json)).await?)
}

pub async fn expect_join_response (ws: &mut WsStream)->Result<()> {
    let resp = read_next_ws_msg(ws).await?;
    if let WsMsg::Join{device_ids,message_id} = resp  {
        Ok(())
    } else {
        Err( OdinSentinelError::WsProtocolError(format!("expected 'join' message, got {:?}",resp)))
    }
}

pub async fn read_next_ws_msg (ws: &mut WsStream)->Result<WsMsg> {
    let json = ws.next().await.ok_or(tungstenite::error::Error::AlreadyClosed)??;
    let msg: WsMsg = serde_json::from_str( json.to_text()?)?;
    Ok(msg)
}

fn get_next_msg_id (msg_id: &mut usize)->String {
    *msg_id += 1;
    msg_id.to_string()        
}

pub async fn get_and_send_record (hself: &ActorHandle<SentinelConnectorMsg>, client: &Client, base_uri: &str, access_token: &str, 
                                  device_id: &str, sensor_no: u32, capability: SensorCapability) -> Result<()> 
{
    use SensorCapability::*;
    match capability {
        Accelerometer => Ok(hself.send_msg( get_latest_record::<AccelerometerData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Anemometer    => Ok(hself.send_msg( get_latest_record::<AnemometerData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Cloudcover    => Ok(hself.send_msg( get_latest_record::<CloudcoverData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Fire          => Ok(hself.send_msg( get_latest_record::<FireData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Gas           => Ok(hself.send_msg( get_latest_record::<GasData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Gps           => Ok(hself.send_msg( get_latest_record::<GpsData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Gyroscope     => Ok(hself.send_msg( get_latest_record::<GyroscopeData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Image         => Ok(hself.send_msg( get_latest_record::<ImageData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Magnetometer  => Ok(hself.send_msg( get_latest_record::<MagnetometerData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Orientation   => Ok(hself.send_msg( get_latest_record::<OrientationData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Person        => Ok(hself.send_msg( get_latest_record::<PersonData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Power         => Ok(hself.send_msg( get_latest_record::<PowerData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Smoke         => Ok(hself.send_msg( get_latest_record::<SmokeData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Thermometer   => Ok(hself.send_msg( get_latest_record::<ThermometerData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Valve         => Ok(hself.send_msg( get_latest_record::<ValveData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
        Voc           => Ok(hself.send_msg( get_latest_record::<VocData>(client, base_uri, access_token, device_id, sensor_no).await?).await?),
    }
}

/* #region websocket messages ***********************************************************************/

// in:      {"event":"connected","data": {"message": "connected"}}
// out+in:  {"event":"join", "data":{ "deviceIds":["roo7gd1dldn3"], "messageId":"test-1"}}
// in:      {"event":"record","data":{"deviceId":"roo7gd1dldn3","sensorNo":37,"type":"image"}}

/// the notifications we get from the Delphire server through the websocket
#[derive(Serialize,Deserialize,Debug,PartialEq)]
#[serde(tag="event", content="data", rename_all="lowercase")]
pub enum WsMsg {
    #[serde(rename_all="camelCase")]
    Connected { message: String },

    #[serde(rename_all="camelCase")]
    Join { device_ids: Vec<String>, message_id: String },

    #[serde(rename_all="camelCase")]
    Record { device_id: String, sensor_no: u32, #[serde(alias="type")] rec_type: SensorCapability },

    #[serde(rename_all="camelCase")]
    Pong { request_time: u64, response_time: u64, message_id: String },

    #[serde(alias="trigger-alert",rename_all="camelCase")]
    TriggerAlert { device_id: String, message_id: String, result: String },

    #[serde(rename_all="camelCase")]
    Error { message: String }
}

/// outgoing websocket messages
#[derive(Serialize,Deserialize,Debug,PartialEq)]
#[serde(tag="event", content="data", rename_all="lowercase")]
pub enum WsCmd {
    #[serde(rename_all="camelCase")]
    Ping { request_time: u64, message_id: String },  // time is epoch millis

    #[serde(alias="trigger-alert",rename_all="camelCase")]
    TriggerAlert { device_ids: Vec<String>, message_id: String },

    #[serde(alias="switch-lights", rename_all="camelCase")]
    SwitchLights { device_ids: Vec<String>, #[serde(alias="type")] light_type: String, state: String, message_id: String },

    #[serde(alias="switch-valve", rename_all="camelCase")]
    SwitchValve { device_ids: Vec<String>, state: String, message_id: String  },
}

impl WsCmd {
    pub fn new_ping (msg_id: impl ToString)-> WsCmd {
        WsCmd::Ping { request_time: Utc::now().timestamp_millis() as u64, message_id: msg_id.to_string() }
    }
}

/* #endregion websocket messages */