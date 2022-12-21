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
import gov.nasa.race.common.JsonWriter
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.{BusEvent, PeriodicRaceActor, PublishingRaceActor, SubscribingRaceActor}
import gov.nasa.race.http.{HttpActor, SyncWSAdapterActor, WsConnectRequest}
import gov.nasa.race.{ifInstanceOf, ifSome, ifTrueSome}
import gov.nasa.race.odin.sentinel.SentinelSensorReading.{DefaultImageDir, IMAGE_PREFIX}
import gov.nasa.race.uom.{DateTime, Time}
import gov.nasa.race.uom.Time.Seconds
import gov.nasa.race.util.FileUtils

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Success, Failure => FailureEx}

/**
 * actor for realtime (bi-directional) communication with Delphire Sentinel fire sensors
 */
class SentinelConnectorActor(val config: Config) extends PublishingRaceActor
                       with SubscribingRaceActor with HttpActor with SyncWSAdapterActor with PeriodicRaceActor  {

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

  // heartbeats - we periodically check if the websocket is open. If not, we try to re-open it and poll the latest
  // sentinel readings as a fallback
  override val TickIntervalKey: String = "heartbeat-interval"
  override def defaultTickInterval: FiniteDuration = 60.seconds
  val reconnectDelay: FiniteDuration = config.getFiniteDurationOrElse("reconnect-delay", 5.seconds)

  // websocket connection parameters
  val connectTimeout = config.getFiniteDurationOrElse("connect-timeout", 10.seconds)
  var nFailedConnects = 0 // number of consecutive connection timeouts/failures
  val maxFailedConnects = config.getIntOrElse("max-failed-connects", 100)
  var nSuccessfulConnects = 0

  val maxHistory = config.getIntOrElse("max-history", 5) // number of recent readings. do we need that per-sensor?
  val imageDir = FileUtils.ensureWritableDir(config.getStringOrElse("image-dir", DefaultImageDir)).get

  val jsonWriter = new JsonWriter()
  val parser = new SentinelParser

  val devices: mutable.Map[String,DeviceEntry] = mutable.Map.empty
  var lastUpdate: DateTime = DateTime.UndefinedDateTime


  //--- RaceActor interface
  override def handleMessage: Receive = handleSentinelRaceMessage.orElse( handleWsMessage)

  override def onStartRaceActor(originator: ActorRef): Boolean = {
    super.onStartRaceActor(originator) && {
      requestDevices()
      true
    }
  }

  //--- PeriodicRaceActor interface (heartbeats)

  override def onRaceTick(): Unit = {
    if (isConnected) {
      sendPing() // through websocket

    } else { // get latest data and try to (re)connect websocket
      pollDevices()
      if (nSuccessfulConnects > 0) tryReConnect(wsUri) // only try reconnect if we previously had a connection
    }

    super.onRaceTick()
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
    nSuccessfulConnects += 1
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
      info(s"scheduling reconnect to $uri in $reconnectDelay, remaining attempts: ${maxFailedConnects - nFailedConnects}")
      scheduleOnce(reconnectDelay, WsConnectRequest(uri,connectTimeout))
    } else if (nFailedConnects == maxFailedConnects) {
        warning(s"max connection attempts to notification websocket $uri exceeded, falling back to polling.")
    }
  }

  //--- actor specifics

  // websocket messages received from Sentinel server
  def handleSentinelNotification (data: Array[Byte]): Unit = {
    info(s"received notification '${new String(data)}'")

    if (parser.initialize(data)) {
      parser.parseNotification() match {
        case Some(sn) =>
          sn match {
            case n: SentinelPongNotification => processPongResponse(n)

            case n: SentinelJoinNotification => info(s"""joined sentinel notification for devices: ${n.deviceIds.mkString(",")}""")
            case n: SentinelRecordNotification => requestUpdateRecord( n.devideId, n.sensorNo, n.sensorCapability)
            case n: SentinelErrorNotification => warning(s"received error notification: '${n.message}'")

            case n: TriggerAlertNotification => processCommandResponse(n,data)
            case n: SwitchLightsNotification => processCommandResponse(n,data)

            case other => warning(s"ignoring unknown notification $other")
          }

        case None => warning(s"ignoring malformed sensor notification: ${new String(data)}")
      }
    }
  }

  // messages received from the bus or point-to-point
  def handleSentinelRaceMessage: Receive = {
    //--- user commands (via route)
    case BusEvent(_,cr:SentinelCommandRequest,_) => processSentinelCommandRequest(cr)

    //--- self messages
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

  override def isReadyToSchedule: Boolean = {
    devices.exists( e=> e._2.sensors.nonEmpty) // we can schedule checks once we have acquired devices with sensors
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

          startScheduler // we are ready to poll (and check websocket connection)

        case None => warning(s"ignoring sensor info for unknown device: ${msg.deviceId}")
      }
    }
  }

  def registerForDeviceUpdates(deviceId: String): Unit = {
    val msg = s"""{"event":"join", "data":{"deviceIds":["$deviceId"],"messageId":"${SentinelCommand.newMsgId()}"}}"""
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

  //--- commands

  var pendingCmdRequests: Map[String,SentinelCommandRequest] = Map.empty  // messageId -> cr

  def processSentinelCommandRequest(cr: SentinelCommandRequest): Unit = {
    val msg = jsonWriter.toNewJson(cr.cmd)
    info(s"sending command: '$msg'")

    pendingCmdRequests = pendingCmdRequests + (cr.cmd.msgId -> cr)

    processOutgoingMessage(msg)
  }

  def processCommandResponse (response: SentinelResponse, data: Array[Byte]): Unit = {
    pendingCmdRequests.get(response.msgId) match {
      case Some(cr) =>
        pendingCmdRequests = pendingCmdRequests - response.msgId
        val msgText = ifTrueSome(cr.requestText){ new String(data) }
        cr.sender ! SentinelCommandResponse(cr, response, msgText)

      case None =>
        warning(s"unexpected command response: $response")
    }
  }


  //--- heartbeats are a special (automatically processed) command type (we don't put them into pendingCmdRequests)

  var lastPing: DateTime = DateTime.UndefinedDateTime
  var roundTripTime: Time = Time.UndefinedTime // this includes network latency
  var serverResponseTime: Time = Time.UndefinedTime // time between request and server response

  def sendPing(): Unit = {
    if (isConnected) {
      lastPing = DateTime.now // this is always wallclock time
      val cmd = PingCommand(SentinelCommand.newMsgId(), lastPing)
      val msg = jsonWriter.toNewJson(cmd)
      info(s"sending ping to remove server at $lastPing")
      processOutgoingMessage(msg)
    }
  }

  def processPongResponse (pong: SentinelPongNotification): Unit = {
    if (pong.requestTime == lastPing) {
      roundTripTime = DateTime.now.timeSince(lastPing)
      serverResponseTime = pong.responseTime.timeSince(lastPing)
      info(s"received pong response with roundTrip: $roundTripTime ($serverResponseTime)")

      // TODO - add roundtrip monitoring here

    } else {
      warning(s"received unsolicited Pong notification: $pong, last ping at: $lastPing")
    }
  }

  //--- events

  // TODO - not yet clear what we do with these
  def processSentinelEvent(notification: SentinelEventNotification): Unit = {
    warning(s"ignoring $notification")
  }

  //--- simulation & debugging

  val fireImage: Option[String] = config.getOptionalFile("fire-image").map( file => {
    val fname = file.getName
    val dest = new File(imageDir, fname)
    Files.copy(file.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
    fname
  })

  def simulateFire(): Unit = {
    findFirstDeviceWithCapability("fire") match {
      case Some((deviceId,sensorNo)) =>
        val date = DateTime.now
        fireImage match {
          case Some(fileName) =>
            info(s"simulating fire with image $fileName at $date")
            val imgReading = SentinelImageReading(deviceId,sensorNo,"42", date, fileName, false)
            publish( SentinelUpdates( Seq(imgReading)))

            val fireReading = SentinelFireReading(deviceId,sensorNo,"43", date + Seconds(5), 0.942)
            scheduleOnce(5.seconds) { publish(SentinelUpdates( Seq(fireReading))) }

          case None =>
            info(s"simulating fire at $date")
            val fireReading = SentinelFireReading(deviceId,sensorNo,"43", date, 0.942)
            publish(SentinelUpdates( Seq(fireReading)))
        }

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

  def findFirstDeviceWithCapability (cap: String): Option[(String,Int)] = {
    var deviceId: String = null
    var sensorNo: Long = 0

    devices.find( e => {
      val (devId,de) = e
      de.sensors.exists(e => {
        val (sn, si) = e
        si.capabilities.exists( e => {
          val (cs, _) = e
          if (cs.equalsString(cap)) {
            deviceId = devId
            sensorNo = sn
            true
          } else false
        })
      })
    }).map(_ => (deviceId,sensorNo.toInt))
  }
}
