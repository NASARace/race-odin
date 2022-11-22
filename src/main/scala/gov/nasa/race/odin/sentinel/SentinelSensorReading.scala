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

import akka.http.scaladsl.model.ContentType
import gov.nasa.race.Dated
import gov.nasa.race.common.ConstAsciiSlice.asc
import gov.nasa.race.common.{CharSeqByteSlice, JsonSerializable, JsonWriter, UTF8JsonPullParser}
import gov.nasa.race.odin.sentinel.Sentinel.{DEVICE_ID, SENSOR_NO}
import gov.nasa.race.uom.Angle.Degrees
import gov.nasa.race.uom.Speed.MetersPerSecond
import gov.nasa.race.uom.Temperature.{Celsius, UndefinedTemperature}
import gov.nasa.race.uom.{Angle, DateTime, Speed, Temperature}
import gov.nasa.race.util.FileUtils

object SentinelSensorReading {

  //--- lexical constants
  val READING = asc("sentinelReading")
  val UPDATES = asc("sentinelUpdates")
  val DEVICE_NAME = asc("deviceName")
  val RECORD_ID = asc("recordId")
  val ID = asc("id") // TODO - this should be recordId
  val TIME_RECORDED = asc("timeRecorded")
  val GPS = asc("gps"); val LAT = asc("latitude"); val LON = asc("longitude")
  val MAG = asc("magnetometer"); val MX = asc("mx"); val MY = asc("my"); val MZ = asc("mz")
  val GYRO = asc("gyroscope"); val GX = asc("gx"); val GY = asc("gy"); val GZ = asc("gz")
  val ACCEL = asc("accelerometer"); val AX = asc("ax"); val AY = asc("ay"); val AZ = asc("az")
  val GAS = asc("gas"); val HUM = asc("humidity"); val PRESS = asc("pressure"); val ALT = asc("altitude")
  val THERMO = asc("thermometer"); val TEMP = asc("temperature")
  val VOC = asc("voc"); val TVOC = asc("TVOC"); val ECO2 = asc("eCO2")
  val ANEMO = asc("anemometer"); val ANGLE = asc("angle"); val SPD = asc("speed")
  val FIRE = asc("fire"); val PROB = asc("fireProb")
  val IMAGE = asc("image"); val FILENAME = asc("filename"); val IS_INFRARED = asc("isInfrared"); val CONF_NO = asc("confNo")
  val SMOKE = asc("smoke"); val SMOKE_PROB = asc("smokeProb")

  val IMAGE_PREFIX = "image"
  val DefaultImageDir = s"tmp/delphire/$IMAGE_PREFIX"
}
import SentinelSensorReading._

/**
 * root type for Sentinel update events
 *
 * note that JSON serialization/deserialization is using RACEs own mechanisms (JsonWriter and JsonPullParser),
 * which requires more code than to use generic 3rd party libraries such as upickle but (a) gives more freedom
 * reg. mapping JSON to objects and (b) allows to use RACEs ByteSlice types to avoid short term memory allocation
 * and heap pressure in the context of how volume input streams
 */
trait SentinelSensorReading extends Dated with JsonSerializable {
  val deviceId: String
  val sensorNo: Int
  val recordId: String

  def readingType: CharSeqByteSlice

  // aggregate serializer (not including deviceId)
  def serializeDataTo (w: JsonWriter): Unit

  def serializeReading (w: JsonWriter): Unit = {
    w.writeStringMember(ID,recordId)
      .writeStringMember(DEVICE_ID, deviceId)
      .writeIntMember(SENSOR_NO,sensorNo)
      .writeDateTimeMember(TIME_RECORDED, date)
      .writeObjectMember(readingType){ serializeDataTo }
  }

  def serializeAsMemberTo (w: JsonWriter): Unit = w.writeObjectMember(readingType){ serializeReading }

  def serializeAsElementTo (w: JsonWriter): Unit = w.writeObject { serializeReading }

  // stand-alone serializer (e.g. for update events)
  def serializeMembersTo (w: JsonWriter): Unit = {
    w.writeStringMember(DEVICE_ID,deviceId)
    serializeDataTo(w)
  }

  def serializeReadingMessageTo(w: JsonWriter): Unit = {
    w.writeObject( _.writeObjectMember(READING)(serializeReading))
  }

  def copyWithDate(newDate: DateTime): SentinelSensorReading
}

/**
 * match-able type we can use to batch a number of SentinelSensorReadings
 */
case class SentinelUpdates (readings: Seq[SentinelSensorReading]) extends JsonSerializable {
  def serializeMembersTo (w: JsonWriter): Unit = {
    w.writeArrayMember(UPDATES){ w=>
      readings.foreach( r=> r.serializeReadingMessageTo(w))
    }
  }
}

//--- the sentinel sensor reading types

case class SentinelGpsReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, lat: Angle, lon: Angle) extends SentinelSensorReading {
  def readingType = GPS

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(LAT, lat.toDegrees)
    w.writeDoubleMember(LON, lon.toDegrees)
  }

  def copyWithDate(newDate: DateTime): SentinelGpsReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
            {
                "latitude": 37.328366705311865,
                "longitude": -122.10084539864475,
                "recordId": 1065930
            },
 */
