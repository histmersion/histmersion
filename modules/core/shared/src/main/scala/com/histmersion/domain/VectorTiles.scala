package com.histmersion.domain

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** One tile of the XYZ grid MapLibre asks for (Web Mercator; a tile's side is [[TileCoord.Extent]]
  * units of the vector tile format): zoom `z`, column `x` from the antimeridian eastwards, row `y` from
  * the north. Only a tile inside the grid exists. */
final case class TileCoord private (z: Int, x: Long, y: Long):
  /** Tiles a side at this zoom. */
  def side: Long = 1L << z

  /** The tile's bounds in degrees: west, south, east, north. */
  def bounds: (Double, Double, Double, Double) =
    val n = side.toDouble
    (x / n * 360 - 180, TileCoord.latOf((y + 1) / n), (x + 1) / n * 360 - 180, TileCoord.latOf(y / n))

  /** Where `lat`/`lon` falls in this tile, in tile units (0 at its west and north edges, [[TileCoord.Extent]]
    * at the east and south): outside the tile below 0 or at the extent and beyond. Pure. */
  def unitsOf(lat: Double, lon: Double): (Double, Double) =
    val n = side.toDouble
    val (mx, my) = TileCoord.mercator(lat, lon)
    ((mx * n - x) * TileCoord.Extent, (my * n - y) * TileCoord.Extent)

object TileCoord:
  /** The units a tile's side is divided into (the vector tile format's usual extent). */
  val Extent: Int = 4096

  /** How far north and south the grid goes: Web Mercator's square world. */
  val MaxLat: Double = 85.0511287798

  /** The tile `z`/`x`/`y`, when the grid has it and `z` is no deeper than `maxZoom`. */
  def of(z: Int, x: Long, y: Long, maxZoom: Int): Either[String, TileCoord] =
    if z < 0 || z > maxZoom then Left(s"zoom $z is outside 0..$maxZoom")
    else if x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z) then Left(s"tile $x/$y is outside the grid of zoom $z")
    else Right(new TileCoord(z, x, y))

  /** Pure: a point's place on the unit square of Web Mercator (0..1 east and south), its latitude held
    * inside [[MaxLat]] so a pole lands on the grid's edge. */
  def mercator(lat: Double, lon: Double): (Double, Double) =
    val l = math.toRadians(math.max(-MaxLat, math.min(MaxLat, lat)))
    ((lon + 180) / 360, (1 - math.log(math.tan(l) + 1 / math.cos(l)) / math.Pi) / 2)

  /** Pure: the latitude of the unit square's row `v` (0 north, 1 south). */
  def latOf(v: Double): Double = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * v))))

/** How a layer's items become a tile's features ([[com.histmersion.api.TileLayer]]): below `maxZoom` a
  * tile holding more than `maxPoints` items counts them by square cells of `cellUnits` tile units, one
  * feature a cell; at `maxZoom` (the deepest tile cut — deeper views draw it larger) every item is one. */
final case class TileGrid private (maxZoom: Int, maxPoints: Int, cellUnits: Int):
  /** The most items `c` keeps as features of their own before it groups them by cell. */
  def pointsAt(c: TileCoord): Int = if c.z >= maxZoom then Int.MaxValue else maxPoints

object TileGrid:
  /** A tile is 512 CSS pixels a side where MapLibre draws it at its own zoom. */
  val TilePx: Int = 512

  /** The grid of `ui.map-tiles`: tiles to `maxZoom`, groups above `maxPoints` items, cells of `cellPx`
    * pixels (each held to at least 1 and at most a tile). */
  def of(maxZoom: Int, maxPoints: Int, cellPx: Int): TileGrid =
    val px = cellPx.max(1).min(TilePx)
    new TileGrid(maxZoom.max(0).min(22), maxPoints.max(1), TileCoord.Extent * px / TilePx)

/** A tile feature's property value, as the vector tile format types it. */
enum TileValue:
  case Text(value: String)
  case Whole(value: Long)
  case Real(value: Double)
  case Flag(value: Boolean)

/** One POINT feature of a vector tile: its place in tile units and its properties, in order. */
final case class TilePoint(x: Int, y: Int, props: Seq[(String, TileValue)])

