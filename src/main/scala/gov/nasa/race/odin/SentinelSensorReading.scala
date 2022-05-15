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
package gov.nasa.race.odin

import akka.http.scaladsl.model.ContentType
import gov.nasa.race.Dated
import gov.nasa.race.common.ConstAsciiSlice.asc
import gov.nasa.race.common.{JsonSerializable, JsonWriter, UTF8JsonPullParser}
import gov.nasa.race.uom.Angle.Degrees
import gov.nasa.race.uom.Speed.MetersPerSecond
import gov.nasa.race.uom.Temperature.{Celsius, UndefinedTemperature}
import gov.nasa.race.uom.{Angle, DateTime, Speed, Temperature}

object SentinelSensorReading {

  //--- lexical constants
  val READING = asc("sentinelReading")
  val UPDATES = asc("sentinelUpdates")
  val DEVICE_ID = asc("deviceId")
  val RECORD_ID = asc("recordId")
  val SENSOR_NO = asc("sensorNo")
  val TIME_RECORDED = asc("timeRecorded")
  val GPS = asc("gps"); val LAT = asc("latitude"); val LON = asc("longitude")
  val MAG = asc("magnetometer"); val MX = asc("mx"); val MY = asc("my"); val MZ = asc("mz")
  val GYRO = asc("gyroscope"); val GX = asc("gx"); val GY = asc("gy"); val GZ = asc("gz")
  val ACCEL = asc("accelerometer"); val AX = asc("ax"); val AY = asc("ay"); val AZ = asc("az")
  val GAS = asc("gas"); val HUM = asc("hummidity"); val PRESS = asc("pressure"); val ALT = asc("altitude")
  val THERMO = asc("thermometer"); val TEMP = asc("temperature")
  val VOC = asc("voc"); val TVOC = asc("TVOC"); val ECO2 = asc("eCO2")
  val ANEMO = asc("anemometer"); val ANGLE = asc("angle"); val SPD = asc("speed")
  val FIRE = asc("fire"); val PROB = asc("fireProb")
  val CAMERA = asc("camera"); val IR = asc("ir"); val PATH = asc("filename")
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
  val deviceId: Int
  val sensorNo: Int

  def readingType: CharSequence

  // aggregate serializer (not including deviceId)
  def serializeDataTo (w: JsonWriter): Unit

  def serializeAsMemberTo (w: JsonWriter): Unit = {
    w.writeObjectMember(readingType){ serializeDataTo }
  }

  // stand-alone serializer (e.g. for update events)
  def serializeMembersTo (w: JsonWriter): Unit = {
    w.writeIntMember(DEVICE_ID,deviceId)
    serializeDataTo(w)
  }

  def serializeReadingTo (w: JsonWriter): Unit = {
    w.writeObject { _
      .writeObjectMember(READING) { _
        .writeIntMember(DEVICE_ID, deviceId)
        .writeObjectMember(readingType){ serializeDataTo }
      }
    }
  }
}

/**
 * match-able type we can use to batch a number of SentinelSensorReadings
 */
case class SentinelUpdates (readings: Seq[SentinelSensorReading]) extends JsonSerializable {
  def serializeMembersTo (w: JsonWriter): Unit = {
    w.writeArrayMember(UPDATES){ w=>
      readings.foreach( r=> r.serializeReadingTo(w))
    }
  }
}

//--- the sentinel sensor reading types

case class SentinelGpsReading (deviceId: Int, sensorNo: Int, date: DateTime, lat: Angle, lon: Angle) extends SentinelSensorReading {
  def readingType = GPS

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(LAT, lat.toDegrees)
    w.writeDoubleMember(LON, lon.toDegrees)
  }
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
  def parseGpsValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelGpsReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var lat = Angle.UndefinedAngle
    var lon = Angle.UndefinedAngle
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case LAT => lat = Degrees(unQuotedValue.toDouble)
        case LON => lon = Degrees(unQuotedValue.toDouble)
        case _ => // ignore other members
      }
      Some(SentinelGpsReading(deviceId,sensorNo,date,lat,lon))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelGyroReading (deviceId: Int, sensorNo: Int, date: DateTime, gx: Double, gy: Double, gz: Double) extends SentinelSensorReading {
  def readingType = GYRO

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(GX, gx)
    w.writeDoubleMember(GY, gy)
    w.writeDoubleMember(GZ, gz)
  }
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
  def parseGyroValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelGyroReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var gx: Double = 0
    var gy: Double = 0
    var gz: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case GX => gx = unQuotedValue.toDouble
        case GY => gy = unQuotedValue.toDouble
        case GZ => gz = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelGyroReading(deviceId,sensorNo,date,gx,gy,gz))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelMagReading (deviceId: Int, sensorNo: Int, date: DateTime, mx: Double, my: Double, mz: Double) extends SentinelSensorReading {
  def readingType = MAG

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(MX, mx)
    w.writeDoubleMember(MY, my)
    w.writeDoubleMember(MZ, mz)
  }
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
  def parseMagValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelMagReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var mx: Double = 0
    var my: Double = 0
    var mz: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case MX => mx = unQuotedValue.toDouble
        case MY => my = unQuotedValue.toDouble
        case MZ => mz = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelMagReading(deviceId,sensorNo,date,mx,my,mz))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelAccelReading (deviceId: Int, sensorNo: Int, date: DateTime, ax: Double, ay: Double, az: Double) extends SentinelSensorReading {
  def readingType = ACCEL

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(AX, ax)
    w.writeDoubleMember(AY, ay)
    w.writeDoubleMember(AZ, az)
  }
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
  def parseAccelValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelAccelReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var ax: Double = 0
    var ay: Double = 0
    var az: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case AX => ax = unQuotedValue.toDouble
        case AY => ay = unQuotedValue.toDouble
        case AZ => az = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelAccelReading(deviceId,sensorNo,date,ax,ay,az))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelGasReading (deviceId: Int, sensorNo: Int, date: DateTime, gas: Long, humidity: Double, pressure: Double, alt: Double) extends SentinelSensorReading {
  def readingType = GAS

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeLongMember(GAS, gas)
    w.writeDoubleMember(HUM, humidity)
    w.writeDoubleMember(PRESS, pressure)
    w.writeDoubleMember(ALT, alt)
  }
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
  def parseGasValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelGasReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var gas: Long = 0
    var humidity: Double = 0
    var pressure: Double = 0
    var altitude: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case GAS => gas = unQuotedValue.toLong
        case HUM => humidity = unQuotedValue.toDouble
        case PRESS => pressure = unQuotedValue.toDouble
        case ALT => altitude = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelGasReading(deviceId,sensorNo,date,gas,humidity,pressure,altitude))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelThermoReading (deviceId: Int, sensorNo: Int, date: DateTime, temp: Temperature) extends SentinelSensorReading {
  def readingType = THERMO

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(TEMP, temp.toCelsius)
  }
}

