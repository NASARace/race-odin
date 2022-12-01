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

import akka.actor.Actor.Receive
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.SourceQueueWithComplete
import com.typesafe.config.Config
import gov.nasa.race.common.ConstAsciiSlice.asc
import gov.nasa.race.common.JsonWriter
import gov.nasa.race.config.ConfigUtils.ConfigWrapper
import gov.nasa.race.core.{BusEvent, ParentActor, PipedRaceDataClient}
import gov.nasa.race.http.{CachedFileAssetMap, DocumentRoute, HttpServer, PushWSRaceRoute, ResponseData, WSContext}
import gov.nasa.race.cesium.CesiumRoute
import gov.nasa.race.ifSome
import gov.nasa.race.odin.sentinel.SentinelSensorReading.{DefaultImageDir, IMAGE_PREFIX}
import gov.nasa.race.ui._
import gov.nasa.race.util.FileUtils
import scalatags.Text

import scala.collection.immutable.Iterable
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
trait SentinelRoute extends  CesiumRoute with PushWSRaceRoute with PipedRaceDataClient {

  private val writer = new JsonWriter()
  private val sentinelCmdParser = new SentinelParser()

  var sentinels: SentinelSet = SentinelSet(Map.empty[String,Sentinel])
  var sentinelsMsg: Option[TextMessage.Strict] = None

  val sentinelDir = config.getString("sentinel.dir")
  val imageDir = FileUtils.ensureWritableDir(config.getStringOrElse("sentinel.image-dir", DefaultImageDir)).get
  val sentinelAssets = getSymbolicAssetMap("sentinel.assets", config,
    Seq(("sentinel","sentinel-sym.png"), ("fire","fire.png"), ("smoke", "smoke.png"), ("fire-smoke", "fire-smoke.png")))
  

  def getSentinelAssetContent (key: String): Option[HttpEntity.Strict] = {
    sentinelAssets.get(key).map( fileName => getFileAssetContent(fileName))
  }

  def getSentinelImage (pathName: String): Option[HttpEntity.Strict] = {
    FileUtils.fileContentsAsBytes(s"$imageDir/$pathName").map( bs=> ResponseData.forExtension( FileUtils.getExtension(pathName), bs))
  }

  //--- route

  def sentinelRoute: Route = {
    get {
      pathPrefix( "sentinel-asset" ~ Slash) { // mesh-models and images
        extractUnmatchedPath { p =>
          completeWithSymbolicAsset(p.toString, sentinelAssets)
        }
      } ~
      pathPrefix( IMAGE_PREFIX ~ Slash) {
        extractUnmatchedPath { p =>
          getSentinelImage(p.toString()) match { // we load them automatically
            case Some(content) => complete(content)
            case None => complete(StatusCodes.NotFound, p.toString())
          }
        }
      } ~
      fileAssetPath("ui_cesium_sentinel.js") ~
      fileAssetPath("sentinel-icon.svg")
    }
  }

  override def route: Route = sentinelRoute ~ super.route

  //--- config

  override def getConfig (requestUri: Uri, remoteAddr: InetSocketAddress): String = {
    super.getConfig(requestUri,remoteAddr) + sentinelConfig(requestUri,remoteAddr)
  }

  def sentinelConfig (requestUri: Uri, remoteAddr: InetSocketAddress): String = {
    val cfg = config.getConfig("sentinel")

    val labelOffsetX = cfg.getIntOrElse("label-offset.x", 8)
    val labelOffsetY = cfg.getIntOrElse("label-offset.y", 0)
    val pointDist = cfg.getIntOrElse("point-dist", 120000)

    s"""export const sentinel = {
  ${cesiumLayerConfig(cfg, "/fire/detection/Sentinel", "stationary Sentinel fire sensors")},
  color: Cesium.Color.fromCssColorString('${cfg.getStringOrElse("color", "chartreuse")}'),
  alertColor: Cesium.Color.fromCssColorString('${cfg.getStringOrElse("alert-color", "deeppink")}'),
  labelFont: '${cfg.getStringOrElse("label-font", "16px sans-serif")}',
  labelBackground: ${cesiumColor(cfg, "label-bg", "black")},
  labelOffset: new Cesium.Cartesian2( $labelOffsetX, $labelOffsetY),
  labelDC: new Cesium.DistanceDisplayCondition( 0, ${cfg.getIntOrElse("label-dist", 200000)}),
  pointSize: ${cfg.getIntOrElse("point-size", 5)},
  pointOutlineColor: ${cesiumColor(cfg,"point-outline-color", "black")},
  pointOutlineWidth: ${cfg.getIntOrElse("point-outline-width", 1)},
  pointDC: new Cesium.DistanceDisplayCondition( $pointDist, Number.MAX_VALUE),
  infoFont: '${cfg.getStringOrElse("info-font", "14px monospace")}',
  infoOffset:  new Cesium.Cartesian2( $labelOffsetX, ${labelOffsetY + 16}),
  infoDC: new Cesium.DistanceDisplayCondition( 0, ${cfg.getIntOrElse("info-dist", 40000)}),
  billboardDC: new Cesium.DistanceDisplayCondition( 0, $pointDist),
  imageWidth: ${cfg.getIntOrElse("img-width", 400)},
  maxHistory: ${cfg.getIntOrElse("max-history", 10)},
  zoomHeight: ${cfg.getIntOrElse("zoom-height", 80000)}
};"""
    //... and more to follow
  }

