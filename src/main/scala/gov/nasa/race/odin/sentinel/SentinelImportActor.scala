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
package gov.nasa.race.odin.sentinel

import akka.actor.ActorRef
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import com.typesafe.config.Config
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.PublishingRaceActor
import gov.nasa.race.http.HttpActor

import scala.util.{Success, Failure => FailureEx}

/**
 * actor for realtime import of Sentinel sensor data from Delphire servers
 */
class SentinelImportActor (val config: Config) extends PublishingRaceActor with HttpActor {

  case class DeviceResponse(bs: Array[Byte])
  case class SensorResponse(deviceId: String, bs: Array[Byte])

  private val baseUrl = s"http://${config.getVaultString("delphire.host")}:${config.getVaultInt("del")}/"
  private val requestHdrs = Seq(Authorization(OAuth2BearerToken(config.getVaultString("delphire.access-token"))))

  val parser = new SentinelParser

  override def handleMessage: Receive = {
    case rsp: DeviceResponse => processDevices(rsp)
    case rsp: SensorResponse => processSensors(rsp)
  }

  override def onStartRaceActor(originator: ActorRef): Boolean = {
    super.onStartRaceActor(originator) && {
      requestDevices()
      true
    }
  }


  def requestDevices(): Unit = {
    httpGetRequestStrict(s"$baseUrl/devices, $requestHdrs").onComplete {
      case Success(strictEntity) => self ! DeviceResponse(strictEntity.getData().toArray)
      case FailureEx(x) => error(s"failed to obtain device list: $x")
    }
  }

  def processDevices(msg: DeviceResponse): Unit = {

  }

  def requestSensors (deviceId: String): Unit = {
    httpGetRequestStrict(s"$baseUrl/devices/$deviceId/sensors, $requestHdrs").onComplete {
      case Success(strictEntity) => self ! SensorResponse(deviceId, strictEntity.getData().toArray)
      case FailureEx(x) => error(s"failed to obtain sensor list for device $deviceId: $x")
    }
  }

  def processSensors(rsp: SensorResponse): Unit = {

  }
}
