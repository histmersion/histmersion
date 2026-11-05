package com.histmersion.web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.TileLayer

object TiledPoints:
  /** Pure: what a tiled layer shows after a read at the same URL and zoom — the features read now, then those shown
    * before that the read no longer has (the map let their tile go) and that `keep` (near the view).
    */
  type ReadNearPPart1 = ReadNearPPart1.T
  object ReadNearPPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadNearPPart2 = ReadNearPPart2.T
  object ReadNearPPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewNowSeenPPart1 = InViewNowSeenPPart1.T
  object InViewNowSeenPPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewNowSeenPPart2 = InViewNowSeenPPart2.T
  object InViewNowSeenPPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxZoomResult = MaxZoomResult.T
  object MaxZoomResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MergeKeyResult = MergeKeyResult.T
  object MergeKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeKeepResult = MergeKeepResult.T
  object MergeKeepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Template = Template.T
  object Template extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanPart1 = SpanPart1.T
  object SpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanPart2 = SpanPart2.T
  object SpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAtPart1 = ReadAtPart1.T
  object ReadAtPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAtPart2 = ReadAtPart2.T
  object ReadAtPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAttachedResult = IsAttachedResult.T
  object IsAttachedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadUrl = LoadUrl.T
  object LoadUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadSPart1 = LoadSPart1.T
  object LoadSPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadSPart2 = LoadSPart2.T
  object LoadSPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecolourSPart1 = RecolourSPart1.T
  object RecolourSPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecolourSPart2 = RecolourSPart2.T
  object RecolourSPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changed = Changed.T
  object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewNowResultPart2 = InViewNowResultPart2.T
  object InViewNowResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsResultPart1 = CountsResultPart1.T
  object CountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsResultPart2 = CountsResultPart2.T
  object CountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsResultPart3 = CountsResultPart3.T
  object CountsResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def merge[A](fresh: Seq[A], was: Seq[A], key: A => com.histmersion.web.TiledPoints.MergeKeyResult, keep: A => com.histmersion.web.TiledPoints.MergeKeepResult): Seq[A] =
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
    maxZoom: () => TiledPoints.MaxZoomResult
):
  val cells: PointLayer[TileCellPoint] = PointLayer(CellKind(dot), () => MapView.dots, () => MapView.spanMid())
  // `var`s: the URL template and span asked for, whether the layer is on, the map's tiles once made, the
  // span the features shown were read for
  private var template: Option[com.histmersion.web.TiledPoints.Template] = None
  private var span: (com.histmersion.web.TiledPoints.SpanPart1, com.histmersion.web.TiledPoints.SpanPart2) = (0L, 1L)
  private var on = false
  private var tiles: Option[PointTiles] = None
  private var shown: (Seq[A], Seq[TileCellPoint]) = (Nil, Nil)
  // the tiles' URL and the zoom what is shown was read at
  private var readAt: Option[(Option[com.histmersion.web.TiledPoints.ReadAtPart1], com.histmersion.web.TiledPoints.ReadAtPart2)] = None

  private def source: Option[PointTiles] =
    if tiles.isEmpty && TheMap.isUp then
      val t = TheMap.surface.pointTiles(s"tiles-${layer.wire}", layer.wire, maxZoom())
      t.onChange(() => if on then read())
      tiles = Some(t)
    tiles

  def isAttached: com.histmersion.web.TiledPoints.IsAttachedResult = on

  def attach(): Unit =
    if !on then
      on = true
      items.attach()
      cells.attach()
      source.foreach(_.setTiles(template))

  def detach(): Unit =
    if on then
      on = false
      readAt = None
      tiles.foreach(_.setTiles(None))
      items.detach()
      cells.detach()
      changed.update(_ + 1)

  /** The tiles of `url` (the span and options in it), drawn for `s`. */
  def load(url: com.histmersion.web.TiledPoints.LoadUrl, s: (com.histmersion.web.TiledPoints.LoadSPart1, com.histmersion.web.TiledPoints.LoadSPart2)): Unit =
    template = Some(url)
    span = s
    if on then source.foreach(_.setTiles(template))

  /** What is shown recoloured for `s` (the span moved; the tiles for it come later). */
  def recolour(s: (com.histmersion.web.TiledPoints.RecolourSPart1, com.histmersion.web.TiledPoints.RecolourSPart2)): Unit =
    span = s
    if on then
      items.show(shown._1, s)
      cells.show(shown._2, s)

  /** The features of the tiles in view, shown — once the tiles of the URL set last are all in. Read again for the same
    * URL at the same zoom (the view moved, or only its size changed: a sidebar dragged), what was shown near the view
    * and is no longer in a tile the map holds stays ([[TiledPoints.merge]]): the map lets go of the tiles a narrower
    * view leaves, and their points went and came back, regrouping the bubbles near them.
    */
  private def read(): Unit =
    tiles.filter(_.loaded).foreach { t =>
      val (cs, is) = t.features.partition(TileDecode.isCell)
      val fresh = (is.flatMap(decode), cs.map(TileDecode.cell(_, dot)))
      val at = (template, math.floor(TheMap.surface.zoom).toInt)
      shown =
        if !readAt.contains(at) then fresh
        else
          val s = TheMap.surface
          val margin = math.max(s.size._1, s.size._2)
          def near(p: (com.histmersion.web.TiledPoints.ReadNearPPart1, com.histmersion.web.TiledPoints.ReadNearPPart2)) = Clustering.inView(s, LatLon(p._1, p._2), margin)
          (
            TiledPoints.merge(fresh._1, shown._1, items.kind.key, a => near(items.kind.at(a))),
            TiledPoints.merge(fresh._2, shown._2, (c: TileCellPoint) => c.key, c => near((c.lat, c.lon)))
          )
      readAt = Some(at)
      items.show(shown._1, span)
      cells.show(shown._2, span)
      changed.update(_ + 1)
    }

  /** Ticks whenever what is shown changes (a tile read, the layer switched off): the lists of what is on the map follow
    * it.
    */
  val changed: com.raquo.laminar.api.L.Var[com.histmersion.web.TiledPoints.Changed] = com.raquo.laminar.api.L.Var(0)

  /** What is shown in the map's view now: the items drawn as themselves, and how many more its cells there count.
    * Nothing while the layer is off.
    */
  def inViewNow(): (Seq[A], com.histmersion.web.TiledPoints.InViewNowResultPart2) =
    if !on || !TheMap.isUp then (Nil, 0)
    else
      val s = TheMap.surface
      def seen(p: (com.histmersion.web.TiledPoints.InViewNowSeenPPart1, com.histmersion.web.TiledPoints.InViewNowSeenPPart2)) = Clustering.inView(s, LatLon(p._1, p._2), 0)
      (shown._1.filter(a => seen(items.kind.at(a))), shown._2.filter(c => seen((c.lat, c.lon))).map(_.n).sum)

  /** How many items and cells are shown, and the items the cells count (diagnostics). */
  def counts: (com.histmersion.web.TiledPoints.CountsResultPart1, com.histmersion.web.TiledPoints.CountsResultPart2, com.histmersion.web.TiledPoints.CountsResultPart3) = (shown._1.size, shown._2.size, shown._2.map(_.n).sum)
