package com.histmersion
package web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.TileProps

/** One [[PointTiles]] layer as a vector source of the server's tiles. MapLibre fetches, caches and decodes the tiles
  * (in its workers) only for a source a style layer reads: an invisible circle layer does. The features are read from
  * the tiles of the view's zoom (`api.TileProps.Zoom`, which every feature carries — MapLibre keeps a coarser tile
  * drawn while the finer ones come in), once each.
  */
final private[maplibre] class GlPointTiles(
    gl: GlMap,
    id: MapSurface.LayerId,
    layer: MapSurface.LayerId,
    maxZoom: MapSurface.PointTilesMaxZoom
) extends PointTiles:
  private val sourceId: String = id.value
  private val tileLayer: String = layer.value
  private val zoomCap: Int = maxZoom.value
  // `var`s: the template asked for, and the listeners
  private var template: Option[BasemapApi.TileTemplate] = None
  private val listeners = mutable.ListBuffer.empty[() => Unit]
  private var queued = false
  private val hitLayer = s"$sourceId-points"

  private def notifyLater(): Unit =
    if !queued then
      queued = true
      val _ = dom.window.requestAnimationFrame { _ => queued = false; listeners.toList.foreach(_()) }

  gl.on(
    "sourcedata",
    (e: GlEvent) =>
      if e.asInstanceOf[js.Dynamic].sourceId.asInstanceOf[js.UndefOr[String]].contains(sourceId) && loaded then
        notifyLater()
  )
  gl.on("moveend", (_: GlEvent) => if loaded then notifyLater())

  /** The source and its layer, when the style lacks them (made, or a new base map loaded). */
  def restore(): Unit =
    template.foreach { t =>
      if gl.isStyleLoaded() || gl.getStyle() != null then
        scala.util.Try {
          if gl.getSource(sourceId).isEmpty then
            gl.addSource(
              sourceId,
              js.Dynamic
                .literal(`type` = "vector", tiles = js.Array(t), minzoom = 0, maxzoom = zoomCap, tileSize = 512)
                .asInstanceOf[js.Object]
            )
          if gl.getLayer(hitLayer).isEmpty then
            gl.addLayer(
              js.Dynamic
                .literal(
                  id = hitLayer,
                  `type` = "circle",
                  source = sourceId,
                  `source-layer` = tileLayer,
                  paint = js.Dynamic.literal(`circle-radius` = 0, `circle-opacity` = 0, `circle-stroke-width` = 0)
                )
                .asInstanceOf[js.Object]
            )
        }
    }

  def setTiles(t: Option[BasemapApi.TileTemplate]): Unit =
    if t != template then
      template = t
      t match
        case None =>
          scala.util.Try {
            if gl.getLayer(hitLayer).isDefined then gl.removeLayer(hitLayer)
            if gl.getSource(sourceId).isDefined then gl.removeSource(sourceId)
          }
        case Some(url) =>
          gl.getSource(sourceId).toOption match
            case Some(src) => scala.util.Try(src.setTiles(js.Array(url)))
            case None      => restore()

  def loaded: Boolean =
    template.isEmpty || scala.util.Try(gl.getSource(sourceId).isDefined && gl.isSourceLoaded(sourceId)).getOrElse(false)

  def onChange(f: () => Unit): Subscription =
    listeners += f
    () => { val _ = listeners -= f }

  def features: Seq[TilePointFeature] =
    if template.isEmpty || gl.getSource(sourceId).isEmpty then Nil
    else
      val z = math.max(0, math.min(zoomCap, math.floor(gl.getZoom()).toInt))
      val seen = mutable.HashSet.empty[String]
      scala.util
        .Try(gl.querySourceFeatures(sourceId, js.Dynamic.literal(sourceLayer = tileLayer).asInstanceOf[js.Object]))
        .getOrElse(js.Array())
        .toSeq
        .flatMap { f =>
          val props = f.properties.asInstanceOf[js.Dictionary[js.Any]]
          val atZoom =
            props.get(TileProps.Zoom).forall(v => v.asInstanceOf[Double].toInt == z)
          val key = props.get(TileProps.Key).map(_.toString).getOrElse("")
          if !atZoom || !seen.add(key) then None
          else ShapeHits.point(f.geometry).map(TilePointFeature(_, props))
        }
