package com.histmersion
package util.basemap

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.storage.Store

import io.circe.Json

import scala.collection.mutable
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.library.Label
import io.github.iltotore.iron.constraint.any.Pure

/** The base maps' physical names (docs/research-terrain-basemap.md, "Labels"): Natural Earth's label layers — public
  * domain — read from their GeoJSON, cut to the region, and written as a small vector archive the page labels from
  * (`labels.pmtiles`, layers `physical` and `rivers`).
  *
  * Kept: mountain ranges, deserts, plateaus, plains, basins, valleys, deltas, peninsulas, isthmuses, coasts, wetlands,
  * regions (Natural Earth's "geoarea"), island groups and the larger islands, lakes (not reservoirs), peaks, passes,
  * depressions, capes, waterfalls, and the major rivers as lines (not canals). Never a settlement, a country or a road:
  * those files are not read. Seas, gulfs, straits and the smaller islands, lakes and rivers come with the vector layer
  * (Protomaps). Pure.
  */
object BasemapLabels:
  type Area = Area.T
  object Area extends Newtype[Double, Pure]
  type Buffer = Buffer.T
  object Buffer extends Newtype[Double, Pure]
  type FeatureClass = FeatureClass.T
  object FeatureClass extends Newtype[String, Pure]
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]
  type Language = Language.T
  object Language extends Newtype[String, Pure]
  type Margin = Margin.T
  object Margin extends Newtype[Double, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Problem = RangeHttp.Problem
  val Problem: RangeHttp.Problem.type = RangeHttp.Problem
  type Tolerance = Tolerance.T
  object Tolerance extends Newtype[Double, Pure]
  type WorldX = WorldX.T
  object WorldX extends Newtype[Double, Pure]
  type WorldY = WorldY.T
  object WorldY extends Newtype[Double, Pure]

  /** One name to place: a point (`line` empty) or a river's line (`line` its parts, lon/lat), shown from map zoom
    * `minZoom` (to `maxZoom`), with its names by language (`name`, `name:en`, …).
    */
  final case class Label(
      kind: Label.Kind,
      names: List[(Language, Name)],
      lon: Label.Lon,
      lat: Label.Lat,
      minZoom: Label.MinZoom,
      maxZoom: Option[Label.MaxZoom] = None,
      line: Vector[Vector[(Label.Lon, Label.Lat)]] = Vector.empty,
      elevation: Option[Label.Elevation] = None
  )

  object Label:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Lon = Lon.T
    object Lon extends Newtype[Double, Pure]
    type Lat = Lat.T
    object Lat extends Newtype[Double, Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends Newtype[Int, Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends Newtype[Int, Pure]
    type Elevation = Elevation.T
    object Elevation extends Newtype[Long, Pure]

  /** Natural Earth's feature classes, lower-cased → the `kind` a style selects; `None` = not kept. */
  def kindOf(featureClass: FeatureClass): Option[Label.Kind] =
    kindName(featureClass.value.trim.toLowerCase(java.util.Locale.ROOT)).map(Label.Kind(_))

  private def kindName(featureClass: String): Option[String] = featureClass match
    case "range/mtn"                   => Some("range")
    case "foothills"                   => Some("foothills")
    case "desert"                      => Some("desert")
    case "plateau"                     => Some("plateau")
    case "plain" | "lowland"           => Some("plain")
    case "basin"                       => Some("basin")
    case "depression"                  => Some("depression")
    case "valley" | "gorge"            => Some("valley")
    case "delta"                       => Some("delta")
    case "peninsula" | "pen/cape"      => Some("peninsula")
    case "isthmus"                     => Some("isthmus")
    case "coast"                       => Some("coast")
    case "wetlands"                    => Some("wetland")
    case "tundra" | "geoarea"          => Some("region")
    case "continent"                   => Some("continent")
    case "island group"                => Some("island_group")
    case "island"                      => Some("island")
    case "lake" | "alkaline lake"      => Some("lake")
    case "mountain" | "spot elevation" => Some("peak")
    case "pass"                        => Some("pass")
    case "cape"                        => Some("cape")
    case "waterfall"                   => Some("waterfall")
    case "river"                       => Some("river")
    case _                             => None // reservoirs, canals, lake centrelines, poles, …

  /** Natural Earth's `name_<code>` → our `name:<code>` (Protomaps' keys: Chinese as zh-Hans / zh-Hant). */
  private def langKey(code: String): String = code match
    case "zh"  => "zh-Hans"
    case "zht" => "zh-Hant"
    case c     => c

  /** A property by name, whatever its case (Natural Earth's polygon files use upper case). */
  private def prop(p: io.circe.JsonObject, k: String): Option[Json] =
    p(k).orElse(p(k.toUpperCase(java.util.Locale.ROOT))).filterNot(_.isNull)

  private def text(p: io.circe.JsonObject, k: String): Option[String] =
    prop(p, k).flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)

  private def number(p: io.circe.JsonObject, k: String): Option[Double] =
    prop(p, k).flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))

  /** The names a feature carries: `name` (Natural Earth's own, mostly English or local), `name:en`, and `name:<code>`
    * for each `name_<code>`.
    */
  def names(p: io.circe.JsonObject): List[(Language, Name)] =
    val own = text(p, "name").toList.map(n => Language("name") -> Name(n))
    val langs = p.keys.toList
      .map(_.toLowerCase(java.util.Locale.ROOT))
      .distinct
      .collect {
        case k if k.startsWith("name_") && k.drop(5).matches("[a-z]{2,3}") => k
      }
      .sorted
      .flatMap(k => text(p, k).map(v => Language(s"name:${langKey(k.drop(5))}") -> Name(v)))
    own ++ langs

  /** The map zoom a feature is labelled from: Natural Earth's `min_label`, else `min_zoom`, else its `scalerank`; a
    * fraction rounds to the nearer zoom. Pure.
    */
  def minZoomOf(p: io.circe.JsonObject): Label.MinZoom =
    Label.MinZoom(
      number(p, "min_label")
        .orElse(number(p, "min_zoom"))
        .orElse(number(p, "scalerank").map(_ + 1))
        .map(z => math.max(0, math.round(z).toInt))
        .getOrElse(6)
    )

  // ── geometry ───────────────────────────────────────────────────────────────────────────────────

  private def coords(j: Json): Option[(Label.Lon, Label.Lat)] =
    j.asArray.flatMap(a =>
      for x <- a.lift(0).flatMap(_.asNumber); y <- a.lift(1).flatMap(_.asNumber)
      yield (Label.Lon(x.toDouble), Label.Lat(y.toDouble))
    )

  private def ring(j: Json): Vector[(Label.Lon, Label.Lat)] = j.asArray.map(_.flatMap(coords)).getOrElse(Vector.empty)

  private def plain(r: Vector[(Label.Lon, Label.Lat)]): Vector[(Double, Double)] = r.map((x, y) => (x.value, y.value))

  /** The signed area of a ring (shoelace), in square degrees. Pure. */
  def area(r: Vector[(Label.Lon, Label.Lat)]): Area = Area(areaOf(plain(r)))

  private def areaOf(r: Vector[(Double, Double)]): Double =
    if r.size < 3 then 0.0
    else
      r.indices.map { i =>
        val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size); x0 * y1 - x1 * y0
      }.sum / 2

  /** Is `p` inside ring `r` (even-odd)? Pure. */
  def inside(r: Vector[(Label.Lon, Label.Lat)], p: (Label.Lon, Label.Lat)): Boolean =
    insideOf(plain(r), (p._1.value, p._2.value))

  private def insideOf(r: Vector[(Double, Double)], p: (Double, Double)): Boolean =
    val (px, py) = p
    r.indices.count { i =>
      val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
      (y0 > py) != (y1 > py) && px < (x1 - x0) * (py - y0) / (y1 - y0) + x0
    } % 2 == 1

  /** Where a polygon's name goes: inside its largest part, at the middle of the widest stretch of the horizontal line
    * through that part's centre of area (a centroid falls outside a crescent-shaped range; this does not). Holes are
    * ignored — a name may sit on a lake in a range. Pure.
    */
  def labelPoint(polygons: Vector[Vector[(Label.Lon, Label.Lat)]]): Option[(Label.Lon, Label.Lat)] =
    labelPointOf(polygons.map(plain)).map((x, y) => (Label.Lon(x), Label.Lat(y)))

  private def labelPointOf(polygons: Vector[Vector[(Double, Double)]]): Option[(Double, Double)] =
    polygons.filter(_.size >= 3).maxByOption(r => math.abs(areaOf(r))).flatMap { r =>
      val a = areaOf(r)
      val (cx, cy) =
        if a == 0 then (r.map(_._1).sum / r.size, r.map(_._2).sum / r.size)
        else
          val parts = r.indices.map { i =>
            val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
            val c = x0 * y1 - x1 * y0
            ((x0 + x1) * c, (y0 + y1) * c)
          }
          (parts.map(_._1).sum / (6 * a), parts.map(_._2).sum / (6 * a))
      if insideOf(r, (cx, cy)) then Some((cx, cy))
      else
        // the crossings of the line y = cy with the ring, sorted: inside between pairs
        val xs = r.indices.flatMap { i =>
          val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
          Option.when((y0 > cy) != (y1 > cy))((x1 - x0) * (cy - y0) / (y1 - y0) + x0)
        }.sorted
        xs.grouped(2)
          .collect { case Seq(a, b) => (a, b) }
          .maxByOption((a, b) => b - a)
          .map((a, b) => ((a + b) / 2, cy))
          .orElse(r.headOption)
    }

  /** One GeoJSON feature as a label, when its class is kept and it has a name. */
  def label(f: Json): Option[Label] =
    for
      p <- f.hcursor.downField("properties").focus.flatMap(_.asObject)
      kind <- text(p, "featurecla").flatMap(c => kindOf(FeatureClass(c)))
      ns = names(p)
      if ns.exists(_._1.value == "name") || ns.exists(_._1.value == "name:en")
      g <- f.hcursor.downField("geometry").focus.flatMap(_.asObject)
      typ <- g("type").flatMap(_.asString)
      cs <- g("coordinates")
      placed <- typ match
        case "Point"   => coords(cs).map(c => (c, Vector.empty[Vector[(Label.Lon, Label.Lat)]]))
        case "Polygon" => labelPoint(cs.asArray.flatMap(_.headOption).map(ring).toVector).map(c => (c, Vector.empty))
        case "MultiPolygon" =>
          labelPoint(cs.asArray.map(_.flatMap(_.asArray.flatMap(_.headOption).map(ring))).getOrElse(Vector.empty))
            .map(c => (c, Vector.empty))
        case "LineString"      => Option(ring(cs)).filter(_.size >= 2).map(l => (l(l.size / 2), Vector(l)))
        case "MultiLineString" =>
          Option(cs.asArray.map(_.map(ring).filter(_.size >= 2)).getOrElse(Vector.empty))
            .filter(_.nonEmpty)
            .map(ls => (ls.maxBy(_.size).apply(ls.maxBy(_.size).size / 2), ls))
        case _ => None
    yield
      val ((lon, lat), line) = placed
      Label(
        kind,
        ns,
        lon,
        lat,
        minZoomOf(p),
        number(p, "max_label").map(z => Label.MaxZoom(math.round(z).toInt)),
        if kind.value == "river" then line else Vector.empty,
        number(p, "elevation").filter(_ => kind.value == "peak").map(e => Label.Elevation(e.round))
      )

  /** Every kept label of one GeoJSON FeatureCollection. */
  def parse(file: FileName, doc: Json): Either[Problem, List[Label]] =
    doc.hcursor
      .downField("features")
      .focus
      .flatMap(_.asArray)
      .toRight(Problem(s"$file is not a GeoJSON FeatureCollection"))
      .map(_.toList.flatMap(label))

  /** Does the label touch `b` (grown by `margin` degrees)? Pure. */
  def within(l: Label, b: BBox, margin: Margin = Margin(1.0)): Boolean =
    def in(lon: Double, lat: Double) =
      lon >= b.minLon.value - margin.value && lon <= b.maxLon.value + margin.value &&
        lat >= b.minLat.value - margin.value && lat <= b.maxLat.value + margin.value
    if l.line.isEmpty then in(l.lon.value, l.lat.value)
    else l.line.exists(_.exists((lon, lat) => in(lon.value, lat.value)))

  // ── tiles ──────────────────────────────────────────────────────────────────────────────────────

  private val Extent = 4096

  /** World position of lon/lat in units of 1/4096 of a zoom-`z` tile. Pure. */
  def world(lon: Label.Lon, lat: Label.Lat, z: PmTiles.Zoom): (WorldX, WorldY) =
    val n = (1L << z.value).toDouble * Extent
    val r = math.toRadians(lat.value.max(-Basemap.MaxLat).min(Basemap.MaxLat))
    (
      WorldX((lon.value + 180.0) / 360.0 * n),
      WorldY((1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n)
    )

  /** Douglas–Peucker: the points of `l` that keep it within `tolerance` of the original. Pure. */
  def simplify(l: Vector[(WorldX, WorldY)], tolerance: Tolerance): Vector[(WorldX, WorldY)] =
    if l.size <= 2 then l
    else
      val pts = l.map((x, y) => (x.value, y.value))
      val keep = Array.fill(l.size)(false)
      keep(0) = true; keep(l.size - 1) = true
      val stack = mutable.Stack((0, l.size - 1)) // ranges still to split
      while stack.nonEmpty do
        val (a, b) = stack.pop()
        val (ax, ay) = pts(a); val (bx, by) = pts(b)
        val dx = bx - ax; val dy = by - ay
        val len = math.hypot(dx, dy)
        var far = -1 // the farthest point of the range and its distance
        var dist = 0.0
        var i = a + 1
        while i < b do
          val (px, py) = pts(i)
          val d =
            if len == 0 then math.hypot(px - ax, py - ay) else math.abs(dy * px - dx * py + bx * ay - by * ax) / len
          if d > dist then { dist = d; far = i }
          i += 1
        if far >= 0 && dist > tolerance.value then
          keep(far) = true
          stack.push((a, far)); stack.push((far, b))
      l.indices.filter(keep).map(l).toVector

  /** The runs of `l` (world units) whose segments touch tile `(x, y)` grown by `buffer`, relative to the tile's corner
    * and rounded. Pure.
    */
  def clip(l: Vector[(WorldX, WorldY)], x: PmTiles.TileColumn, y: PmTiles.TileRow, buffer: Buffer): Vector[Vector[(Mvt.PointX, Mvt.PointY)]] =
    val tx = x.value
    val ty = y.value
    val pts = l.map((wx, wy) => (wx.value, wy.value))
    val x0 = tx * Extent - buffer.value; val y0 = ty * Extent - buffer.value
    val x1 = (tx + 1) * Extent + buffer.value; val y1 = (ty + 1) * Extent + buffer.value
    def touches(a: (Double, Double), b: (Double, Double)) =
      math.max(a._1, b._1) >= x0 && math.min(a._1, b._1) <= x1 && math.max(a._2, b._2) >= y0 && math.min(
        a._2,
        b._2
      ) <= y1
    val runs = Vector.newBuilder[Vector[(Int, Int)]]
    val run = Vector.newBuilder[(Double, Double)]
    var open = false // a run is being built
    pts.indices.dropRight(1).foreach { i =>
      if touches(pts(i), pts(i + 1)) then
        if !open then { run.clear(); run += pts(i); open = true }
        run += pts(i + 1)
      else if open then { runs += rel(run.result(), tx, ty); open = false }
    }
    if open then runs += rel(run.result(), tx, ty)
    runs.result().filter(_.size >= 2).map(_.map((a, b) => (Mvt.PointX(a), Mvt.PointY(b))))

  private def rel(r: Vector[(Double, Double)], x: Long, y: Long): Vector[(Int, Int)] =
    r.map((wx, wy) => (math.round(wx - x * Extent).toInt, math.round(wy - y * Extent).toInt))
      .foldLeft(Vector.empty[(Int, Int)])((acc, p) => if acc.lastOption.contains(p) then acc else acc :+ p)

  /** Is the label drawn by the page from data tile `z` (which serves map zoom z + 1; the deepest, every zoom past it)?
    * Its own `min_zoom` filters it further in the page. Pure.
    */
  def inTile(l: Label, z: PmTiles.Zoom, maxZoom: PmTiles.Zoom): Boolean =
    (z.value == maxZoom.value || l.minZoom.value <= z.value + 1) && l.maxZoom.forall(_.value >= z.value + 1)

  private def props(l: Label): List[(Mvt.PropKey, String | Long)] =
    (Mvt.PropKey("kind"), l.kind.value: String | Long) ::
      (Mvt.PropKey("min_zoom"), l.minZoom.value.toLong: String | Long) ::
      (l.names.map((k, n) => (Mvt.PropKey(k.value), n.value: String | Long)) ++
        l.elevation.map(e => (Mvt.PropKey("elevation"), e.value: String | Long)).toList)

  /** The archive's tiles, by tile id: points in the tile they fall in, rivers clipped to each tile they cross
    * (simplified to about a pixel of the zoom), zooms 0…`maxZoom`, gzip-compressed MVT. Pure.
    */
  def tiles(labels: List[Label], maxZoom: PmTiles.Zoom): Vector[(PmTiles.Entry.TileId, PmTiles.TileBytes)] =
    type Features = mutable.ListBuffer[(Mvt.GeomType, Vector[Mvt.GeometryCommand], List[(Mvt.PropKey, String | Long)])]
    // per tile id, its points and its lines, filled zoom by zoom
    val out = mutable.TreeMap.empty[Long, (Features, Features)]
    def slot(z: PmTiles.Zoom, x: Long, y: Long) =
      out.getOrElseUpdate(
        PmTiles.tileId(z, PmTiles.TileColumn(x), PmTiles.TileRow(y)).value,
        (mutable.ListBuffer.empty, mutable.ListBuffer.empty)
      )
    for zoom <- 0 to maxZoom.value do
      val z = PmTiles.Zoom(zoom)
      val n = 1L << zoom
      labels.filter(inTile(_, z, maxZoom)).foreach { l =>
        if l.line.isEmpty then
          val (wx, wy) = world(l.lon, l.lat, z)
          val tx = math.floor(wx.value / Extent).toLong.max(0).min(n - 1)
          val ty = math.floor(wy.value / Extent).toLong.max(0).min(n - 1)
          slot(z, tx, ty)._1 += ((
            Mvt.GeomType(1),
            Mvt.pointGeometry(
              Mvt.PointX(math.round(wx.value - tx * Extent).toInt),
              Mvt.PointY(math.round(wy.value - ty * Extent).toInt)
            ),
            props(l)
          ))
        else
          val parts = l.line.map(p => simplify(p.map((lo, la) => world(lo, la, z)), Tolerance(Extent / 256.0))).filter(_.size >= 2)
          val all = parts.flatten
          if all.nonEmpty then
            val txs = (math
              .floor(all.map(_._1.value).min / Extent)
              .toLong
              .max(0) to math.floor(all.map(_._1.value).max / Extent).toLong.min(n - 1))
            val tys = (math
              .floor(all.map(_._2.value).min / Extent)
              .toLong
              .max(0) to math.floor(all.map(_._2.value).max / Extent).toLong.min(n - 1))
            for tx <- txs; ty <- tys do
              val runs = parts.flatMap(clip(_, PmTiles.TileColumn(tx), PmTiles.TileRow(ty), Buffer(64)))
              if runs.nonEmpty then slot(z, tx, ty)._2 += ((Mvt.GeomType(2), Mvt.lineGeometry(runs), props(l)))
      }
    out.toVector.map { case (id, (points, lines)) =>
      val layers = List(
        Option.when(points.nonEmpty)(Mvt.typedLayer(Mvt.LayerName("physical"), points.toList)),
        Option.when(lines.nonEmpty)(Mvt.typedLayer(Mvt.LayerName("rivers"), lines.toList))
      ).flatten
      PmTiles.Entry.TileId(id) -> PmTiles.TileBytes(PmTiles.gzip(Mvt.tile(layers)).value)
    }

  /** Write `labels` as the archive `key` of `store` (zooms 0…`maxZoom`, bounds `b`); answers its tiles and bytes. */
  def write(store: Store, key: Store.Key, labels: List[Label], b: BBox, maxZoom: PmTiles.Zoom, metadata: Json): (BasemapFetch.TileCount, ByteCount) =
    val ts = tiles(labels, maxZoom)
    val w = PmTilesWriter(store, key)
    val header = PmTiles.Header(
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      PmTiles.Header.AddressedTiles(0L),
      PmTiles.Header.TileEntries(0L),
      PmTiles.Header.TileContents(0L),
      PmTiles.Header.Clustered(true),
      PmTiles.Compression.Gzip,
      PmTiles.Compression.Gzip,
      PmTiles.TileType.Mvt,
      PmTiles.Zoom(0),
      maxZoom,
      PmTiles.Header.MinLon(b.minLon.value),
      PmTiles.Header.MinLat(b.minLat.value),
      PmTiles.Header.MaxLon(b.maxLon.value),
      PmTiles.Header.MaxLat(b.maxLat.value),
      PmTiles.Zoom(math.min(maxZoom.value, 5)),
      PmTiles.Header.CenterLon((b.minLon.value + b.maxLon.value) / 2),
      PmTiles.Header.CenterLat((b.minLat.value + b.maxLat.value) / 2)
    )
    try
      ts.foreach((id, t) => w.add(id, t))
      (BasemapFetch.TileCount(ts.size.toLong), w.finish(header, metadata))
    catch case e: Exception => { w.abort(); throw e }
