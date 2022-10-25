package gov.nasa.race.odin.sentinel

import com.typesafe.config.Config
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.{BusEvent, ContinuousTimeRaceActor, SubscribingRaceActor}
import gov.nasa.race.smtp.SmtpActor
import gov.nasa.race.uom.DateTime
import gov.nasa.race.uom.Time.Minutes

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
  val maxAge = Minutes( config.getFiniteDurationOrElse("max-age", 30.minutes).toMinutes)
  val alarmDelay = config.getFiniteDurationOrElse("alarm-delay", 5.seconds)

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
          triggerAlarm( "fire alarm", alarmMessage(sentinel.name, ssr.date, ssr.sensorNo, ssr.prob))
        }
      case None => warning(s"ignoring fire sensor reading from unknown device ${ssr.deviceId}")
    }
  }

  def checkSmokeAlarm (ssr: SentinelSmokeReading): Unit = {
    sentinels.get(ssr.deviceId) match {
      case Some(sentinel) =>
        if (ssr.prob >= smokeThreshold && currentSimTime.timeSince(ssr.date) < maxAge) {
          triggerAlarm( "smoke alarm", alarmMessage(sentinel.name, ssr.date, ssr.sensorNo, ssr.prob))
        }
      case None => warning(s"ignoring smoke sensor reading from unknown device ${ssr.deviceId}")
    }
  }

  def alarmMessage (deviceName: String, date: DateTime, sensorNo: Int, probability: Double): String = {
    s"device: $deviceName\ndate: ${date.formatLocal_yMd_Hms}\nsensor: $sensorNo\nprobability: ${(probability * 100).round}%"
  }

  def triggerAlarm (alarmType: String, alarmMessage: String): Unit = {
    info(s"triggering: $alarmType\n$alarmMessage")
    sendEmail(senderAddress, Nil, Nil, recipients, alarmType, alarmMessage)
  }
}
