package com.histmersion
package web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.TileLayer
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.Geo
import com.histmersion.api.RowCount
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Year

object TiledPoints:
  /** Pure: what a tiled layer shows after a read at the same URL and zoom — the features read now, then those shown
    * before that the read no longer has (the map let their tile go) and that `keep` (near the view).
    */

  def merge[A](fresh: Seq[A], was: Seq[A], key: A => String, keep: A => Boolean): Seq[A] =
    val now = fresh.iterator.map(key).toSet
    fresh ++ was.filter(a => !now(key(a)) && keep(a))

/** One of the map's point layers read from the server's tiles (`GET /api/tiles`, [[PointTiles]]): its items in their
  * [[PointLayer]] and the cells of the crowded tiles in another, both in the map's one cluster layer. The page sets the
  * tiles' URL for the span and the layer's options ([[load]]); the features follow the view as the map brings its tiles
  * in. Switched off, no tile is asked for.
  */
final class TiledPoints[A](
    layer: TileLayer,
    val items: PointLayer[A],
    dot: DotKind,
    decode: TilePointFeature => Option[A],
    maxZoom: () => MapTilesUiConfig.MaxZoom
):
  val cells: PointLayer[TileCellPoint] = PointLayer(CellKind(dot), () => MapView.dots, () => MapView.spanMid())
  // `var`s: the URL template and span asked for, whether the layer is on, the map's tiles once made, the
  // span the features shown were read for
  private var template: Option[ApiClient.Url] = None
  private var span: (Year.Astronomical, Year.Astronomical) = (Year.Astronomical(0L), Year.Astronomical(1L))
  private var on = false
  private var tiles: Option[PointTiles] = None
  private var shown: (Seq[A], Seq[TileCellPoint]) = (Nil, Nil)
  // the tiles' URL and the zoom what is shown was read at
  private var readAt: Option[(Option[ApiClient.Url], Int)] = None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def tileTemplate: Option[BasemapApi.TileTemplate] =
    template.map(u => BasemapApi.TileTemplate(u.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def source: Option[PointTiles] =
    if tiles.isEmpty && TheMap.isUp then
      val t = TheMap.surface.pointTiles(
        MapSurface.LayerId(s"tiles-${layer.wire}"),
        MapSurface.LayerId(layer.wire.value),
        MapSurface.PointTilesMaxZoom(maxZoom().value)
      )
      t.onChange(() => if on then read())
      tiles = Some(t)
    tiles

  def isAttached: Boolean = on
  def notifying: Boolean = on && tiles.exists(_.notifying)

  def attach(): Unit =
    if !on then
      on = true
      items.attach()
      cells.attach()
      source.foreach(_.setTiles(tileTemplate))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def detach(): Unit =
    if on then
      on = false
      readAt = None
      tiles.foreach(_.setTiles(None))
      items.detach()
      cells.detach()
      changed.update(r => Poll.Revision(r.value + 1))

  /** The tiles of `url` (the span and options in it), drawn for `s`. */
  def load(url: ApiClient.Url, s: (Year.Astronomical, Year.Astronomical)): Unit =
    template = Some(url)
    span = s
    if on then source.foreach(_.setTiles(tileTemplate))

  /** What is shown recoloured for `s` (the span moved; the tiles for it come later). */
  def recolour(s: (Year.Astronomical, Year.Astronomical)): Unit =
    span = s
    if on then
      items.show(shown._1, s)
      cells.show(shown._2, s)

  /** The features of the tiles in view, shown — once the tiles of the URL set last are all in. Read again for the same
    * URL at the same zoom (the view moved, or only its size changed: a sidebar dragged), what was shown near the view
    * and is no longer in a tile the map holds stays ([[TiledPoints.merge]]): the map lets go of the tiles a narrower
    * view leaves, and their points went and came back, regrouping the bubbles near them.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def read(): Unit =
    tiles.filter(_.loaded).foreach { t =>
      val (cs, is) = t.features.partition(TileDecode.isCell)
      val fresh = (is.flatMap(decode), cs.map(TileDecode.cell(_, dot)))
      val at = (template, math.floor(TheMap.surface.zoom.value).toInt)
      shown =
        if !readAt.contains(at) then fresh
        else
          val s = TheMap.surface
          val margin = Clustering.InViewMargin(math.max(s.size._1.value, s.size._2.value))
          def near(p: (Geo.Latitude, Geo.Longitude)) = Clustering.inView(s, LatLon(p._1, p._2), margin)
          (
            TiledPoints.merge(fresh._1, shown._1, a => items.kind.key(a).value, a => near(items.kind.at(a))),
            TiledPoints.merge(
              fresh._2,
              shown._2,
              (c: TileCellPoint) => c.key.value,
              c => near((Geo.Latitude(c.lat.value), Geo.Longitude(c.lon.value)))
            )
          )
      readAt = Some(at)
      items.show(shown._1, span)
      cells.show(shown._2, span)
      changed.update(r => Poll.Revision(r.value + 1))
    }

  /** Ticks whenever what is shown changes (a tile read, the layer switched off): the lists of what is on the map follow
    * it.
    */
  val changed: com.raquo.laminar.api.L.Var[Poll.Revision] = com.raquo.laminar.api.L.Var(Poll.Revision(0))

  /** What is shown in the map's view now: the items drawn as themselves, and how many more its cells there count.
    * Nothing while the layer is off.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def inViewNow(): (Seq[A], RowCount) =
    if !on || !TheMap.isUp then (Nil, RowCount(0))
    else
      val s = TheMap.surface
      def seen(p: (Geo.Latitude, Geo.Longitude)) = Clustering.inView(s, LatLon(p._1, p._2), Clustering.InViewMargin(0))
      (
        shown._1.filter(a => seen(items.kind.at(a))),
        RowCount(
          shown._2
            .filter(c => seen((Geo.Latitude(c.lat.value), Geo.Longitude(c.lon.value))))
            .map(_.n.value)
            .sum
        )
      )

  /** How many items and cells are shown, and the items the cells count (diagnostics). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def counts: (RowCount, RowCount, RowCount) =
    (RowCount(shown._1.size), RowCount(shown._2.size), RowCount(shown._2.map(_.n.value).sum))
