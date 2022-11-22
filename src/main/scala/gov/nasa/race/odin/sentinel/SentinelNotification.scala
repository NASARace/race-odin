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
import gov.nasa.race.odin.sentinel.Sentinel.{DATA, DEVICE_ID, DEVICE_IDS, SENSOR_NO, SENSOR_TYPE}

object SentinelNotification {
  val JOINED = asc("joined")
  val NEW_RECORD = asc("NewRecord")
  val RECEIVED = asc("received")
  val ACTION = asc("action")
  val RESULT = asc("result")
  val SUCCESS = asc("success")
  val EVENT = asc("event")
}
import SentinelNotification._

/**
 * something we get from the Delphire server through the websocket
 */
trait SentinelNotification

/**
 * notification about successful join for device/sensor update notifications
 */
case class SentinelJoinNotification (deviceIds: Seq[String]) extends SentinelNotification

/**
 * notification that there is a new sensor record available
 */
case class SentinelRecordNotification (devideId: String, sensorNo: Int, sensorCapability: String) extends SentinelNotification

/**
 * TODO - check if this is just a failed command response
 */
case class SentinelReceivedNotification (deviceIds: Seq[String]) extends SentinelNotification

/**
 * response to a command we send to the Delphire server
 */
case class SentinelActionNotification (action: String, success: Boolean, deviceIds: Seq[String]) extends SentinelNotification


/**
 * a rempte server event that is not a response (error messages etc)
 */
case class SentinelEventNotification (event: String, details: Option[String]) extends SentinelNotification

/**
 * JSON parsing support for SentinelNotifications
 */
trait SentinelNotificationParser extends UTF8JsonPullParser {
  /**
   * parse sensor record notification messages (received through websocket). The following message types are supported:
   *
   * { "joined":["dizwqq96w36j"] }
   * { "NewRecord":{"deviceId":"dizwqq96w36j", "sensorNo":0, "type":"magnetometer"}}
   *
   * TODO - this should use regular syntax
   */
  def parseNotification(): Option[SentinelNotification] = {
    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case JOINED => return parseSentinelJoinNotification()
      case NEW_RECORD => return parseSentinelRecordNotification()
      case RECEIVED => return parseSentinelReceivedNotification()
      case ACTION => return parseSentinelActionNotification()
      case EVENT => return parseSentinelEventNotification()
      case other => warning(s"ignore unknown notification '$other'");
    }
    None
  }

  /**
   * notification that we successfully subscribed to newRecord notification for devices
   * {"joined":["roo7gd1dldn3"]}
   */
  def parseSentinelJoinNotification(): Option[SentinelJoinNotification] = {
    val devIds = readCurrentStringArray()
    if (devIds.nonEmpty) Some(SentinelJoinNotification(devIds)) else None
  }


  /**
   * notification about availability of new record:
   * {"NewRecord":{"deviceId":"roo7gd1dldn3","sensorNo":15,"type":"voc"}}
   */
  def parseSentinelRecordNotification(): Option[SentinelRecordNotification] = {
    var deviceId: String = null
    var sensorNo: Int = -1
    var sensorType: String = null

    foreachMemberInCurrentObject {
      case DEVICE_ID => deviceId = quotedValue.intern
      case SENSOR_NO => sensorNo = unQuotedValue.toInt
      case SENSOR_TYPE => sensorType = quotedValue.intern
    }

    if (deviceId != null && sensorNo >= 0 && sensorType != null) Some(SentinelRecordNotification(deviceId,sensorNo,sensorType)) else None
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
    val event = quotedValue.intern
    var data: Option[String] = None

    foreachRemainingMember {
      case DATA => data = Some(readTextOfCurrentLevel())
    }

    Some(SentinelEventNotification(event,data))
  }
}