  //--- document content

  def uiSentinelWindow(title: String="Sentinels"): Text.TypedTag[String] = {
    val maxDataRows = 8

    uiWindow(title, "sentinel", "sentinel-icon.svg")(
      cesiumLayerPanel("sentinel", "main.toggleShowSentinel(event)"),
      uiList("sentinel.list", 10, "main.selectSentinel(event)", dblClickAction = "main.zoomToSentinel(event)"),
      uiPanel("data", false)(
        uiTabbedContainer()(
          uiTab("fire", false)(uiList("sentinel.fire.list", maxDataRows)),
          uiTab("smoke", false)(uiList("sentinel.smoke.list", maxDataRows)),
          uiTab("imgs", true)(uiList("sentinel.image.list", maxDataRows, "main.selectImage(event)")),
          uiTab("gas", false)(uiList("sentinel.gas.list", maxDataRows)),
          uiTab("temp", false)(uiList("sentinel.thermo.list", maxDataRows)),
          uiTab("wind", false)(uiList("sentinel.anemo.list", maxDataRows)),
          uiTab("voc", false)(uiList("sentinel.voc.list", maxDataRows)),
          uiTab("accel", false)(uiList("sentinel.accel.list", maxDataRows)),
          uiTab("gps",false)(uiList("sentinel.gps.list", maxDataRows))
        )
      ),
      uiPanel("diagnostics", false)(
        uiList("sentinel.diag.cmdList", maxRows=6, "main.selectSentinelCmd(event)"),
        uiColumnContainer()(
          uiTextArea( "sentinel.diag.cmd", isFixed=true, visCols=44, visRows=4 )
        ),
        uiRowContainer()(
          uiButton("send", "main.sendSentinelCmd()"),
          uiButton("clear history", "main.clearSentinelHistory()")
        ),
        uiColumnContainer()(
          uiTextArea( "sentinel.diag.log", isReadOnly=true, isFixed=true, visCols=44, visRows=4 ),
        )
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
    case BusEvent(_,rdgs: SentinelUpdates,_) =>
      synchronized {
        val msg = writer.clear().toJson( rdgs)
        push(TextMessage.Strict(msg))
      }

    case BusEvent(_,sm: SentinelSet,_) =>
      synchronized {
        sentinels = sm
        sentinelsMsg = None // re-serialize on next request
      }

    case cr: SentinelCommandResponse =>
      ifSome(cr.text) { txt=>
        val msg = s"""{"cmdResponse":"${JsonWriter.toJsonString(txt)}"}"""
        pushTo(cr.remoteAddress, TextMessage.Strict(msg))
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

  /**
   * handle client messages (commands)
   */
  override protected def handleIncoming (ctx: WSContext, m: Message): Iterable[Message] = {
    handleIncomingSentinelMsg(ctx, m)
    super.handleIncoming(ctx,m) // no own returns
  }

  def handleIncomingSentinelMsg (ctx: WSContext, m: Message): Unit = {
    // we parse here to check for malformed cmd requests before we send them to other actors
    withStrictMessageData(m) { data=>
      if (sentinelCmdParser.initialize(data)) {
        val parseResult = sentinelCmdParser.parseSentinelCommand()
        parseResult match {
          case Some(cmd) => publishData( SentinelCommandRequest( actorRef, ctx.remoteAddress, cmd, true))
          case None => warning(s"ignoring malformed command: '${sentinelCmdParser.dataAsString}'")
        }
      }
    }
  }
}

/**
 * simple service to show live Sentinel data
 */
class SentinelApp (val parent: ParentActor, val config: Config) extends DocumentRoute with SentinelRoute