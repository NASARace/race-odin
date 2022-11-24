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
import gov.nasa.race.common.{CharSeqByteSlice, ConstCharSequences, ConstUtf8Slice, JsonParseException, JsonSerializable, JsonWriter, UTF8JsonPullParser}
import gov.nasa.race.uom.DateTime
import gov.nasa.race.{Dated, ifSome}

import scala.collection.mutable.ArrayBuffer
import SentinelSensorReading._

import scala.collection.mutable

/**
 * Sentinel data model for Delphire's powerline fire sensors (see https://delphiretech.com/).
 */
object Sentinel {

  //--- lexical constants
  val DATA = asc("data")
  val IMAGES = asc("images")
  val INFO = asc("info")
  val DEVICE_ID = asc("deviceId")
  val SENSOR_NO = asc("sensorNo")
  val SENSOR_TYPE = asc("type")  // ?? should that be sensorCapability ?
  val NO = asc("no")
  val PART = asc("partNo")
  val CAPS = asc("capabilities")
  val CONFS = asc("camConfs")
  val CLAIMS = asc("claims")
  val EVIDENCE = asc("evidences")
  val EVENT = asc("event")
  val ACTION = asc("action")
  val INJECT = asc("inject")
  val DEVICE_IDS = asc("deviceIds")

  val MaxReadings = 10  // TODO - do we need per-sensor type limits and should it be configurable?
}
import Sentinel._

/**
  * Sentinel device state
 * This is basically a time-stamped snapshot of accumulated SentinelReading records
  */
case class Sentinel (
                      id: String, // deviceId
                      name: String,
                      date: DateTime = DateTime.UndefinedDateTime, // last sensor update
                      gps:    Seq[SentinelGpsReading]    = Seq.empty,
                      gyro:   Seq[SentinelGyroReading]   = Seq.empty,
                      mag:    Seq[SentinelMagReading]    = Seq.empty,
                      accel:  Seq[SentinelAccelReading]  = Seq.empty,
                      gas:    Seq[SentinelGasReading]    = Seq.empty,
                      thermo: Seq[SentinelThermoReading] = Seq.empty,
                      voc:    Seq[SentinelVocReading]    = Seq.empty,
                      anemo:  Seq[SentinelAnemoReading]  = Seq.empty,
                      fire:   Seq[SentinelFireReading]   = Seq.empty,
                      smoke:  Seq[SentinelSmokeReading]  = Seq.empty,
                      images: Seq[SentinelImageReading]  = Seq.empty
               ) extends Dated with JsonSerializable {

  def serializeMembersTo (w: JsonWriter): Unit = {
    w.writeStringMember(DEVICE_ID, id)
    w.writeStringMember(DEVICE_NAME, name)
    w.writeDateTimeMember(TIME_RECORDED,date)

    serializeReadings(w, gps)
    serializeReadings(w, gyro)
    serializeReadings(w, mag)
    serializeReadings(w, accel)
    serializeReadings(w, gas)
    serializeReadings(w, thermo)
    serializeReadings(w, voc)
    serializeReadings(w, anemo)
    serializeReadings(w, fire)
    serializeReadings(w, smoke)
    serializeReadings(w, images)
  }

  def serializeReadings(w: JsonWriter, rs: Seq[SentinelSensorReading]): Unit = {
    if (rs.nonEmpty) {
      val k = rs.head.readingType
      w.writeArrayMember(k) { w=> rs.foreach( _.serializeAsElementTo(w))}
    }
  }

  def updateWith (update: SentinelSensorReading): Sentinel = {
    implicit val maxReadings = MaxReadings

    if (update.deviceId != id) return this  // not for us

    update match {
      case r: SentinelGpsReading =>    copy( date= r.date, gps=     addReading(r, gps))
      case r: SentinelGyroReading =>   copy( date= r.date, gyro=    addReading(r, gyro))
      case r: SentinelMagReading =>    copy( date= r.date, mag=     addReading(r, mag))
      case r: SentinelAccelReading =>  copy( date= r.date, accel=   addReading(r, accel))
      case r: SentinelGasReading =>    copy( date= r.date, gas=     addReading(r, gas))
      case r: SentinelThermoReading => copy( date= r.date, thermo=  addReading(r, thermo))
      case r: SentinelVocReading =>    copy( date= r.date, voc=     addReading(r, voc))
      case r: SentinelAnemoReading =>  copy( date= r.date, anemo=   addReading(r, anemo))
      case r: SentinelFireReading =>   copy( date= r.date, fire=    addReading(r, fire))
      case r: SentinelSmokeReading =>  copy( date= r.date, smoke=   addReading(r, smoke))
      case r: SentinelImageReading =>  copy( date= r.date, images=  addReading(r, images))
    }
  }

  def addReading[T <: SentinelSensorReading](r: T, rs: Seq[T]) (implicit maxReadings: Int): Seq[T] = {
    var newRs = rs
    if (rs.size >= maxReadings) newRs = rs.dropRight(1)
    newRs = r +: newRs

    newRs.sortWith( (a,b) => a.date > b.date)  // should we only sort initially for caps with multiple sensors (e.g. images)?
  }
}

/**
 * this parses Sentinel records received from the Delphire server, which are of the form
 * {
 *   "data": [
 *       {
 *           "id": "dizwqq96w36j",  // was numeric
 *           "timeRecorded": ""2022-09-07T02:41:57.000Z"" // was epoch seconds,
 *           "sensorNo": 0,
 *           "deviceId": 18,
 *           "<sensor-type>": { sensor-data }
 *       }, ...
 *   ]
 * }
 */
