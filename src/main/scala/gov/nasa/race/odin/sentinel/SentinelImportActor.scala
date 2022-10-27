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
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.ws.Message
import com.typesafe.config.Config
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.{PeriodicRaceActor, PublishingRaceActor}
import gov.nasa.race.http.{HttpActor, SyncWSAdapterActor, WsConnectRequest}
import gov.nasa.race.ifInstanceOf
import gov.nasa.race.odin.sentinel.SentinelSensorReading.IMAGE_PREFIX
import gov.nasa.race.uom.DateTime
import gov.nasa.race.util.FileUtils

import java.io.File
import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Success, Failure => FailureEx}

/**
 * actor for realtime import of Sentinel sensor data from Delphire servers
 */
class SentinelImportActor (val config: Config) extends PublishingRaceActor with HttpActor with SyncWSAdapterActor with PeriodicRaceActor  {

  // not thread-safe, access only from actor thread
  class DeviceEntry (val deviceInfo: SentinelDeviceInfo) {
    var sensors: Map[Long,SentinelSensorInfo] = Map.empty  // sensorNo->sensorInfo (set during processSensors)
  }

  //--- internal (self) messages to transfer control back from I/O into actor thread
  case class DeviceResponse(data: Array[Byte])
  case class SensorResponse(deviceId: String, data: Array[Byte])
  case class RecordResponse(deviceId: String, sensorNo: Int, sensorCapability: String, data: Array[Byte])
  //case class RecordNotification (data: Array[Byte])

  private val delphireHost = s"${config.getVaultString("delphire.host")}:${config.getVaultInt("delphire.port")}"
  private val baseUrl = s"http://$delphireHost"
  private val requestHdrs = Seq(Authorization(OAuth2BearerToken(config.getVaultString("delphire.access-token"))))
  private val wsUri = s"ws://$delphireHost"

  // if those are set we fall back to polling if websocket connection is lost and can't be re-established
  val usePolling = config.getBooleanOrElse("use-polling", true)
  override def defaultTickInterval: FiniteDuration = 60.seconds

  // websocket connection parameters
  val connectTimeout = config.getFiniteDurationOrElse("connect-timeout", 10.seconds)
  var nFailedConnects = 0 // number of consecutive connection timeouts/failures
  val maxFailedConnects = config.getIntOrElse("max-failed-connects", 5)
  val reconnectInterval: FiniteDuration = config.getFiniteDurationOrElse("reconnect-interval", 30.seconds)

  val maxHistory = config.getIntOrElse("max-history", 5) // number of recent readings. do we need that per-sensor?
  val imageDir = FileUtils.ensureWritableDir(config.getStringOrElse("image-dir", s"tmp/delphire/$IMAGE_PREFIX")).get

  val parser = new SentinelParser

  val devices: mutable.Map[String,DeviceEntry] = mutable.Map.empty
  var lastUpdate: DateTime = DateTime.UndefinedDateTime

  //--- RaceActor interface
  override def handleMessage: Receive = handleSentinelImportMessage.orElse( handleWsMessage)

  override def onStartRaceActor(originator: ActorRef): Boolean = {
    super.onStartRaceActor(originator) && {
      requestDevices()
      true
    }
  }

  //--- PeriodicRaceActor interface (in case we are either configured or have to fall back to polling)
  override def onRaceTick(): Unit = {
    if (usePolling) pollDevices()
  }

  //--- WsAdapterActor interface
  override def getWsUri = Some(wsUri)
  override def getWebSocketRequestHeaders(): Seq[HttpHeader] = requestHdrs

  override protected def processIncomingMessage (msg: Message): Unit = {
    withMessageData(msg) { data=>
      if (data.length > 0) handleSentinelNotification(data)
    }
  }

  //--- websocket connection state callbacks

  override def onConnect(): Unit = {
    info(s"connected to $wsUri")
    devices.foreach( e=> registerForDeviceUpdates(e._1)) // note we only connect after we already got devices
  }
  override def onConnectFailed (uri: String, cause: String): Unit = {
    warning(s"connecting to web socket $uri failed with $cause")
    tryReConnect(uri)
  }
  override def onConnectTimeout(uri: String, dur: FiniteDuration): Unit = {
    warning(s"connecting to web socket $uri times out, fall back to polling")
    tryReConnect(uri)
  }

