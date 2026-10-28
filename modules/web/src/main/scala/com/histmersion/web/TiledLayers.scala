package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.api.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** A CELL of a server tile: `n` items of one kind counted where there were more than a tile keeps
  * alone (`ui.map-tiles`, docs/map-layers.md) — at the items' mean point, with the least and the
  * greatest middle of their dates and their box. */
final case class TileCellPoint(key: String, kind: DotKind, lat: Double, lon: Double, n: Int,
    midLo: Option[Double], midHi: Option[Double], box: GeoBounds)

/** The pure half of the tiled layers: a tile feature as the app's own point records. */
object TileDecode:
  private def str(p: js.Dictionary[js.Any], k: String): Option[String] =
    p.get(k).filter(v => v != null && !js.isUndefined(v)).map(_.toString)
  private def num(p: js.Dictionary[js.Any], k: String): Option[Double] =
    p.get(k).filter(v => js.typeOf(v) == "number").map(_.asInstanceOf[Double])
  private def long(p: js.Dictionary[js.Any], k: String): Option[Long] = num(p, k).map(_.toLong)

  /** Is it a cell of several items (it carries their count)? */
  def isCell(f: TilePointFeature): Boolean = num(f.props, P.Count).exists(_ > 1)

  /** A cell of `kind`: its count, dates' range and box (none of them missing: its own point). */
  def cell(f: TilePointFeature, kind: DotKind): TileCellPoint =
    val p = f.props
    val box = (for w <- num(p, P.West); s <- num(p, P.South); e <- num(p, P.East); n <- num(p, P.North)
      yield GeoBounds(s, w, n, e)).getOrElse(GeoBounds(f.at.lat, f.at.lon, f.at.lat, f.at.lon))
    TileCellPoint(str(p, P.Key).getOrElse(s"${f.at.lat}|${f.at.lon}"), kind, f.at.lat, f.at.lon,
      num(p, P.Count).fold(1)(_.toInt), num(p, P.MidLo), num(p, P.MidHi), box)

  def site(f: TilePointFeature): Option[SitePoint] =
    val p = f.props
    str(p, P.Id).map { id =>
      val period = for a <- num(p, P.PeriodStart); b <- num(p, P.PeriodEnd) yield (a, b)
      SitePoint(Some(id), str(p, P.Label).getOrElse(""), str(p, P.Kind).getOrElse(com.histmersion.values.PlaceKind.Site),
        str(p, P.SiteType), str(p, P.Basis), f.at.lat, f.at.lon, period)
    }

  def event(f: TilePointFeature): Option[EventGeo] =
    val p = f.props
    for id <- str(p, P.Id) yield EventGeo(id, str(p, P.Label).getOrElse(id), str(p, P.Kind).getOrElse(""),
      str(p, P.Scale).getOrElse(EventScale.Local), str(p, P.Historicity).getOrElse(""),
      TimeSpan(long(p, P.NotBefore), long(p, P.BeginBy), long(p, P.EndFrom), long(p, P.NotAfter)),
      f.at.lat, f.at.lon, str(p, P.Anchor).getOrElse("event"), str(p, P.PlaceId), str(p, P.PlaceLabel), str(p, P.ParentId),
      None, str(p, P.Tier).getOrElse(EventTier.Attested), str(p, P.Size), str(p, P.Precision).getOrElse(EventPrecision.Precise),
      str(p, P.Dating))

  def media(f: TilePointFeature): Option[MediaGeoHit] =
    val p = f.props
    for uid <- str(p, P.Uid) yield MediaGeoHit(str(p, P.Citekey), str(p, P.AssetName).getOrElse(uid), str(p, P.Kind),
      f.at.lat, f.at.lon, long(p, P.PeriodStart), long(p, P.PeriodEnd), str(p, P.Caption), uid, str(p, P.Provider),
      str(p, P.License), str(p, P.Credit), str(p, P.ThumbUrl), fileUrl = Some(MediaThumb.filePath(uid)),
      sourceUrl = str(p, P.SourceUrl), licenseUrl = str(p, P.LicenseUrl), author = str(p, P.Author))

object TiledPoints:
  /** Pure: what a tiled layer shows after a read at the same URL and zoom — the features read now, then those
    * shown before that the read no longer has (the map let their tile go) and that `keep` (near the view). */
  def merge[A](fresh: Seq[A], was: Seq[A], key: A => String, keep: A => Boolean): Seq[A] =
    val now = fresh.iterator.map(key).toSet
    fresh ++ was.filter(a => !now(key(a)) && keep(a))

/** The span the tiled layers are drawn for in playback: the playhead's window widened to whole buckets
  * of the years `ui.map-tiles.playback-bucket-seconds` of playback cover (a round number of years), so
  * the tiles' URL — and the request for them — changes once a bucket, and a replay finds them kept. */