class SentinelParser extends UTF8JsonPullParser
    with SentinelAccelParser with SentinelAnemoParser with SentinelGasParser with SentinelMagParser with SentinelThermoParser
    with SentinelFireParser with SentinelGyroParser with SentinelVocParser with SentinelGpsParser
    with SentinelSmokeParser with SentinelImageParser
    with SentinelNotificationParser with SentinelCommandParser {

  /* to debug parser exceptions:
  override def exception(msg: String): JsonParseException = {
    println("@@------------- JSON parse error")
    println(dataAsString)
    println("@@-------------")
    Thread.dumpStack()
    new JsonParseException(msg)
  }
  */

  def parseRecords(): Seq[SentinelSensorReading] = {
    val updates = ArrayBuffer.empty[SentinelSensorReading]
    var recordId: String = null
    var deviceId: String = null
    var sensorId = -1
    var timeRecorded = DateTime.UndefinedDateTime

    def appendSomeRecording (maybeReading: Option[SentinelSensorReading]): Unit = {
      ifSome(maybeReading){ r=>
        if (deviceId != null && timeRecorded.isDefined) {
          updates += r
        }
      }
    }

    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case DATA =>
        foreachElementInCurrentArray {
          recordId = null
          deviceId = null
          sensorId = -1
          timeRecorded = DateTime.UndefinedDateTime

          foreachMemberInCurrentObject {
                  // NOTE - this relies on member order in the serialization
            case ID => recordId = value.toString() // we accept both quoted and unquoted (int -> string)
            case DEVICE_ID => deviceId = value.intern // ditto
            case SENSOR_NO => sensorId = unQuotedValue.toInt
            case TIME_RECORDED => timeRecorded = dateTimeValue
            case GPS => appendSomeRecording( parseGpsValue( deviceId, sensorId, recordId, timeRecorded))
            case GAS => appendSomeRecording( parseGasValue( deviceId, sensorId, recordId, timeRecorded))
            case ACCEL => appendSomeRecording( parseAccelValue( deviceId, sensorId, recordId, timeRecorded))
            case ANEMO => appendSomeRecording( parseWindValue( deviceId, sensorId, recordId, timeRecorded))
            case GYRO => appendSomeRecording( parseGyroValue( deviceId, sensorId, recordId, timeRecorded))
            case THERMO => appendSomeRecording( parseThermoValue( deviceId, sensorId, recordId, timeRecorded))
            case MAG => appendSomeRecording( parseMagValue( deviceId, sensorId, recordId, timeRecorded))
            case FIRE => appendSomeRecording( parseFireValue( deviceId, sensorId, recordId, timeRecorded))
            case VOC => appendSomeRecording( parseVocValue( deviceId, sensorId, recordId, timeRecorded))
            case SMOKE => appendSomeRecording( parseSmokeValue(deviceId, sensorId, recordId, timeRecorded))
            case IMAGE => appendSomeRecording( parseImageValue(deviceId, sensorId, recordId, timeRecorded))

            case CLAIMS => skipPastAggregate()
            case EVIDENCE => skipPastAggregate()
            case _ => // ignore other members
          }
        }

      case _ => // ignore other members
    }

    updates.toSeq
  }


  /**
   * until device/recordIds are properly separated we have to parse this separately. The silver lining is
   * that we only expect a subset of above message format in this case
   *
   *   "data": [
   *       {
   *          "id": "dizwqq96w36j",
   *          "info": "test"
   *       }
   *    ], ...
   */
  def parseDevices(): Seq[SentinelDeviceInfo] = {
    val deviceList = ArrayBuffer.empty[SentinelDeviceInfo]

    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case DATA =>
        foreachElementInCurrentArray {
          var deviceId: String = null
          var info: String = ""

          foreachMemberInCurrentObject {
            case ID => deviceId = quotedValue.intern
            case INFO => info = quotedValue.toString()
          }
          if (deviceId != null) deviceList += SentinelDeviceInfo(deviceId,info)
        }
      case _ => // ignore other members
    }
    deviceList.toSeq
  }

  /**
   * another initialization message to get sensors for a given device - see parseDevices()
   *   {
   *      "no": 0,
   *      "partNo": "ICM20x",
   *      "camConfs": [],
   *      "capabilities": [
   *         "accelerometer",
   *         "gyroscope",
   *         "magnetometer"
   *       ]
   *   }, ...
   */
  def parseSensors(): Seq[SentinelSensorInfo] = {
    val sensorList = ArrayBuffer.empty[SentinelSensorInfo]

    ensureNextIsObjectStart()
    foreachMemberInCurrentObject {
      case DATA =>
        foreachElementInCurrentArray {
          var sensorNo: Int = -1
          var part: String = ""
          var caps = Seq.empty[CharSeqByteSlice]

          foreachMemberInCurrentObject {
            case NO => sensorNo = unQuotedValue.toInt
            case PART => part = quotedValue.toString()
            case CONFS => skipPastAggregate() // ignore for now
            case CAPS => caps = readCurrentStringArray().map( s=> ConstUtf8Slice(s))
          }
          if (sensorNo >= 0 && caps.nonEmpty) {
            val capRecs = caps.foldLeft(mutable.Map.empty[CharSeqByteSlice,Option[SentinelSensorReading]]) { (acc,c) => acc += (c -> None)}
            sensorList += SentinelSensorInfo( sensorNo, part, capRecs)
          }
        }
      case _ => // ignore other members
    }

    sensorList.toSeq
  }

}

/**
 * struct we get from a device query response
 */
case class SentinelDeviceInfo (deviceId: String, info: String)

/**
 * struct we populate from a sensor query response and use to keep track of last received sensor records
 * capabilities is a map sensorType -> last reading
 */
case class SentinelSensorInfo (sensorNo: Int, partNo: String, capabilities: mutable.Map[CharSeqByteSlice,Option[SentinelSensorReading]])
