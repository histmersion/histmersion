package com.histmersion.web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** One [[PointTiles]] layer as a vector source of the server's tiles. MapLibre fetches, caches and decodes the tiles
  * (in its workers) only for a source a style layer reads: an invisible circle layer does. The features are read from
  * the tiles of the view's zoom (`api.TileProps.Zoom`, which every feature carries — MapLibre keeps a coarser tile
  * drawn while the finer ones come in), once each.
  */
final private[maplibre] class GlPointTiles(gl: GlMap, id: String, layer: String, maxZoom: Int) extends PointTiles:
  // `var`s: the template asked for, and the listeners
  private var template: Option[com.histmersion.web.map.maplibre.GlPointTiles.Template] = None
  private val listeners = mutable.ListBuffer.empty[() => Unit]
  private var queued = false
  private val hitLayer = s"$id-points"

  private def notifyLater(): Unit =
    if !queued then
      queued = true
      val _ = dom.window.requestAnimationFrame { _ => queued = false; listeners.toList.foreach(_()) }

  gl.on(
    "sourcedata",
    (e: GlEvent) =>
      if e.asInstanceOf[js.Dynamic].sourceId.asInstanceOf[js.UndefOr[String]].contains(id) && loaded then notifyLater()
  )
  gl.on("moveend", (_: GlEvent) => if loaded then notifyLater())

  /** The source and its layer, when the style lacks them (made, or a new base map loaded). */
  def restore(): Unit =
    template.foreach { t =>
      if gl.isStyleLoaded() || gl.getStyle() != null then
        scala.util.Try {
          if gl.getSource(id).isEmpty then
            gl.addSource(
              id,
              js.Dynamic
                .literal(`type` = "vector", tiles = js.Array(t), minzoom = 0, maxzoom = maxZoom, tileSize = 512)
                .asInstanceOf[js.Object]
            )
          if gl.getLayer(hitLayer).isEmpty then
            gl.addLayer(
              js.Dynamic
                .literal(
                  id = hitLayer,
                  `type` = "circle",
                  source = id,
                  `source-layer` = layer,
                  paint = js.Dynamic.literal(`circle-radius` = 0, `circle-opacity` = 0, `circle-stroke-width` = 0)
                )
                .asInstanceOf[js.Object]
            )
        }
    }

  def setTiles(t: Option[com.histmersion.web.map.maplibre.GlPointTiles.SetTilesT]): Unit =
    if t != template then
      template = t
      t match
        case None =>
          scala.util.Try {
            if gl.getLayer(hitLayer).isDefined then gl.removeLayer(hitLayer)
            if gl.getSource(id).isDefined then gl.removeSource(id)
          }
        case Some(url) =>
          gl.getSource(id).toOption match
            case Some(src) => scala.util.Try(src.setTiles(js.Array(url)))
            case None      => restore()

  def loaded: com.histmersion.web.map.maplibre.GlPointTiles.LoadedResult =
    template.isEmpty || scala.util.Try(gl.getSource(id).isDefined && gl.isSourceLoaded(id)).getOrElse(false)

  def onChange(f: () => Unit): Subscription =
    listeners += f
    () => { val _ = listeners -= f }

  def features: Seq[TilePointFeature] =
    if template.isEmpty || gl.getSource(id).isEmpty then Nil
    else
      val z = math.max(0, math.min(maxZoom, math.floor(gl.getZoom()).toInt))
      val seen = mutable.HashSet.empty[String]
      scala.util
        .Try(gl.querySourceFeatures(id, js.Dynamic.literal(sourceLayer = layer).asInstanceOf[js.Object]))
        .getOrElse(js.Array())
        .toSeq
        .flatMap { f =>
          val props = f.properties.asInstanceOf[js.Dictionary[js.Any]]
          val atZoom =
            props.get(com.histmersion.api.catalogue.TileProps.Zoom).forall(v => v.asInstanceOf[Double].toInt == z)
          val key = props.get(com.histmersion.api.catalogue.TileProps.Key).map(_.toString).getOrElse("")
          if !atZoom || !seen.add(key) then None
          else ShapeHits.point(f.geometry).map(TilePointFeature(_, props))
        }

object GlPointTiles:
  type Template = Template.T
  object Template extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetTilesT = SetTilesT.T
  object SetTilesT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadedResult = LoadedResult.T
  object LoadedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