  override protected def onDisconnect(): Unit = {
    info(s"disconnected from $wsUri")
    if (!isTerminating) tryReConnect(wsUri)
  }

  def tryReConnect(uri: String): Unit = {
    nFailedConnects += 1
    if (nFailedConnects < maxFailedConnects) {
      info(s"scheduling reconnect to $uri in $reconnectInterval, remaining attempts: ${maxFailedConnects - nFailedConnects}")
      scheduleOnce(reconnectInterval, WsConnectRequest(uri,connectTimeout))
    } else {
      if (usePolling) {
        warning(s"max connection attempts to $uri exceeded, falling back to polling")
        startScheduler
      } else {
        warning(s"max connection attempts to $uri exceeded, data acquisition terminated.")
      }
    }
  }

  //--- actor specifics

  // websocket messages
  def handleSentinelNotification(data: Array[Byte]): Unit = {
    if (parser.initialize(data)) {
      parser.parseNotification() match {
        case Some(sn) =>
          sn match {
            case sjn: SentinelJoinNotification => info(s"""joined sentinel notification for devices: ${sjn.deviceIds.mkString(",")}""")
            case srn: SentinelRecordNotification => requestUpdateRecord( srn.devideId, srn.sensorNo, srn.sensorCapability)
          }

        case None => warning(s"ignoring malformed sensor notification: ${new String(data)}")
      }
    }
  }

  // actor (self) messages
  def handleSentinelImportMessage: Receive = {
    case rsp: DeviceResponse => processDevices(rsp)
    case rsp: SensorResponse => processSensors(rsp)
    case rsp: RecordResponse => processRecords(rsp)

    //case rn: RecordNotification => handleSentinelRecordNotification(rn.data)

    //--- simulation & debugging
    case "simulateFire" => simulateFire()
    case "simulateSmoke" => simulateSmoke()
  }

  def requestDevices(): Unit = {
    val url = s"$baseUrl/devices"
    httpRequestStrict(uri=s"$baseUrl/devices", headers=requestHdrs) {
      case Success(strictEntity) => self ! DeviceResponse(strictEntity.getData().toArray)
      case FailureEx(x) => error(s"failed to obtain device list: $x")
    }
  }

  def processDevices(msg: DeviceResponse): Unit = {
    if (parser.initialize(msg.data)) {
      val deviceInfos =  parser.parseDevices()
      if (deviceInfos.nonEmpty) {
        deviceInfos.foreach { di=>
          devices += (di.deviceId -> new DeviceEntry(di))
          requestSensors(di.deviceId)
        }

        connect() // ready to open websocket

      } else warning(s"no devices")
    }
  }

  def requestSensors (deviceId: String): Unit = {
    info(s"requesting sensor info for device: $deviceId")
    httpRequestStrict(uri=s"$baseUrl/devices/$deviceId/sensors?sort=no,ASC", headers=requestHdrs) {
      case Success(strictEntity) => self ! SensorResponse(deviceId, strictEntity.getData().toArray)
      case FailureEx(x) => error(s"failed to obtain sensor list for device $deviceId: $x")
    }
  }

  def processSensors(msg: SensorResponse): Unit = {
    if (parser.initialize(msg.data)) {
      val sensorInfos = parser.parseSensors()
      val deviceId = msg.deviceId
      devices.get(deviceId) match {
        case Some(de) =>
          de.sensors = Map.from( sensorInfos.map(si => si.sensorNo.toLong -> si))
          sensorInfos.foreach { si=>
            si.capabilities.foreach { capEntry =>
              requestInitialRecords(deviceId, si.sensorNo, capEntry._1.toString)
            }
          }

        case None => warning(s"ignoring sensor info for unknown device: ${msg.deviceId}")
      }
    }
  }

  def registerForDeviceUpdates(deviceId: String): Unit = {
    val msg = s"""{"event":"join", "data":{"deviceIds":["$deviceId"]}}"""
    info(s"sending join message: $msg")
    processOutgoingMessage(msg)
  }

  def requestInitialRecords (deviceId: String, sensorNo: Int, sensorCapability: String): Unit = {
    val url = s"$baseUrl/devices/$deviceId/sensors/$sensorNo/$sensorCapability?sort=timeRecorded,DESC&limit=$maxHistory"
    requestRecords(deviceId, sensorNo, sensorCapability, url)
  }

