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
import gov.nasa.race.common.ConstUtf8Slice.utf8
import gov.nasa.race.common.{JsonSerializable, JsonWriter, UTF8JsonPullParser}
import gov.nasa.race.odin.sentinel.Sentinel.{ACTION, DATA, DEVICE_IDS, EVENT, INJECT}

import java.net.InetSocketAddress

object SentinelCommand {
  val COMMAND = asc("command")
}
import SentinelCommand._

/**
 * a command we send to the Delphire server through the websocket . Note this needs to be extensible
 */
trait SentinelCommand extends JsonSerializable {

  def requestId: String = "" // TODO - we need this to properly route responses back to the originators

  override def serializeMembersTo(writer: JsonWriter): Unit = {
    writer.writeStringMember("event", "command")
    writer.writeObjectMember("data") { w=>
      serializeDataMembersTo(w)
    }
  }

  def serializeDataMembersTo(writer: JsonWriter): Unit
}

case class InjectCommand (deviceIds: Seq[String]) extends SentinelCommand {

  def serializeDataMembersTo(writer: JsonWriter): Unit = {
    writer.writeStringMember("action", "inject")
    writer.writeArrayMember("deviceIds") { w=>
      deviceIds.foreach(w.writeString)
    }
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
    var action = utf8("")
    var deviceIds = Seq.empty[String]

    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case EVENT => if (value != COMMAND) return None // shortcut
      case DATA =>
        if (isInObject) {
          foreachMemberInCurrentObject {
            case ACTION => action = quotedValue.constCopy
            case DEVICE_IDS => deviceIds = readCurrentStringArray()
          }
        } else return None // shortcut

      case _ => // ignore
    }

    action match {
      case INJECT => if (deviceIds.nonEmpty) Some(InjectCommand(deviceIds)) else None
      case _ => None // unsupported command
    }
  }
}