object PlaybackBuckets:
  /** Pure: the bucket of `yearsPerSecond` × `seconds`, up to a round 1, 2 or 5 × 10ⁿ years (at least 1). */
  def years(yearsPerSecond: Double, seconds: Double): Long =
    val raw = math.max(1.0, math.abs(yearsPerSecond) * seconds)
    val p = math.pow(10, math.floor(math.log10(raw)))
    val step = List(1.0, 2.0, 5.0, 10.0).map(_ * p).find(_ >= raw).getOrElse(10 * p)
    math.max(1L, math.round(step))

  /** Pure: `span` widened outwards to whole buckets of `bucket` years. */
  def widen(span: (Long, Long), bucket: Long): (Long, Long) =
    val (lo, hi) = (span._1.min(span._2), span._1.max(span._2))
    val b = bucket.max(1)
    (Math.floorDiv(lo, b) * b, Math.floorDiv(hi, b) * b + b - 1)

/** The cells of a tiled layer drawn as bubbles ([[MapView.dots]] draws a cell alone as the bubble of
  * its count): what they count for and where a click goes — into their box. */
final class CellKind(val dot: DotKind) extends PointKind[TileCellPoint]:
  def shape: Shape = Shape.Circle
  def icon: Icon = Icon.PointSite
  def layerLabel: String = dot.many
  def meaning: String = s"several ${dot.many} nearby"
  def box: (Int, Int) = (0, 0)
  def key(c: TileCellPoint): String = c.key
  def at(c: TileCellPoint): (Double, Double) = (c.lat, c.lon)
  def inner(c: TileCellPoint): String = c.n.toString
  def fill(c: TileCellPoint, span: (Long, Long)): String = MapView.rampEnds(c.midLo.toList ++ c.midHi, span, Certainty.NoSay)._1
  def member(c: TileCellPoint): Member = Member(dot, c.n, (c.midLo.toList ++ c.midHi).distinct, Certainty.NoSay, cell = Some(c.box))
  def label(c: TileCellPoint): String = Plural.count(c.n, dot.one, dot.many)
  def action(c: TileCellPoint): String = "zoom in"
  def target(c: TileCellPoint, asOf: Option[Long]): Option[Preview.Target] = None
  def activate(c: TileCellPoint, how: Activation): Unit = MapView.zoomInto(List(c.box))
  def open(key: String, c: TileCellPoint, row: dom.Element): Unit = MapView.zoomInto(List(c.box))
  override def recolour(m: DomMarker, c: TileCellPoint, span: (Long, Long)): Unit = MapView.repaintCell(m, member(c))

/** One of the map's point layers read from the server's tiles (`GET /api/tiles`, [[PointTiles]]): its
  * items in their [[PointLayer]] and the cells of the crowded tiles in another, both in the map's one
  * cluster layer. The page sets the tiles' URL for the span and the layer's options ([[load]]); the
  * features follow the view as the map brings its tiles in. Switched off, no tile is asked for. */
final class TiledPoints[A](layer: TileLayer, val items: PointLayer[A], dot: DotKind, decode: TilePointFeature => Option[A],
    maxZoom: () => Int):
  val cells: PointLayer[TileCellPoint] = PointLayer(CellKind(dot), () => MapView.dots, () => MapView.spanMid())
  // `var`s: the URL template and span asked for, whether the layer is on, the map's tiles once made, the
  // span the features shown were read for
  private var template: Option[String] = None
  private var span: (Long, Long) = (0L, 1L)
  private var on = false
  private var tiles: Option[PointTiles] = None
  private var shown: (Seq[A], Seq[TileCellPoint]) = (Nil, Nil)

  private def source: Option[PointTiles] =
    if tiles.isEmpty && TheMap.isUp then
      val t = TheMap.surface.pointTiles(s"tiles-${layer.wire}", layer.wire, maxZoom())
      t.onChange(() => if on then read())
      tiles = Some(t)
    tiles

  def isAttached: Boolean = on

  def attach(): Unit =
    if !on then
      on = true
      items.attach()
      cells.attach()
      source.foreach(_.setTiles(template))

  def detach(): Unit =
    if on then
      on = false
      tiles.foreach(_.setTiles(None))
      items.detach()
      cells.detach()

  /** The tiles of `url` (the span and options in it), drawn for `s`. */
  def load(url: String, s: (Long, Long)): Unit =
    template = Some(url)
    span = s
    if on then source.foreach(_.setTiles(template))

  /** What is shown recoloured for `s` (the span moved; the tiles for it come later). */
  def recolour(s: (Long, Long)): Unit =
    span = s
    if on then
      items.show(shown._1, s)
      cells.show(shown._2, s)

  /** The features of the tiles in view, shown — once the tiles of the URL set last are all in. */
  private def read(): Unit =
    tiles.filter(_.loaded).foreach { t =>
      val (cs, is) = t.features.partition(TileDecode.isCell)
      shown = (is.flatMap(decode), cs.map(TileDecode.cell(_, dot)))
      items.show(shown._1, span)
      cells.show(shown._2, span)
    }

  /** How many items and cells are shown, and the items the cells count (diagnostics). */
  def counts: (Int, Int, Int) = (shown._1.size, shown._2.size, shown._2.map(_.n).sum)
