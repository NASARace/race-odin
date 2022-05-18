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
import com.typesafe.config.Config
import gov.nasa.race.common.{JsonSerializable, JsonWriter}
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.{BusEvent, PublishingRaceActor, RaceContext, SubscribingRaceActor}
import gov.nasa.race.uom.DateTime
import gov.nasa.race.util.FileUtils

import java.io.File
import scala.collection.mutable
import scala.util.Sorting

// an immutable match type we can use in messages
case class SentinelSet (sentinels: Map[Int,Sentinel]) extends JsonSerializable {
  def serializeMembersTo (w: JsonWriter): Unit = {
    w.writeArrayMember("sentinels"){ w=>
      sentinels.keys.toSeq.sortWith( (a,b)=> a > b).foreach { sentinelId=>
        sentinels(sentinelId).serializeTo(w)
      }
    }
  }
}

/**
 * actor that create a map of Sentinel objects and updates them from SentinelSensorReading messages
 *
 * note that our map of Sentinel objects is immutable to allow sending snapshots
 */
class SentinelUpdateActor (val config: Config) extends SubscribingRaceActor with PublishingRaceActor {

  val sentinelDir = config.getString("sentinel-dir")
  val storeSentinels = config.getBooleanOrElse("store-sentinels", false)

  var sentinels: Map[Int,Sentinel] = Map.empty
  val updatedSentinelIds: mutable.Set[Int] = mutable.Set.empty

  def sentinelStore: File = new File(sentinelDir, "sentinels.json")

  def updatedSentinels: Seq[Sentinel] = {
    updatedSentinelIds.toSeq.map(sentinels)
  }

  override def onInitializeRaceActor(ctx: RaceContext, actorConf: Config): Boolean = {
    super.onInitializeRaceActor(ctx,actorConf) && {
      val f = sentinelStore
      if (f.isFile) readSentinels(f)
      true
    }
  }

  override def onStartRaceActor(originator: ActorRef): Boolean = {
    super.onStartRaceActor(originator) && {
      publish( SentinelSet(sentinels))
      true
    }
  }

  override def onTerminateRaceActor(originator: ActorRef): Boolean = {
    if (storeSentinels) saveSentinels(sentinelStore)
    super.onTerminateRaceActor(originator)
  }

  override def handleMessage: Receive = {
    case BusEvent (_,ssr: SentinelUpdates,_) => processSensorReadings(ssr)
  }

  def processSensorReadings(ssr: SentinelUpdates): Unit = {
    updatedSentinelIds.clear()
    ssr.readings.foreach(update)

    //updatedSentinels.foreach(publish)
    publish( SentinelSet(sentinels))
    publish( ssr) // we re-publish on our output channel so that subscribers always get the complete map before the changes
  }

  def update (sensorReading: SentinelSensorReading): Unit = {
    val deviceId = sensorReading.deviceId
    val updatedSentinel = sentinels.getOrElse(deviceId, new Sentinel(deviceId)).updateWith(sensorReading)
    sentinels = sentinels + (deviceId -> updatedSentinel)
    updatedSentinelIds.add(deviceId)
  }

  def saveSentinels (file: File): Unit = {
    val w = new JsonWriter()
    w.format(true)
    w.readableDateTime(true)

    w.writeObject{ w=>
      w.writeArrayMember("sentinels"){ w=>
        val deviceIds = sentinels.keys.toArray
        Sorting.quickSort(deviceIds)
        deviceIds.foreach { sentinelId=>
          sentinels(sentinelId).serializeTo(w)
        }
      }
    }
    FileUtils.setFileContents( file, w.toJson)
  }

  def readSentinels (file: File): Unit = {
    val parser = new SentinelParser

    FileUtils.fileContentsAsBytes(file) match {
      case Some(data) =>
        if (parser.initialize(data)){
          sentinels = parser.parse().foldLeft( Map.empty[Int,Sentinel]) { (acc, r) =>
            val deviceId = r.deviceId
            val s = acc.getOrElse(deviceId, new Sentinel(deviceId)).updateWith(r)
            acc + (deviceId -> s)
          }
        }
      case None => // report error
    }
  }
}
