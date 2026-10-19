package com.histmersion.server.basemap

import com.histmersion.core.*

import io.circe.Json

import scala.collection.mutable

/** The terrain map's physical names (docs/research-terrain-basemap.md, "Labels"): Natural Earth's
  * label layers — public domain — read from their GeoJSON, cut to the region, and written as a small
  * vector archive the page labels from (`labels.pmtiles`, layers `physical` and `rivers`).
  *
  * Kept: mountain ranges, deserts, plateaus, plains, basins, valleys, deltas, peninsulas, isthmuses,
  * coasts, wetlands, regions (Natural Earth's "geoarea"), island groups and the larger islands, lakes
  * (not reservoirs), peaks, passes, depressions, capes, waterfalls, and the major rivers as lines (not
  * canals). Never a settlement, a country or a road: those files are not read. Seas, gulfs, straits and
  * the smaller islands, lakes and rivers come with the vector layer (Protomaps). Pure. */
object BasemapLabels:

  /** One name to place: a point (`line` empty) or a river's line (`line` its parts, lon/lat), shown
    * from map zoom `minZoom` (to `maxZoom`), with its names by language (`name`, `name:en`, …). */
  final case class Label(kind: String, names: List[(String, String)], lon: Double, lat: Double, minZoom: Int,
      maxZoom: Option[Int] = None, line: Vector[Vector[(Double, Double)]] = Vector.empty, elevation: Option[Long] = None)

  /** Natural Earth's feature classes, lower-cased → the `kind` a style selects; `None` = not kept. */
  def kindOf(featureClass: String): Option[String] = featureClass.trim.toLowerCase(java.util.Locale.ROOT) match
    case "range/mtn"                         => Some("range")
    case "foothills"                         => Some("foothills")
    case "desert"                            => Some("desert")
    case "plateau"                           => Some("plateau")
    case "plain" | "lowland"                 => Some("plain")
    case "basin"                             => Some("basin")
    case "depression"                        => Some("depression")
    case "valley" | "gorge"                  => Some("valley")
    case "delta"                             => Some("delta")
    case "peninsula" | "pen/cape"            => Some("peninsula")
    case "isthmus"                           => Some("isthmus")
    case "coast"                             => Some("coast")
    case "wetlands"                          => Some("wetland")
    case "tundra" | "geoarea"                => Some("region")
    case "continent"                         => Some("continent")
    case "island group"                      => Some("island_group")
    case "island"                            => Some("island")
    case "lake" | "alkaline lake"            => Some("lake")
    case "mountain" | "spot elevation"       => Some("peak")
    case "pass"                              => Some("pass")
    case "cape"                              => Some("cape")
    case "waterfall"                         => Some("waterfall")
    case "river"                             => Some("river")
    case _                                   => None   // reservoirs, canals, lake centrelines, poles, …

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

  /** The names a feature carries: `name` (Natural Earth's own, mostly English or local), `name:en`,
    * and `name:<code>` for each `name_<code>`. */
  def names(p: io.circe.JsonObject): List[(String, String)] =
    val own = text(p, "name").toList.map("name" -> _)
    val langs = p.keys.toList.map(_.toLowerCase(java.util.Locale.ROOT)).distinct.collect {
      case k if k.startsWith("name_") && k.drop(5).matches("[a-z]{2,3}") => k
    }.sorted.flatMap(k => text(p, k).map(v => s"name:${langKey(k.drop(5))}" -> v))
    own ++ langs

  /** The map zoom a feature is labelled from: Natural Earth's `min_label`, else `min_zoom`, else its
    * `scalerank`; a fraction rounds to the nearer zoom. Pure. */
  def minZoomOf(p: io.circe.JsonObject): Int =
    number(p, "min_label").orElse(number(p, "min_zoom")).orElse(number(p, "scalerank").map(_ + 1))
      .map(z => math.max(0, math.round(z).toInt)).getOrElse(6)

  // ── geometry ───────────────────────────────────────────────────────────────────────────────────

  private def coords(j: Json): Option[(Double, Double)] =
    j.asArray.flatMap(a => for x <- a.lift(0).flatMap(_.asNumber); y <- a.lift(1).flatMap(_.asNumber) yield (x.toDouble, y.toDouble))

  private def ring(j: Json): Vector[(Double, Double)] = j.asArray.map(_.flatMap(coords)).getOrElse(Vector.empty)

  /** The signed area of a ring (shoelace), in square degrees. Pure. */
  def area(r: Vector[(Double, Double)]): Double =
    if r.size < 3 then 0.0
    else r.indices.map(i => { val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size); x0 * y1 - x1 * y0 }).sum / 2

  /** Is `p` inside ring `r` (even-odd)? Pure. */
  def inside(r: Vector[(Double, Double)], p: (Double, Double)): Boolean =
    val (px, py) = p
    r.indices.count { i =>
      val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
      (y0 > py) != (y1 > py) && px < (x1 - x0) * (py - y0) / (y1 - y0) + x0
    } % 2 == 1

  /** Where a polygon's name goes: inside its largest part, at the middle of the widest stretch of the
    * horizontal line through that part's centre of area (a centroid falls outside a crescent-shaped
    * range; this does not). Holes are ignored — a name may sit on a lake in a range. Pure. */
  def labelPoint(polygons: Vector[Vector[(Double, Double)]]): Option[(Double, Double)] =
    polygons.filter(_.size >= 3).maxByOption(r => math.abs(area(r))).flatMap { r =>
      val a = area(r)
      val (cx, cy) =
        if a == 0 then (r.map(_._1).sum / r.size, r.map(_._2).sum / r.size)
        else
          val parts = r.indices.map { i =>
            val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
            val c = x0 * y1 - x1 * y0
            ((x0 + x1) * c, (y0 + y1) * c)
          }
          (parts.map(_._1).sum / (6 * a), parts.map(_._2).sum / (6 * a))
      if inside(r, (cx, cy)) then Some((cx, cy))
      else
        // the crossings of the line y = cy with the ring, sorted: inside between pairs
        val xs = r.indices.flatMap { i =>
          val (x0, y0) = r(i); val (x1, y1) = r((i + 1) % r.size)
          Option.when((y0 > cy) != (y1 > cy))((x1 - x0) * (cy - y0) / (y1 - y0) + x0)
        }.sorted
        xs.grouped(2).collect { case Seq(a, b) => (a, b) }.maxByOption((a, b) => b - a).map((a, b) => ((a + b) / 2, cy))
          .orElse(r.headOption)
    }

  /** One GeoJSON feature as a label, when its class is kept and it has a name. */
  def label(f: Json): Option[Label] =
    for
      p     <- f.hcursor.downField("properties").focus.flatMap(_.asObject)
      kind  <- text(p, "featurecla").flatMap(kindOf)
      ns     = names(p)
      if ns.exists(_._1 == "name") || ns.exists(_._1 == "name:en")
      g     <- f.hcursor.downField("geometry").focus.flatMap(_.asObject)
      typ   <- g("type").flatMap(_.asString)
      cs    <- g("coordinates")
      placed <- typ match
        case "Point" => coords(cs).map(c => (c, Vector.empty[Vector[(Double, Double)]]))
        case "Polygon" => labelPoint(cs.asArray.flatMap(_.headOption).map(ring).toVector).map(c => (c, Vector.empty))
        case "MultiPolygon" =>
          labelPoint(cs.asArray.map(_.flatMap(_.asArray.flatMap(_.headOption).map(ring))).getOrElse(Vector.empty))
            .map(c => (c, Vector.empty))
        case "LineString" => Option(ring(cs)).filter(_.size >= 2).map(l => (l(l.size / 2), Vector(l)))
        case "MultiLineString" =>
          Option(cs.asArray.map(_.map(ring).filter(_.size >= 2)).getOrElse(Vector.empty)).filter(_.nonEmpty)
            .map(ls => (ls.maxBy(_.size).apply(ls.maxBy(_.size).size / 2), ls))
        case _ => None
    yield
      val ((lon, lat), line) = placed
      Label(kind, ns, lon, lat, minZoomOf(p), number(p, "max_label").map(z => math.round(z).toInt),
        if kind == "river" then line else Vector.empty,
        number(p, "elevation").filter(_ => kind == "peak").map(_.round))

  /** Every kept label of one GeoJSON FeatureCollection. */
  def parse(file: String, doc: Json): Either[String, List[Label]] =
    doc.hcursor.downField("features").focus.flatMap(_.asArray)
      .toRight(s"$file is not a GeoJSON FeatureCollection")
      .map(_.toList.flatMap(label))

  /** Does the label touch `b` (grown by `margin` degrees)? Pure. */
  def within(l: Label, b: BBox, margin: Double = 1.0): Boolean =
    def in(lon: Double, lat: Double) =
      lon >= b.minLon - margin && lon <= b.maxLon + margin && lat >= b.minLat - margin && lat <= b.maxLat + margin
    if l.line.isEmpty then in(l.lon, l.lat) else l.line.exists(_.exists(in.tupled))

  // ── tiles ──────────────────────────────────────────────────────────────────────────────────────

  private val Extent = 4096

  /** World position of lon/lat in units of 1/4096 of a zoom-`z` tile. Pure. */
  def world(lon: Double, lat: Double, z: Int): (Double, Double) =
    val n   = (1L << z).toDouble * Extent
    val r   = math.toRadians(lat.max(-Basemap.MaxLat).min(Basemap.MaxLat))
    ((lon + 180.0) / 360.0 * n, (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n)

  /** Douglas–Peucker: the points of `l` that keep it within `tolerance` of the original. Pure. */
  def simplify(l: Vector[(Double, Double)], tolerance: Double): Vector[(Double, Double)] =
    if l.size <= 2 then l
    else
      val keep = Array.fill(l.size)(false)
      keep(0) = true; keep(l.size - 1) = true
      val stack = mutable.Stack((0, l.size - 1)) // ranges still to split
      while stack.nonEmpty do
        val (a, b) = stack.pop()
        val (ax, ay) = l(a); val (bx, by) = l(b)
        val dx = bx - ax; val dy = by - ay
        val len = math.hypot(dx, dy)
        var far = -1      // the farthest point of the range and its distance
        var dist = 0.0
        var i = a + 1
        while i < b do
          val (px, py) = l(i)
          val d = if len == 0 then math.hypot(px - ax, py - ay) else math.abs(dy * px - dx * py + bx * ay - by * ax) / len
          if d > dist then { dist = d; far = i }
          i += 1
        if far >= 0 && dist > tolerance then
          keep(far) = true
          stack.push((a, far)); stack.push((far, b))
      l.indices.filter(keep).map(l).toVector

  /** The runs of `l` (world units) whose segments touch tile `(x, y)` grown by `buffer`, relative to
    * the tile's corner and rounded. Pure. */
  def clip(l: Vector[(Double, Double)], x: Long, y: Long, buffer: Double): Vector[Vector[(Int, Int)]] =
    val x0 = x * Extent - buffer; val y0 = y * Extent - buffer
    val x1 = (x + 1) * Extent + buffer; val y1 = (y + 1) * Extent + buffer
    def touches(a: (Double, Double), b: (Double, Double)) =
      math.max(a._1, b._1) >= x0 && math.min(a._1, b._1) <= x1 && math.max(a._2, b._2) >= y0 && math.min(a._2, b._2) <= y1
    val runs = Vector.newBuilder[Vector[(Int, Int)]]
    val run  = Vector.newBuilder[(Double, Double)]
    var open = false // a run is being built
    l.indices.dropRight(1).foreach { i =>
      if touches(l(i), l(i + 1)) then
        if !open then { run.clear(); run += l(i); open = true }
        run += l(i + 1)
      else if open then { runs += rel(run.result(), x, y); open = false }
    }
    if open then runs += rel(run.result(), x, y)
    runs.result().filter(_.size >= 2)

  private def rel(r: Vector[(Double, Double)], x: Long, y: Long): Vector[(Int, Int)] =
    r.map((wx, wy) => (math.round(wx - x * Extent).toInt, math.round(wy - y * Extent).toInt))
      .foldLeft(Vector.empty[(Int, Int)])((acc, p) => if acc.lastOption.contains(p) then acc else acc :+ p)

  /** Is the label drawn by the page from data tile `z` (which serves map zoom z + 1; the deepest, every
    * zoom past it)? Its own `min_zoom` filters it further in the page. Pure. */
  def inTile(l: Label, z: Int, maxZoom: Int): Boolean =
    (z == maxZoom || l.minZoom <= z + 1) && l.maxZoom.forall(_ >= z + 1)

  private def props(l: Label): List[(String, String | Long)] =
    ("kind" -> l.kind) :: ("min_zoom" -> l.minZoom.toLong) :: l.names ++ l.elevation.map("elevation" -> _).toList

  /** The archive's tiles, by tile id: points in the tile they fall in, rivers clipped to each tile they
    * cross (simplified to about a pixel of the zoom), zooms 0…`maxZoom`, gzip-compressed MVT. Pure. */
  def tiles(labels: List[Label], maxZoom: Int): Vector[(Long, Array[Byte])] =
    type Features = mutable.ListBuffer[(Int, Vector[Int], List[(String, String | Long)])]
    // per tile id, its points and its lines, filled zoom by zoom
    val out = mutable.TreeMap.empty[Long, (Features, Features)]
    def slot(z: Int, x: Long, y: Long) =
      out.getOrElseUpdate(PmTiles.tileId(z, x, y), (mutable.ListBuffer.empty, mutable.ListBuffer.empty))
    for z <- 0 to maxZoom do
      val n = 1L << z
      labels.filter(inTile(_, z, maxZoom)).foreach { l =>
        if l.line.isEmpty then
          val (wx, wy) = world(l.lon, l.lat, z)
          val tx = math.floor(wx / Extent).toLong.max(0).min(n - 1)
          val ty = math.floor(wy / Extent).toLong.max(0).min(n - 1)
          slot(z, tx, ty)._1 += ((1, Mvt.pointGeometry(math.round(wx - tx * Extent).toInt, math.round(wy - ty * Extent).toInt), props(l)))
        else
          val parts = l.line.map(p => simplify(p.map(world(_, _, z)), Extent / 256.0)).filter(_.size >= 2)
          val all = parts.flatten
          if all.nonEmpty then
            val txs = (math.floor(all.map(_._1).min / Extent).toLong.max(0) to math.floor(all.map(_._1).max / Extent).toLong.min(n - 1))
            val tys = (math.floor(all.map(_._2).min / Extent).toLong.max(0) to math.floor(all.map(_._2).max / Extent).toLong.min(n - 1))
            for tx <- txs; ty <- tys do
              val runs = parts.flatMap(clip(_, tx, ty, 64))
              if runs.nonEmpty then slot(z, tx, ty)._2 += ((2, Mvt.lineGeometry(runs), props(l)))
      }
    out.toVector.map { case (id, (points, lines)) =>
      val layers = List(Option.when(points.nonEmpty)(Mvt.typedLayer("physical", points.toList)),
        Option.when(lines.nonEmpty)(Mvt.typedLayer("rivers", lines.toList))).flatten
      id -> PmTiles.gzip(Mvt.tile(layers))
    }

  /** Write `labels` as the archive `out` (zooms 0…`maxZoom`, bounds `b`); answers its tiles and bytes. */
  def write(out: java.nio.file.Path, labels: List[Label], b: BBox, maxZoom: Int, metadata: Json): (Int, Long) =
    val ts = tiles(labels, maxZoom)
    val w  = PmTilesWriter(out)
    val header = PmTiles.Header(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, PmTiles.Compression.Gzip, PmTiles.Compression.Gzip,
      PmTiles.TileType.Mvt, 0, maxZoom, b.minLon, b.minLat, b.maxLon, b.maxLat, math.min(maxZoom, 5),
      (b.minLon + b.maxLon) / 2, (b.minLat + b.maxLat) / 2)
    try
      ts.foreach((id, t) => w.add(id, t))
      (ts.size, w.finish(header, metadata))
    catch case e: Exception => { w.abort(); throw e }
