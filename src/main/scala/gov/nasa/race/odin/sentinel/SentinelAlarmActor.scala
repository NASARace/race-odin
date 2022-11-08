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

import com.typesafe.config.Config
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.{BusEvent, ContinuousTimeRaceActor, PublishingRaceActor, SubscribingRaceActor}
import gov.nasa.race.odin.sentinel.SentinelSensorReading.DefaultImageDir
import gov.nasa.race.smtp.SmtpActor
import gov.nasa.race.uom.{DateTime, Time}
import gov.nasa.race.uom.Time.Minutes
import gov.nasa.race.util.FileUtils

import java.io.File
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

class SentinelAlarmActor (val config: Config) extends SubscribingRaceActor with SmtpActor with ContinuousTimeRaceActor {

  class AlarmStatus (
                    val deviceId: String,
                    val sensors: mutable.Buffer[Int],
                    var notified: DateTime = DateTime.UndefinedDateTime
                    )

  val alarmStatus: Map[String,AlarmStatus] = Map.empty
  var sentinels: Map[String,Sentinel] = Map.empty

  val senderAddress = config.getVaultableString("sender")
  val recipients = config.getStringSeq("recipients")
  val smokeThreshold = config.getDoubleOrElse("smoke-threshold", 0.5)
  val fireThreshold = config.getDoubleOrElse("fire-threshold", 0.5)
  val maxAge = config.getDurationTimeOrElse("max-age", Minutes(5))  // alarm to current time
  val alarmDelay = config.getFiniteDurationOrElse("alarm-delay", 5.seconds)

  val sendImages = config.getBooleanOrElse("send-images", false)
  val imageDir = FileUtils.ensureDir(config.getStringOrElse("image-dir", DefaultImageDir)).get
  val maxImageAge = config.getDurationTimeOrElse("image-age", Minutes(2)) // image update to fire/smoke report


  override def handleMessage: Receive = {
    case BusEvent(_,SentinelSet(updatedSentinels),_) => sentinels = updatedSentinels
    case BusEvent (_,ssrs: SentinelUpdates,_) => processSensorReadings(ssrs)
  }

  def processSensorReadings (ssrs: SentinelUpdates): Unit = {
    ssrs.readings.foreach {
      case r:SentinelFireReading => checkFireAlarm(r)
      case r:SentinelSmokeReading => checkSmokeAlarm(r)
      case _ => // we ignore the rest (for now)
    }
  }

  def checkFireAlarm (ssr: SentinelFireReading): Unit = {
    sentinels.get(ssr.deviceId) match {
      case Some(sentinel) =>
        if (ssr.prob >= fireThreshold && currentSimTime.timeSince(ssr.date) < maxAge) {
          triggerAlarm( ssr, "fire alarm", alarmMessage(sentinel.name, ssr.date, ssr.sensorNo, ssr.prob))
        }
      case None => warning(s"ignoring fire sensor reading from unknown device ${ssr.deviceId}")
    }
  }

  def checkSmokeAlarm (ssr: SentinelSmokeReading): Unit = {
    sentinels.get(ssr.deviceId) match {
      case Some(sentinel) =>
        if (ssr.prob >= smokeThreshold && currentSimTime.timeSince(ssr.date) < maxAge) {
          triggerAlarm( ssr, "smoke alarm", alarmMessage(sentinel.name, ssr.date, ssr.sensorNo, ssr.prob))
        }
      case None => warning(s"ignoring smoke sensor reading from unknown device ${ssr.deviceId}")
    }
  }

  def triggerAlarm (ssr: SentinelSensorReading, alarmType: String, alarmMessage: String): Unit = {
    if (sendImages) {
      getLastImageReading(ssr) match {
        case Some(file) =>
          sendEmail(senderAddress, Nil, Nil, recipients, alarmType, Seq(
            textPart(alarmMessage),
            inlinedFileAttachmentPart(file)
          ))
        case None => {
          sendEmail(senderAddress, Nil, Nil, recipients, alarmType, alarmMessage)
        } // we don't have an image
      }
    } else { // not configured to send out images
      sendEmail(senderAddress, Nil, Nil, recipients, alarmType, alarmMessage)
    }
  }

  def triggerTextAlarm(alarmType: String, alarmMessage: String): Unit = {
    info(s"triggering: $alarmType\n$alarmMessage")
    sendEmail(senderAddress, Nil, Nil, recipients, alarmType, alarmMessage)
  }

  def alarmMessage (deviceName: String, date: DateTime, sensorNo: Int, probability: Double): String = {
    s"device: $deviceName\ndate: ${date.formatLocal_yMd_Hms}\nsensor: $sensorNo\nprobability: ${(probability * 100).round}%"
  }

  // TODO - this just uses the last image from the respective device. We should base this on the 'evidences' of the trigger
  def getLastImageReading (ssr: SentinelSensorReading): Option[File] = {
    for {
      sentinel <- sentinels.get(ssr.deviceId);
      ssr <- sentinel.images.find( r => ssr.date.timeSince(r.date) < maxAge);
      file <- FileUtils.existingNonEmptyFile(new File(imageDir, ssr.fileName))
    } yield file
  }
}