trait SentinelGpsParser extends UTF8JsonPullParser {
  def parseGpsValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelGpsReading] = {
    var lat = Angle.UndefinedAngle
    var lon = Angle.UndefinedAngle
    if (isInObject) {
      foreachMemberInCurrentObject {
        case LAT => lat = Degrees(unQuotedValue.toDouble).toNormalizedLatitude
        case LON => lon = Degrees(unQuotedValue.toDouble).toNormalizedLongitude
        case _ => // ignore other members
      }
      Some(SentinelGpsReading(deviceId,sensorNo,recordId,date,lat,lon))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelGyroReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, gx: Double, gy: Double, gz: Double) extends SentinelSensorReading {
  def readingType = GYRO

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(GX, gx)
    w.writeDoubleMember(GY, gy)
    w.writeDoubleMember(GZ, gz)
  }

  def copyWithDate(newDate: DateTime): SentinelGyroReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
     {
      "gx": 9.886104239500455,
      "gy": -0.019948077582174595,
      "gz": 0.01958188436975299,
      ...
     }
 */
trait SentinelGyroParser extends UTF8JsonPullParser {
  def parseGyroValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelGyroReading] = {
    var gx: Double = 0
    var gy: Double = 0
    var gz: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case GX => gx = unQuotedValue.toDouble
        case GY => gy = unQuotedValue.toDouble
        case GZ => gz = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelGyroReading(deviceId,sensorNo,recordId,date,gx,gy,gz))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelMagReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, mx: Double, my: Double, mz: Double) extends SentinelSensorReading {
  def readingType = MAG

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(MX, mx)
    w.writeDoubleMember(MY, my)
    w.writeDoubleMember(MZ, mz)
  }

  def copyWithDate(newDate: DateTime): SentinelMagReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
     {
      "mx": 9.886104239500455,
      "my": -0.019948077582174595,
      "mz": 0.01958188436975299,
      ...
     }
 */
trait SentinelMagParser extends UTF8JsonPullParser {
  def parseMagValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelMagReading] = {
    var mx: Double = 0
    var my: Double = 0
    var mz: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case MX => mx = unQuotedValue.toDouble
        case MY => my = unQuotedValue.toDouble
        case MZ => mz = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelMagReading(deviceId,sensorNo,recordId,date,mx,my,mz))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelAccelReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, ax: Double, ay: Double, az: Double) extends SentinelSensorReading {
  def readingType = ACCEL

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(AX, ax)
    w.writeDoubleMember(AY, ay)
    w.writeDoubleMember(AZ, az)
  }

  def copyWithDate(newDate: DateTime): SentinelAccelReading = copy(date = newDate)
}
/**
 * parses value 'null' or:
     {
      "ax": 9.886104239500455,
      "ay": -0.019948077582174595,
      "az": 0.01958188436975299,
      ...
     }
 */
trait SentinelAccelParser extends UTF8JsonPullParser {
  def parseAccelValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelAccelReading] = {
    var ax: Double = 0
    var ay: Double = 0
    var az: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case AX => ax = unQuotedValue.toDouble
        case AY => ay = unQuotedValue.toDouble
        case AZ => az = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelAccelReading(deviceId,sensorNo,recordId,date,ax,ay,az))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelGasReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, gas: Long, humidity: Double, pressure: Double, alt: Double) extends SentinelSensorReading {
  def readingType = GAS

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeLongMember(GAS, gas)
    w.writeDoubleMember(HUM, humidity)
    w.writeDoubleMember(PRESS, pressure)
    w.writeDoubleMember(ALT, alt)
  }

  def copyWithDate(newDate: DateTime): SentinelGasReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
            "gas": {
                "gas": 100502,
                "humidity": 41.83471927704546,
                "pressure": 985.3858389187525,
                "altitude": 271.6421960490708,
                "recordId": 1065919
            },
 */
trait SentinelGasParser extends UTF8JsonPullParser {
  def parseGasValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelGasReading] = {
    var gas: Long = 0
    var humidity: Double = 0
    var pressure: Double = 0
    var altitude: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case GAS => gas = unQuotedValue.toLong
        case HUM => humidity = unQuotedValue.toDouble
        case PRESS => pressure = unQuotedValue.toDouble
        case ALT => altitude = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelGasReading(deviceId,sensorNo,recordId,date,gas,humidity,pressure,altitude))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelThermoReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, temp: Temperature) extends SentinelSensorReading {
  def readingType = THERMO

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(TEMP, temp.toCelsius)
  }

  def copyWithDate(newDate: DateTime): SentinelThermoReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
            {
                "temperature": 25.574097844252393,
                "recordId": 1065922
            },
 */