/**
 * parses value 'null' or:
            {
                "temperature": 25.574097844252393,
                "recordId": 1065922
            },
 */
trait SentinelThermoParser extends UTF8JsonPullParser {
  def parseThermoValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelThermoReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var temp = UndefinedTemperature
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case TEMP => temp = Celsius(unQuotedValue.toDouble)
        case _ => // ignore other members
      }
      Some(SentinelThermoReading(deviceId,sensorNo,date,temp))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelVocReading (deviceId: Int, sensorNo: Int, date: DateTime, tvoc: Int, eco2: Int) extends SentinelSensorReading {
  def readingType = VOC

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeIntMember(TVOC, tvoc)
    w.writeIntMember(ECO2, eco2)
  }
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
  def parseVocValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelVocReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var tvoc: Int = 0
    var eco2: Int = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case TEMP => tvoc = unQuotedValue.toInt
        case ECO2 => eco2 = unQuotedValue.toInt
        case _ => // ignore other members
      }
      Some(SentinelVocReading(deviceId,sensorNo,date,tvoc,eco2))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelAnemoReading(deviceId: Int, sensorNo: Int, date: DateTime, dir: Angle, spd: Speed) extends SentinelSensorReading {
  def readingType = ANEMO

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(ANGLE, dir.toDegrees)
    w.writeDoubleMember(SPD, spd.toMetersPerSecond)
  }
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
  def parseWindValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelAnemoReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var angle = Angle.UndefinedAngle
    var speed = Speed.UndefinedSpeed
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case ANGLE => angle = Degrees(unQuotedValue.toDouble)
        case SPD => speed = MetersPerSecond(unQuotedValue.toDouble)
        case _ => // ignore other members
      }
      Some(SentinelAnemoReading(deviceId,sensorNo,date,angle,speed))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelFireReading (deviceId: Int, sensorNo: Int, date: DateTime, prob: Double) extends SentinelSensorReading {
  def readingType = FIRE

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeDoubleMember(PROB, prob)
  }
}

/**
 * parses value 'null' or:
             {
                "fireProb": 0.9689663083869459,
                "recordId": 1065946
            },
 */
trait SentinelFireParser extends UTF8JsonPullParser {
  def parseFireValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelFireReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var prob: Double = 0
    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case PROB => prob = unQuotedValue.toDouble
        case _ => // ignore other members
      }
      Some(SentinelFireReading(deviceId,sensorNo,date,prob))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

case class SentinelCameraReading (deviceId: Int, sensorNo: Int, date: DateTime, isIR: Boolean, path: String, recordId: Long) extends SentinelSensorReading {
  def readingType = CAMERA

  def serializeDataTo(w: JsonWriter): Unit = {
    w.writeIntMember(SENSOR_NO,sensorNo)
    w.writeDateTimeMember(TIME_RECORDED, date)
    w.writeBooleanMember(IR,isIR)
    w.writeStringMember(PATH,path)
    w.writeLongMember(RECORD_ID,recordId)
  }
}

/**
 * parses value 'null' or:
           {
                "filename": "./camera/4c292b0c-a270-4015-a9f6-006fc8641f73.webp",
                "isInfrared": true,
                "recordId": 1065944
            }
 */
trait SentinelCameraParser extends UTF8JsonPullParser {
  def parseCameraValue(deviceId: Int, defaultSensor: Int, defaultDate: DateTime): Option[SentinelCameraReading] = {
    var date = defaultDate
    var sensorNo = defaultSensor
    var isIR: Boolean = false
    var filename: String = null
    var recordId: Long = -1

    if (isInObject) {
      foreachMemberInCurrentObject {
        case TIME_RECORDED => date = dateTimeValue
        case SENSOR_NO => sensorNo = unQuotedValue.toInt
        case IR => isIR = unQuotedValue.toBoolean
        case PATH => filename = quotedValue.asString
        case RECORD_ID => recordId = unQuotedValue.toLong
        case _ => // ignore other members
      }
      filename = s"camera/$recordId.webp"  // FIXME - until we retrieve them from the sentinel server we just use the recordId
      Some(SentinelCameraReading(deviceId,sensorNo,date,isIR,filename,recordId))
    } else if (isNull) None
    else throw exception("expected object value")
  }
}

/**
 * event type we use to publish image data received from the sentinel server
 * note these can get quite large so data should be stored in files
 */
case class SentinelImage (cameraReading: SentinelCameraReading, data: Array[Byte], contentType: ContentType)