/** Writes Mapbox vector tiles (version 2, https://github.com/mapbox/vector-tile-spec) of point layers.
  * Each key and value is stored once per layer. Pure. */
object VectorTileWriter:

  /** A tile of the `layers` given (name → its points); an empty layer is left out. */
  def tile(layers: Seq[(String, Seq[TilePoint])]): Array[Byte] =
    val out = ByteArrayOutputStream()
    layers.filter(_._2.nonEmpty).foreach((name, points) => message(out, 3, layer(name, points)))
    out.toByteArray

  /** One layer's message. */
  def layer(name: String, points: Seq[TilePoint]): Array[Byte] =
    val keys   = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    val values = scala.collection.mutable.LinkedHashMap.empty[TileValue, Int]
    val out = ByteArrayOutputStream()
    varintField(out, 15, 2)
    message(out, 1, name.getBytes(StandardCharsets.UTF_8))
    points.foreach { p =>
      val tags = ByteArrayOutputStream()
      p.props.foreach { (k, v) =>
        varint(tags, keys.getOrElseUpdate(k, keys.size).toLong)
        varint(tags, values.getOrElseUpdate(v, values.size).toLong)
      }
      val geometry = ByteArrayOutputStream()
      // one MoveTo of one point, at its zig-zagged place
      varint(geometry, 9)
      varint(geometry, zigzag(p.x.toLong))
      varint(geometry, zigzag(p.y.toLong))
      val feature = ByteArrayOutputStream()
      message(feature, 2, tags.toByteArray)
      varintField(feature, 3, 1)
      message(feature, 4, geometry.toByteArray)
      message(out, 2, feature.toByteArray)
    }
    keys.keys.foreach(k => message(out, 3, k.getBytes(StandardCharsets.UTF_8)))
    values.keys.foreach(v => message(out, 4, value(v)))
    varintField(out, 5, TileCoord.Extent.toLong)
    out.toByteArray

  private def value(v: TileValue): Array[Byte] =
    val out = ByteArrayOutputStream()
    v match
      case TileValue.Text(s)  => message(out, 1, s.getBytes(StandardCharsets.UTF_8))
      case TileValue.Real(d)  =>
        varint(out, (3L << 3) | 1)
        val bits = java.lang.Double.doubleToLongBits(d)
        (0 until 8).foreach(i => out.write(((bits >>> (8 * i)) & 0xff).toInt))
      case TileValue.Whole(n) => varintField(out, 6, zigzag(n))
      case TileValue.Flag(b)  => varintField(out, 7, if b then 1L else 0L)
    out.toByteArray

  /** Protobuf's zig-zag encoding of a signed number. Pure. */
  def zigzag(n: Long): Long = (n << 1) ^ (n >> 63)

  private def message(out: ByteArrayOutputStream, number: Int, payload: Array[Byte]): Unit =
    varint(out, (number.toLong << 3) | 2)
    varint(out, payload.length.toLong)
    out.write(payload)

  private def varintField(out: ByteArrayOutputStream, number: Int, v: Long): Unit =
    varint(out, number.toLong << 3)
    varint(out, v)

  private def varint(out: ByteArrayOutputStream, v: Long): Unit =
    // `var`: the value's next seven bits
    var rest = v
    while (rest & ~0x7fL) != 0 do
      out.write(((rest & 0x7f) | 0x80).toInt)
      rest >>>= 7
    out.write(rest.toInt)

/** What a tile's query answers before the tile is written: ONE item (`key` the item's, `n` 1) or a CELL
  * of `n` items (`key` the cell's), at `x`/`y` in tile units (a cell's: its items' mean), the least and
  * greatest middle of their dated spans (`None`: none of them is dated at both ends), their bounding
  * box in degrees, and how many of them have no known date (`undated`: drawn only when a viewer asks
  * for them). */
final case class TileCell(key: String, x: Int, y: Int, n: Int, midLo: Option[Double], midHi: Option[Double],
    west: Double, south: Double, east: Double, north: Double, undated: Int = 0):
  def isItem: Boolean = n == 1

/** A layer's tile features, from its cells and the records of its items ([[com.histmersion.api.TileProps]]).
  * Pure. */