  def requestUpdateRecord (deviceId: String, sensorNo: Int, sensorCapability: String): Unit = {
    val url = s"$baseUrl/devices/$deviceId/sensors/$sensorNo/$sensorCapability?sort=timeRecorded,DESC&limit=1"
    requestRecords(deviceId, sensorNo, sensorCapability, url)
  }

  def requestRecords (deviceId: String, sensorNo: Int, sensorCapability: String, url: String): Unit = {
    httpRequestStrictWithRetry(url, headers = requestHdrs, retries=maxRetry) {
      case Success(strictEntity) => self ! RecordResponse(deviceId, sensorNo, sensorCapability, strictEntity.getData().toArray)
      case FailureEx(x) => error(s"failed to obtain initial records: $x")
    }
  }

  def processRecords(msg: RecordResponse): Unit = {
    if (parser.initialize(msg.data)) {
      val ssrs = parser.parseRecords().filter(updateDevice)
      if (ssrs.nonEmpty) publish(  SentinelUpdates(ssrs))
    }
  }


  def updateDevice (r: SentinelSensorReading): Boolean = {
    val recordType = r.readingType

    for (di <- devices.get(r.deviceId); si <- di.sensors.get(r.sensorNo)) {
      si.capabilities.get(recordType) match {
        case Some(None) => // first record
          si.capabilities += (recordType -> Some(r))
        case Some(Some(rLast)) =>
          if (rLast.recordId == r.recordId || rLast.date > r.date) return false  // we already have it
          si.capabilities += (recordType -> Some(r))
        case None => return false  // unknown capability
      }

      lastUpdate = r.date
      ifInstanceOf[SentinelImageReading](r)( requestImage)
      return true
    }

    false // ignore unknown device of sensor or capability
  }

  def requestImage (imgRec: SentinelImageReading): Unit = {
    val url = s"$baseUrl/images/${imgRec.recordId}"
    httpRequestStrictWithRetry(url,headers = requestHdrs, retries=maxRetry) {
      case Success(strictEntity) =>
        val file = new File(imageDir,imgRec.fileName)

        if (FileUtils.setFileContents(file, strictEntity.getData().toArray)){
          info(s"saved image file $file")
        } else warning(s"failed to save image for record ${imgRec.recordId}")

      case FailureEx(x) => error(s"failed to retrieve image ${imgRec.recordId}: $x")
    }
  }

  def pollDevices(): Unit = {
    devices.foreach { e=>
      val (deviceId,de) = e
      info(s"polling sensors of device: $deviceId")
      de.sensors.foreach { e=>
        val (sensorNo,si) = e
        si.capabilities.foreach { e=>
          val sensorCap = e._1
          requestUpdateRecord(deviceId, sensorNo.toInt, sensorCap.toString)
        }
      }
    }
  }

  //--- simulation & debugging

  def findFirstDeviceWithCapability(cap: String): Option[(String,Long)] = {
    if (devices.nonEmpty) {
      devices.foreach { e =>
        val (deviceId, de) = e
        de.sensors.foreach { e =>
          val (sensorNo, si) = e
          si.capabilities.foreach { e =>
            val sensorCap = e._1
            if (sensorCap == cap) {
              return Some(deviceId, sensorNo)
            }
          }
        }
      }
    }
    None
  }

  def simulateFire(): Unit = {
    findFirstDeviceWithCapability("fire") match {
      case Some((deviceId,sensorNo)) =>
        val fireReading = SentinelFireReading(deviceId,sensorNo.toInt,"42", DateTime.now, 0.942)
        publish( new SentinelUpdates( Seq(fireReading)))
      case None =>  warning(s"cannot simulate fire - no suitable device found")
    }
  }

  def simulateSmoke(): Unit = {
    findFirstDeviceWithCapability("smoke") match {
      case Some((deviceId,sensorNo)) =>
        val fireReading = SentinelSmokeReading(deviceId,sensorNo.toInt,"42", DateTime.now, 0.942)
        publish( new SentinelUpdates( Seq(fireReading)))
      case None =>  warning(s"cannot simulate smoke - no suitable device found")
    }
  }
}
