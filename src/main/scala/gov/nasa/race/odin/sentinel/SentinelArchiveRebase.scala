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

import gov.nasa.race.common.{JsonArray, JsonLong, JsonNumber, JsonObject, JsonPrintOptions, UTF8JsonPullParser}
import gov.nasa.race.ifSome
import gov.nasa.race.tool.{TaggedArchiveRepair, TaggedArchiveRepairReader}
import gov.nasa.race.uom.{DateTime, Time}

import java.io.{ByteArrayOutputStream, InputStream, PrintStream}

/**
 * offline tool to change replay and payload time stamps in sentinel archives
 *
 * note we base this on generic JSON parsing of archive entry payloads and do not assume a specific record format,
 * other than records (optionally) having a "timeRecorded" member with epoch seconds. This is to allow for (some)
 * record format changes (we get records from the Delphire server)
 *
 *    {"data":[
 *      {"id":1066211,"timeRecorded":1598127579,"sensorNo":2,"deviceId":13,"voc":{"TVOC":0,"eCO2":400,"recordId":1066211}},
 *      ...
 *    ], "count": 42}
 */
object SentinelArchiveRebase extends TaggedArchiveRepair {

  class SentinelArchiveReader (is: InputStream) extends TaggedArchiveRepairReader(is) {
    val parser = new UTF8JsonPullParser
    val fmt = JsonPrintOptions(noNullMembers = true,pretty = false)
    val bao = new ByteArrayOutputStream(8192)
    val ps = new PrintStream(bao)

    def printRecord (jo: JsonObject, dt: Time): Unit = {
      jo.get("timeRecorded") match {
        case Some(jn: JsonNumber) =>
          val d = DateTime.ofEpochSeconds(jn.asLong) + dt
          jo += ("timeRecorded" -> JsonLong(d.toEpochSeconds))
        case _ => // no member to replace
      }
      jo.printOn(ps,fmt)
    }

    override protected def getPayload(len: Int, dt: Time): String = {
      if (parser.initialize(buf, len)) {
        ifSome(parser.parseJsonValue) { jv=>
          var nRec=0
          bao.reset()
          jv match {
            case jo: JsonObject =>
              jo.get("data") match {
                case Some(ja: JsonArray) =>
                  ps.println("""{"data":[""")
                  ja.foreach {
                    case jo: JsonObject =>
                      if (nRec > 0) ps.println(",")
                      printRecord(jo, dt)
                      nRec += 1
                    case _ => println("Error: expected JSON object as data element")
                  }
                  ps.print(s"""\n],"count":$nRec}""")
                  ps.flush()
                case _ => println("Error: expected JSON \"data\" array")
              }
            case _ => println("Error: expected JSON object payload")
          }
        }
        bao.toString
      } else ""
    }
  }

  override protected def createReader (is: InputStream): TaggedArchiveRepairReader = new SentinelArchiveReader(is)
}