trait SentinelThermoParser extends UTF8JsonPullParser {
  def parseThermoValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelThermoReading] = {
    var temp = UndefinedTemperature
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TEMP => temp = Celsius(unQuotedValue.toDouble)
        case _ => // ignore other members
      }
      Some(SentinelThermoReading(deviceId,sensorNo,recordId,date,temp))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelVocReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, tvoc: Int, eco2: Int) extends SentinelSensorReading {
  def readingType = VOC

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(TVOC, tvoc)
    w.writeIntMember(ECO2, eco2)
  }

  def copyWithDate(newDate: DateTime): SentinelVocReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
            {
                "TVOC": 0,
                "eCO2": 400,
                "recordId": 1065927
            },
 */
trait SentinelVocParser extends UTF8JsonPullParser {
  def parseVocValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelVocReading] = {
    var tvoc: Int = 0
    var eco2: Int = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TVOC => tvoc = unQuotedValue.toInt
        case ECO2 => eco2 = unQuotedValue.toInt
        case _ => // ignore other members
      }
      Some(SentinelVocReading(deviceId,sensorNo,recordId,date,tvoc,eco2))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelAnemoReading(deviceId: String, sensorNo: Int, recordId: String, date: DateTime, dir: Angle, spd: Speed) extends SentinelSensorReading {
  def readingType = ANEMO

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(ANGLE, dir.toDegrees)
    w.writeDoubleMember(SPD, spd.toMetersPerSecond)
  }

  def copyWithDate(newDate: DateTime): SentinelAnemoReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
             {
                "angle": 324.2628274722738,
                "speed": 0.04973375738796704,
                "recordId": 1065928
            },
 */
trait SentinelAnemoParser extends UTF8JsonPullParser {
  def parseWindValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelAnemoReading] = {
    var angle = Angle.UndefinedAngle
    var speed = Speed.UndefinedSpeed
    if (isInObject) {
      foreachMemberInCurrentObject {
        case ANGLE => angle = Degrees(unQuotedValue.toDouble)
        case SPD => speed = MetersPerSecond(unQuotedValue.toDouble)
        case _ => // ignore other members
      }
      Some(SentinelAnemoReading(deviceId,sensorNo,recordId,date,angle,speed))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelFireReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, prob: Double) extends SentinelSensorReading {
  def readingType = FIRE

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(PROB, prob)
  }

  def copyWithDate(newDate: DateTime): SentinelFireReading = copy(date = newDate)
}

/**
 * parses value 'null' or:
             {
                "fireProb": 0.9689663083869459,
                "recordId": 1065946
            },
 */
trait SentinelFireParser extends UTF8JsonPullParser {
  def parseFireValue(deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelFireReading] = {
    var prob: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case PROB => prob = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelFireReading(deviceId,sensorNo,recordId,date,prob))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

/**
 * event type we use to publish image data received from the sentinel server
 * note these can get quite large so data should be stored in files
 */
case class SentinelImage (cameraReading: SentinelImageReading, data: Array[Byte], contentType: ContentType)


//--- new record formats (TODO - we need to update serialization)

/**
 * new "smoke" record
 */
case class SentinelSmokeReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, prob: Double) extends SentinelSensorReading {
  def readingType = SMOKE

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeDoubleMember(SMOKE_PROB,prob)
  }

  def copyWithDate(newDate: DateTime): SentinelSmokeReading = copy(date = newDate)
}

/**
 *  "smoke":{"smokeProb":0.07105258107185364}
 */
trait SentinelSmokeParser extends UTF8JsonPullParser {
  def parseSmokeValue (deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelSmokeReading] = {
    var prob: Double = Double.NaN
    if (isInObject) {
      foreachMemberInCurrentObject {
        case SMOKE_PROB => prob = unQuotedValue.toDouble
        case _ => // ignore
      }
      if (prob.isNaN) None else Some(SentinelSmokeReading(deviceId,sensorNo,recordId,date,prob))
    } else if (isNull) None
    else throw exception("expected smoke object value")
  }
}

/**
 * new "image" record
 */
case class SentinelImageReading (deviceId: String, sensorNo: Int, recordId: String, date: DateTime, fileName: String, isInfrared: Boolean) extends SentinelSensorReading {
  def readingType = IMAGE

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeStringMember(FILENAME, s"$IMAGE_PREFIX/$fileName")
    w.writeBooleanMember(IS_INFRARED,isInfrared)
  }

  def copyWithDate(newDate: DateTime): SentinelImageReading = copy(date = newDate)
}

/**
 * "image":{
 *   "filename": "./__image/e3bd4676-9d97-417b-9c6ec7295a96e470.webp",
 *   "isInfrared": true,
 *   "confNo": null
 *  }
 */
trait SentinelImageParser extends UTF8JsonPullParser {
  def parseImageValue (deviceId: String, sensorNo: Int, recordId: String, date: DateTime): Option[SentinelImageReading] = {
    var fileName: String = null
    var isInfrared = false

    if (isInObject) {
      foreachMemberInCurrentObject {
        case FILENAME => fileName = FileUtils.filename(quotedValue.toString()) // we remove the path portion
        case IS_INFRARED => isInfrared = unQuotedValue.toBoolean
        case _ => // confNo ?
      }
      if (fileName != null) Some(SentinelImageReading(deviceId, sensorNo, recordId,date, fileName, isInfrared)) else None
    } else if (isNull) None
    else throw exception("expected image object value")
  }
}