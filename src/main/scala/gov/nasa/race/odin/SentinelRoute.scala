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

import akka.actor.Actor.Receive
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.SourceQueueWithComplete
import gov.nasa.race.common.ConstAsciiSlice.asc
import gov.nasa.race.common.JsonWriter
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.BusEvent
import gov.nasa.race.http.{CachedFileAssetMap, HttpServer, PushWSRaceRoute, ResponseData, WSContext}
import gov.nasa.race.cesium.CesiumRoute
import gov.nasa.race.ifSome
import gov.nasa.race.ui.{extModule, uiButton, uiField, uiFieldGroup, uiIcon, uiList, uiNumField, uiPanel, uiRowContainer, uiWindow}
import gov.nasa.race.util.FileUtils
import scalatags.Text

import java.net.InetSocketAddress
import java.io.File

object SentinelRoute {
  val SENTINEL = asc("sentinel")
}
import SentinelRoute._

/**
 * this is a RaceRoute that serves content reg. Sentinel devices
 * Use this trait as a mixin for single-page application routes that include a Sentinel micro service, which entails
 * additions to the accumulated
 *
 *   - route  (assets used by this service)
 *   - app config (generated Javascript)
 *   - document content (generated HTML)
 *   - websocket handlers
 *
 */
trait SentinelRoute extends  CesiumRoute with PushWSRaceRoute {

  private val writer = new JsonWriter()

  var sentinels: SentinelSet = SentinelSet(Map.empty[Int,Sentinel])
  var sentinelsMsg: Option[TextMessage.Strict] = None

  val sentinelDir = config.getString("sentinel-dir")
  val sentinelAssets = Map.from(
    config.getKeyValuePairsOrElse("sentinel-assets",
      Seq(("sentinel","sentinel-sym.png"), ("fire","fire.png"))
    )
  )

  def getSentinelAssetContent (key: String): Option[HttpEntity.Strict] = {
    sentinelAssets.get(key).map( fileName => getFileAssetContent(fileName))
  }

  def getSentinelImage (pathName: String): Option[HttpEntity.Strict] = {
    FileUtils.fileContentsAsBytes(s"$sentinelDir/images/$pathName").map( bs=> ResponseData.forExtension( FileUtils.getExtension(pathName), bs))
  }

  //--- route

  def sentinelRoute: Route = {
    get {
      pathPrefix( "sentinel-asset" ~ Slash) { // mesh-models and images
        extractUnmatchedPath { p =>
          getSentinelAssetContent(p.toString()) match {
            case Some(content) => complete(content)
            case None => complete(StatusCodes.NotFound, p.toString())
          }
        }
      } ~
      pathPrefix("camera" ~ Slash) {
        extractUnmatchedPath { p =>
          getSentinelImage(p.toString()) match { // we load them automatically
            case Some(content) => complete(content)
            case None => complete(StatusCodes.NotFound, p.toString())
          }
        }
      } ~
      fileAsset("ui_cesium_sentinel.js") ~
      fileAsset("sentinel-icon.svg")
    }
  }

  override def route: Route = sentinelRoute ~ super.route

  //--- config

  override def getConfig (requestUri: Uri, remoteAddr: InetSocketAddress): String = {
    super.getConfig(requestUri,remoteAddr) + sentinelConfig(requestUri,remoteAddr)
  }

  def sentinelConfig (requestUri: Uri, remoteAddr: InetSocketAddress): String = {
    val labelOffsetX = config.getIntOrElse("sentinel-label-offset.x", 8)
    val labelOffsetY = config.getIntOrElse("sentinel-label-offset.y", 0)

    s"""
      export const sentinelColor = Cesium.Color.fromCssColorString('${config.getStringOrElse("sentinel-color", "chartreuse")}');
      export const sentinelLabelOffset = new Cesium.Cartesian2( $labelOffsetX, $labelOffsetY);
      export const sentinelBillboardDC = new Cesium.DistanceDisplayCondition( 0, 200000);
      export const sentinelImageWidth = ${config.getIntOrElse("sentinel-img-width", 400)};
      """
    //... and more to follow
  }

  //--- document content

  def uiSentinelWindow(title: String="Sentinels"): Text.TypedTag[String] = {
    uiWindow(title, "sentinel", "sentinel-icon.svg")(
      uiList("sentinel.list", 10, "main.selectSentinel(event)"),
      uiPanel("data", false)(
        uiFieldGroup(labelWidth="5rem", width="18rem") (
          uiField("fire", "sentinel.data.fire", isFixed = true),
          uiField("wind", "sentinel.data.anemo", isFixed = true),
          uiField("gas", "sentinel.data.gas", isFixed = true),
          uiField("thermo", "sentinel.data.thermo", isFixed = true),
          uiField("voc", "sentinel.data.voc", isFixed = true)
        )
      ),
      uiPanel("images", false)(
        uiList("sentinel.image.list", 6, "main.selectImage(event)"),
      )
    )
  }

  def uiSentinelIcon: Text.TypedTag[String] = {
    uiIcon("sentinel-icon.svg", "main.toggleWindow(event,'sentinel')", "sentinel_icon")
  }


  override def getHeaderFragments: Seq[Text.TypedTag[String]] = super.getHeaderFragments ++ Seq(
    extModule("ui_cesium_sentinel.js")
  )

  override def getBodyFragments: Seq[Text.TypedTag[String]] = super.getBodyFragments ++ Seq(
    uiSentinelWindow(),
    uiSentinelIcon
  )

  //--- websocket support

  // called from our DataClient actor
  // NOTE - this is called from the actor thread, beware of data races
  def receiveSentinelData: Receive = {
    case BusEvent(channel,rdgs: SentinelUpdates,_) =>
      synchronized {
        val msg = writer.clear().toJson( rdgs)
        push(TextMessage.Strict(msg))
      }

    case BusEvent(_,sm: SentinelSet,_) =>
      synchronized {
        sentinels = sm
        sentinelsMsg = None // re-serialize on next request
      }
  }

  override def receiveData: Receive = receiveSentinelData.orElse(super.receiveData)

  override protected def initializeConnection (ctx: WSContext, queue: SourceQueueWithComplete[Message]): Unit = {
    super.initializeConnection(ctx,queue)
    initializeSentinelConnection(ctx,queue)
  }

  protected def initializeSentinelConnection (ctx: WSContext, queue: SourceQueueWithComplete[Message]): Unit = {
    val remoteAddr = ctx.remoteAddress

    synchronized {
      if (sentinelsMsg.isEmpty) {
        val msg = writer.clear().toJson(sentinels)
        sentinelsMsg = Some(TextMessage.Strict(msg))
      }

      ifSome(sentinelsMsg) { tm => pushTo(remoteAddr, queue, tm) }
    }
  }
}
