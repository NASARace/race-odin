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

import gov.nasa.race.common.ConstAsciiSlice.asc
import gov.nasa.race.common.UTF8JsonPullParser
import gov.nasa.race.odin.sentinel.Sentinel.{DATA, DEVICE_ID, DEVICE_IDS, MESSAGE_ID, SENSOR_NO, SENSOR_TYPE}
import gov.nasa.race.odin.sentinel.SentinelCommand.{STATE, TRIGGER_ALERT}

object SentinelNotification {
  val JOIN = asc("join")
  val RECORD = asc("record")
  val RECEIVED = asc("received")
  val ACTION = asc("action")
  val RESULT = asc("result")
  val SUCCESS = asc("success")
  val EVENT = asc("event")
  val ERROR = asc("error")
  val TYPE = asc("type")
  val MESSAGE = asc("message")
}
import SentinelNotification._

/**
 * something we get from the Delphire server through the websocket
 */
trait SentinelNotification

/**
 * notification about successful join for device/sensor update notifications
 */
case class SentinelJoinNotification (deviceIds: Seq[String], msgId: String) extends SentinelNotification

/**
 * notification that there is a new sensor record available
 */
case class SentinelRecordNotification (devideId: String, sensorNo: Int, sensorCapability: String) extends SentinelNotification

/**
 * error reported by the sentinel server
 */
case class SentinelErrorNotification (message: String) extends SentinelNotification

/**
 * TODO - check if this is just a failed command response
 */
case class SentinelReceivedNotification (deviceIds: Seq[String]) extends SentinelNotification

/**
 * response to a command we send to the Delphire server
 */
case class SentinelActionNotification (action: String, success: Boolean, deviceIds: Seq[String]) extends SentinelNotification

/**
 * response to a TriggerAlertCommand
 */
case class TriggerAlertNotification (deviceId: String, msgId: String, result: String) extends SentinelNotification

/**
 * response to a SwitchLightsCommand
 */
case class SwitchLightsNotification (deviceId: String, msgId: String, subject: String, state: String) extends SentinelNotification

/**
 * a remote server event that is not a response (error messages etc)
 */
case class SentinelEventNotification (event: String, details: Option[String]) extends SentinelNotification

/**
 * JSON parsing support for SentinelNotifications
 */
trait SentinelNotificationParser extends UTF8JsonPullParser {

  def parseNotification(): Option[SentinelNotification] = {
    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case EVENT => value match {
        case JOIN => return parseJoinEvent()
        case RECORD => return parseRecordEvent()
        case ERROR => return parseErrorEvent()
        case TRIGGER_ALERT => return parseTriggerAlertEvent()
        case other => warning(s"unknown event type '$other'")
      }
      case other => warning(s"unknown message '$other'")
    }
    None
  }

  def parseJoinEvent(): Option[SentinelJoinNotification] = {
    var res: Option[SentinelJoinNotification] = None
    foreachRemainingMember {
      case DATA =>
        var deviceIds = Seq.empty[String]
        var msgId = ""

        foreachMemberInCurrentObject {
          case DEVICE_IDS => deviceIds = readCurrentStringArray()
          case MESSAGE_ID => msgId = quotedValue.intern
        }
        res = Option.when(deviceIds.nonEmpty && !msgId.isEmpty)( SentinelJoinNotification(deviceIds, msgId))

      case _ =>
    }
    res
  }

  def parseRecordEvent(): Option[SentinelRecordNotification] = {
    var res: Option[SentinelRecordNotification] = None
    foreachRemainingMember {
      case DATA =>
        var deviceId = ""
        var sensorNo = -1
        var recType = ""

        foreachMemberInCurrentObject {
          case DEVICE_ID => deviceId = quotedValue.intern
          case SENSOR_NO => sensorNo = unQuotedValue.toInt
          case TYPE => recType = quotedValue.intern
          case _ => // ignore
        }
        res = Option.when(!deviceId.isEmpty && sensorNo >= 0 && !recType.isEmpty)( SentinelRecordNotification(deviceId, sensorNo, recType))

      case _ =>
    }
    res
  }

  def parseErrorEvent(): Option[SentinelErrorNotification] = {
    var res: Option[SentinelErrorNotification] = None
    foreachRemainingMember {
      case DATA =>
        var msg = ""
        foreachMemberInCurrentObject {
          case MESSAGE => msg = quotedValue.toString()
          case _ => // ignore
        }
        res = Option.when(!msg.isEmpty)( SentinelErrorNotification(msg))
      case _ =>
    }
    res
  }

  def parseTriggerAlertEvent(): Option[TriggerAlertNotification] = {
    var res: Option[TriggerAlertNotification] = None
    foreachRemainingMember {
      case DATA =>
        var deviceId = ""
        var msgId = ""
        var result = ""

        foreachMemberInCurrentObject {
          case DEVICE_ID => deviceId = quotedValue.intern
          case MESSAGE_ID => msgId = quotedValue.toString()
          case RESULT => result = quotedValue.toString()
          case _ => // ignore
        }
        res = Option.when(!deviceId.isEmpty /*&& !msgId.isEmpty */ && !result.isEmpty)( TriggerAlertNotification(deviceId, msgId, result))
      case _ =>
    }
    res
  }


  def parseSwitchLightsEvent(): Option[SwitchLightsNotification] = {
    var res: Option[SwitchLightsNotification] = None
    foreachRemainingMember {
      case DATA =>
        var deviceId = ""
        var msgId = ""
        var subject = ""
        var state = ""

        foreachMemberInCurrentObject {
          case DEVICE_ID => deviceId = quotedValue.intern
          case MESSAGE_ID => msgId = quotedValue.toString()
          case STATE => quotedValue.intern
          case TYPE => quotedValue.intern
          case _ => // ignore
        }
        res = Option.when(!deviceId.isEmpty && !msgId.isEmpty && !subject.isEmpty && !state.isEmpty){
          SwitchLightsNotification(deviceId, msgId, subject, state)
        }
      case _ =>
    }
    res
  }


  /**
   *  TODO - not clear what the purpose is. Does it always precede an action response ?
   */
  def parseSentinelReceivedNotification(): Option[SentinelReceivedNotification] = {
    if (isInArray) {
      val deviceIds = readCurrentStringArray()
      Some(SentinelReceivedNotification(deviceIds))
    } else {
      warning(s"malformed received message")
      None
    }
  }

  /**
   * FIXME - this is still flat and uses a single deviceId, format needs to change
   * {"action":"inject","result":"success","deviceId":"roo7gd1dldn3"}
   */
  def parseSentinelActionNotification(): Option[SentinelActionNotification] = {
    val action = quotedValue.intern
    var result = false
    var deviceIds = Seq.empty[String]

    foreachRemainingMember {
      case RESULT => result = (quotedValue == SUCCESS)
      case DEVICE_ID => deviceIds = Seq(quotedValue.intern)
      case DEVICE_IDS => deviceIds = readCurrentInternedStringArray()
    }

    Some(SentinelActionNotification(action,result,deviceIds))
  }

  /**
   *
   * {"event":"error","data":{"message":"Forbidden resource"}}
   */
  def parseSentinelEventNotification(): Option[SentinelEventNotification] = {
    quotedValue match {
      case RECORD =>

    }

    val event = quotedValue.intern
    var data: Option[String] = None

    foreachRemainingMember {
      case DATA => data = Some(readTextOfCurrentLevel())
    }

    Some(SentinelEventNotification(event,data))
  }
}