object TileFeatures:
  import com.histmersion.api.{EventGeo, MediaGeoHit, SiteTile, TileProps as P}

  private def text(k: String, v: String): Seq[(String, TileValue)] = Seq(k -> TileValue.Text(v))
  private def text(k: String, v: Option[String]): Seq[(String, TileValue)] = v.toSeq.map(s => k -> TileValue.Text(s))
  private def whole(k: String, v: Option[Long]): Seq[(String, TileValue)] = v.toSeq.map(n => k -> TileValue.Whole(n))

  /** How many of a cell's items (or whether an item) have no known date: left out when none. */
  private def undated(c: TileCell): Seq[(String, TileValue)] =
    Option.when(c.undated > 0)(P.Undated -> TileValue.Whole(c.undated.toLong)).toSeq

  /** A cell of several items: its key, count, dates' range and box. */
  def cell(c: TileCell): TilePoint =
    TilePoint(c.x, c.y, text(P.Key, c.key) ++ Seq(P.Count -> TileValue.Whole(c.n.toLong)) ++ undated(c) ++
      c.midLo.toSeq.map(v => P.MidLo -> TileValue.Real(v)) ++ c.midHi.toSeq.map(v => P.MidHi -> TileValue.Real(v)) ++
      Seq(P.West -> TileValue.Real(c.west), P.South -> TileValue.Real(c.south), P.East -> TileValue.Real(c.east),
        P.North -> TileValue.Real(c.north)))

  def site(c: TileCell, s: SiteTile): TilePoint =
    TilePoint(c.x, c.y, text(P.Key, c.key) ++ text(P.Id, s.id) ++ text(P.Label, s.label) ++ text(P.Kind, s.kind) ++
      text(P.SiteType, s.siteType) ++ text(P.Basis, s.basis) ++ whole(P.PeriodStart, s.periodStart) ++ whole(P.PeriodEnd, s.periodEnd) ++
      undated(c))

  def event(c: TileCell, e: EventGeo): TilePoint =
    TilePoint(c.x, c.y, text(P.Key, c.key) ++ text(P.Id, e.id) ++ text(P.Label, e.label) ++ text(P.Kind, e.kind) ++
      text(P.Scale, e.scale) ++ text(P.Historicity, e.historicity) ++ whole(P.NotBefore, e.timeSpan.notBefore) ++
      whole(P.BeginBy, e.timeSpan.beginBy) ++ whole(P.EndFrom, e.timeSpan.endFrom) ++ whole(P.NotAfter, e.timeSpan.notAfter) ++
      text(P.Anchor, e.anchor) ++ text(P.PlaceId, e.placeId) ++ text(P.PlaceLabel, e.placeLabel) ++ text(P.ParentId, e.parentId) ++
      text(P.Tier, e.tier) ++ text(P.Size, e.size) ++ text(P.Precision, e.precision) ++ text(P.Dating, e.dating) ++
      undated(c))

  def media(c: TileCell, h: MediaGeoHit): TilePoint =
    TilePoint(c.x, c.y, text(P.Key, c.key) ++ text(P.Uid, h.uid) ++ text(P.Citekey, h.citekey) ++ text(P.AssetName, h.assetName) ++
      text(P.Kind, h.kind) ++ whole(P.PeriodStart, h.periodStart) ++ whole(P.PeriodEnd, h.periodEnd) ++ text(P.Caption, h.caption) ++
      text(P.Provider, h.provider) ++ text(P.License, h.license) ++ text(P.Credit, h.credit) ++ text(P.ThumbUrl, h.thumbUrl) ++
      // its full size is this server's own `/media/{uid}/file`, which the map makes from the uid (not sent per picture)
      text(P.SourceUrl, h.sourceUrl) ++ text(P.LicenseUrl, h.licenseUrl) ++ text(P.Author, h.author))

  /** The features of a tile of zoom `zoom`: each cell of several as a cell, each item with its record
    * (`record` by the item's key; an item whose record is gone since its cell was read is left out),
    * every one with the tile's zoom. */
  def of[A](zoom: Int, cells: Seq[TileCell], record: String => Option[A], feature: (TileCell, A) => TilePoint): Seq[TilePoint] =
    val z = P.Zoom -> TileValue.Whole(zoom.toLong)
    cells.flatMap(c => if c.isItem then record(c.key).map(feature(c, _)) else Some(cell(c))).map(p => p.copy(props = z +: p.props))
