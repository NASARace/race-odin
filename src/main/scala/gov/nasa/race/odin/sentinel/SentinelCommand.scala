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
import gov.nasa.race.common.ConstAsciiSlice.asc
import gov.nasa.race.common.{JsonSerializable, JsonWriter, UTF8JsonPullParser}
import gov.nasa.race.odin.sentinel.Sentinel.{DATA, DEVICE_IDS, EVENT, MESSAGE_ID}

import java.net.InetSocketAddress

object SentinelCommand {
  val STATE = asc("state")
  val TYPE = asc("type")
  val TRIGGER_ALERT = asc("trigger-alert")
  val SWITCH_VALVE = asc("switch-valve")
  val SWITCH_LIGHTS = asc("switch-lights")

  var nMsgs = 0

  def newMsgId(): String = synchronized {
    nMsgs += 1
    s"MSG-$nMsgs"
  }
}
import SentinelCommand._

/**
 * a command we send to the Delphire server through the websocket . Note this needs to be extensible
 */
trait SentinelCommand extends JsonSerializable {
  val event: String
  val msgId: String

  override def serializeMembersTo(writer: JsonWriter): Unit = {
    writer.writeStringMember("event", event)
    writer.writeObjectMember("data") { w=>
      serializeDataMembersTo(w)
    }
  }

  def serializeDataMembersTo(writer: JsonWriter): Unit
}

case class TriggerAlertCommand(msgId: String, deviceIds: Seq[String]) extends SentinelCommand {
  val event = TRIGGER_ALERT.toString()

  def serializeDataMembersTo(writer: JsonWriter): Unit = {
    writer.writeArrayMember("deviceIds") { w=> deviceIds.foreach(w.writeString) }
    writer.writeStringMember( MESSAGE_ID, msgId)
  }
}

case class SwitchLightsCommand(msgId: String, deviceIds: Seq[String], subject: String, state: String) extends SentinelCommand {
  val event = SWITCH_LIGHTS.toString()

  def serializeDataMembersTo(writer: JsonWriter): Unit = {
    writer.writeStringMember("type", subject)
    writer.writeStringMember("state", state)
    writer.writeArrayMember("deviceIds") { w=> deviceIds.foreach(w.writeString) }
    writer.writeStringMember( MESSAGE_ID, msgId)
  }
}

case class SwitchValveCommand(msgId: String, deviceIds: Seq[String], state: String) extends SentinelCommand {
  val event = SWITCH_VALVE.toString()

  def serializeDataMembersTo(writer: JsonWriter): Unit = {
    writer.writeStringMember("state", state)
    writer.writeArrayMember("deviceIds") { w=> deviceIds.foreach(w.writeString) }
    writer.writeStringMember( MESSAGE_ID, msgId)
  }
}

//---wrappers that associates the requesting client with the command/response
case class SentinelCommandRequest (sender: ActorRef, remoteAddress: InetSocketAddress, cmd: SentinelCommand, requestText: Boolean)
case class SentinelCommandResponse (remoteAddress: InetSocketAddress, response: Option[SentinelNotification], text: Option[String])

/**
 * JSON parsing support for SentinelCommands
 */
trait SentinelCommandParser extends UTF8JsonPullParser {

  def parseSentinelCommand(): Option[SentinelCommand] = {
    var res: Option[SentinelCommand] = None

    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case EVENT => quotedValue match {
        case TRIGGER_ALERT => res = parseTriggerAlert()
        case SWITCH_LIGHTS => res = parseSwitchLights()
        //... more to follow
      }

      case _ => // ignore
    }
    res
  }

  def parseTriggerAlert(): Option[TriggerAlertCommand] = {
    var res: Option[TriggerAlertCommand] = None
    foreachRemainingMember {
      case DATA =>
        var deviceIds = Seq.empty[String]
        var msgId = ""

        foreachMemberInCurrentObject {
          case DEVICE_IDS => deviceIds = readCurrentStringArray()
          case MESSAGE_ID => msgId = quotedValue.intern
        }
        if (msgId.isEmpty) msgId = newMsgId()
        res = Option.when(deviceIds.nonEmpty && !msgId.isEmpty)( TriggerAlertCommand(msgId, deviceIds))

      case _ =>
    }
    res
  }

  def parseSwitchLights(): Option[SwitchLightsCommand] = {
    var res: Option[SwitchLightsCommand] = None
    foreachRemainingMember {
      case DATA =>
        var deviceIds = Seq.empty[String]
        var msgId = ""
        var subject = ""
        var state = ""

        foreachMemberInCurrentObject {
          case DEVICE_IDS => deviceIds = readCurrentStringArray()
          case MESSAGE_ID => msgId = quotedValue.intern
          case TYPE => subject = quotedValue.intern
          case STATE => state = quotedValue.intern
        }
        if (msgId.isEmpty) msgId = newMsgId()
        res = Option.when(deviceIds.nonEmpty && !msgId.isEmpty)( SwitchLightsCommand(msgId, deviceIds, subject, state))

      case _ =>
    }
    res
  }
}