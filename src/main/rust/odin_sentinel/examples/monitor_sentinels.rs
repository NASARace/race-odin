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

#[macro_use]
extern crate lazy_static;

use std::{sync::Arc,path::PathBuf};
use odin_sentinel::actor::TriggerJsonSnapshot;
use tokio;
use structopt::StructOpt;
use odin_actor::prelude::*;
use odin_actor::tokio_kanal::{ActorSystem,Actor,ActorHandle};
use odin_config::load_config;
use odin_sentinel::{SentinelConfig,actor::{SentinelConnector,SentinelConnectorMsg,AddInitCallback,AddJsonUpdateCallback}};
use anyhow::Result;


/* #region monitor actor *****************************************************************/

#[derive(Debug)] pub struct SentinelDataAvailable;

#[derive(Debug)] pub struct SentinelSnapshot(String);

#[derive(Debug)] pub struct SentinelUpdate(Arc<String>);

define_actor_msg_type! { SentinelMonitorMsg = SentinelDataAvailable | SentinelSnapshot | SentinelUpdate }

struct SentinelMonitor {
    source: ActorHandle<SentinelConnectorMsg> 
}

impl_actor! { match msg for Actor<SentinelMonitor,SentinelMonitorMsg> as
    _Start_ => cont! {
        let hself = &self.hself;
        let action = send_msg_callback!( hself <- || SentinelDataAvailable{} );
        let _ = self.source.send_msg( AddInitCallback {id: String::from(self.id()), action} ).await;
    }
    SentinelDataAvailable => cont! {
        let hself = &self.hself;
        let action = send_msg_callback!( hself <- |json: String| SentinelSnapshot(json));
        let _ = self.source.send_msg( TriggerJsonSnapshot(action) ).await;
        
        let action = send_msg_callback!( hself <- |json: Arc<String>| SentinelUpdate(json));
        let _ = self.source.send_msg( AddJsonUpdateCallback {id: String::from(self.id()), action} ).await;
    }
    SentinelSnapshot => cont! {
        println!("------------------------------ snapshot");
        println!("{}", msg.0);
    }
    SentinelUpdate => cont! { 
        println!("------------------------------ update");
        println!("{}", msg.0) 
    }
}

/* #endregion monitor actor */

#[derive(StructOpt)]
#[structopt(about = "example of how to use the SentinelConnector actor")]
struct CliOpts {
        /// path to sentinel config file
        config_path: PathBuf
}

lazy_static! {
    static ref ARGS: CliOpts = CliOpts::from_args();
}

#[tokio::main]
async fn main ()->Result<()> {
    let sentinel_config: SentinelConfig = load_config( &ARGS.config_path)?;
    let mut actor_system = ActorSystem::new("main");

    let importer = spawn_actor!( actor_system, "importer", SentinelConnector::new(sentinel_config))?;
    let monitor = spawn_actor!( actor_system, "monitor", SentinelMonitor{ source: importer })?;

    actor_system.start_all(millis(20)).await?;
    actor_system.process_requests().await?;

    Ok(())
}