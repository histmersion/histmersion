package com.histmersion.web

import scala.collection.mutable

/** Where a region's or polity's NAME goes on the map, and which names are shown at a zoom — all pure,
  * so the placement is a unit test and the map only applies the answer ([[MapLabels]]).
  *
  * Coordinates are Web-Mercator pixels at zoom 0 (a 256 px world, `y` down), the plane Leaflet draws
  * on: a distance there times `2^zoom` is screen pixels, so a label's anchor and a shape's size are
  * computed ONCE per shape and every zoom after that is a multiplication.
  *
  *   - [[anchor]]: the pole of inaccessibility (Mapbox's polylabel) of the largest part — the point
  *     inside the shape farthest from its edge, where a name has the most room; a crescent or a
  *     coastline-hugging polity is labelled inside itself, never at a centroid in the sea;
  *   - [[tier]]: how big the name is drawn at a zoom, or not at all when the shape is too small;
  *   - [[cull]]: which labels survive collisions, larger shapes first. */
object LabelPlacement:

  /** A point in the zoom-0 Mercator plane. */
  final case class Pt(x: Double, y: Double)

  /** A polygon: its outer ring then its holes, each a closed or open list of points. */
  type Poly = Vector[Vector[Pt]]

  private val World = 256.0
  private val MaxLat = 85.0511287798

  /** Pure: (lat, lon) → the zoom-0 Mercator plane. */
  def project(lat: Double, lon: Double): Pt =
    val phi = math.toRadians(math.max(-MaxLat, math.min(MaxLat, lat)))
    Pt((lon + 180.0) / 360.0 * World, (1.0 - math.log(math.tan(phi) + 1.0 / math.cos(phi)) / math.Pi) / 2.0 * World)

  /** Pure: the inverse of [[project]] → (lat, lon). */
  def unproject(p: Pt): (Double, Double) =
    val lon = p.x / World * 360.0 - 180.0
    val n = math.Pi - 2.0 * math.Pi * p.y / World
    (math.toDegrees(math.atan(math.sinh(n))), lon)

  /** Pure: a ring's unsigned area (shoelace). */
  def ringArea(r: Vector[Pt]): Double =
    if r.length < 3 then 0.0
    else
      var s = 0.0
      var j = r.length - 1
      var i = 0
      while i < r.length do
        s += (r(j).x - r(i).x) * (r(j).y + r(i).y)
        j = i
        i += 1
      math.abs(s) / 2.0

  /** Pure: a polygon's area — its outer ring less its holes. */
  def polyArea(p: Poly): Double =
    p.headOption.fold(0.0)(ringArea) - p.drop(1).map(ringArea).sum

  /** What the map needs to know about one shape, computed once: where its name goes (lat, lon), its
    * area and the width of its largest part's bounding box, both at zoom 0. */
  final case class Shape(lat: Double, lon: Double, area0: Double, width0: Double)

  /** Pure: the label anchor and size of a (multi)polygon given in (lat, lon) rings — `None` for an
    * empty one. The name goes in the LARGEST part; the area is all of them. */
  def shape(polys: Seq[Seq[Seq[(Double, Double)]]]): Option[Shape] =
    val projected = polys.map(_.map(_.map((la, lo) => project(la, lo)).toVector).toVector).filter(_.nonEmpty)
      .filter(_.head.length >= 3)
    if projected.isEmpty then None
    else
      val areas = projected.map(polyArea)
      val largest = projected(areas.indices.maxBy(areas))
      val outer = largest.head
      val width = outer.map(_.x).max - outer.map(_.x).min
      val (lat, lon) = unproject(anchor(largest))
      Some(Shape(lat, lon, areas.sum, width))

  /** At most this many points of a ring are used to place a name: a label needs the shape's room, not
    * its coastline, and a 20 000-point Cliopatria outline would otherwise cost every probe 20 000
    * segment distances. */
  val MaxRingPoints = 400

  /** Pure: every `k`-th point, so the ring keeps at most `max` (its first point always). */
  def thin(r: Vector[Pt], max: Int = MaxRingPoints): Vector[Pt] =
    if r.length <= max then r
    else
      val step = math.ceil(r.length.toDouble / max).toInt
      r.indices.by(step).map(r).toVector

  /** Cells probed at most per shape, so one pathological outline cannot stall a frame. */
  val MaxProbes = 1500

  /** Pure: the pole of inaccessibility of `poly` (polylabel, Agafonkin 2016): a best-first quadtree
    * search for the interior point farthest from every edge, to within 1% of the shape's size. */
  def anchor(poly: Poly): Pt =
    val rings = poly.map(thin(_))
    val outer = rings.head
    val minX = outer.map(_.x).min
    val maxX = outer.map(_.x).max
    val minY = outer.map(_.y).min
    val maxY = outer.map(_.y).max
    val w = maxX - minX
    val h = maxY - minY
    val cellSize = math.min(w, h)
    if cellSize <= 0 then Pt(minX + w / 2, minY + h / 2)
    else
      val precision = math.max(w, h) / 100.0
      final case class Cell(x: Double, y: Double, half: Double):
        val d: Double   = signedDistance(x, y, rings)
        val max: Double = d + half * math.sqrt(2)
      val queue = mutable.PriorityQueue.empty[Cell](using Ordering.by[Cell, Double](_.max))
      var cx = minX
      while cx < maxX do
        var cy = minY
        while cy < maxY do
          queue.enqueue(Cell(cx + cellSize / 2, cy + cellSize / 2, cellSize / 2))
          cy += cellSize
        cx += cellSize
      var best = centroidCell(outer).map((x, y) => Cell(x, y, 0)).getOrElse(Cell(minX + w / 2, minY + h / 2, 0))
      val box = Cell(minX + w / 2, minY + h / 2, 0)
      if box.d > best.d then best = box
      var probes = 0
      while queue.nonEmpty && probes < MaxProbes do
        val c = queue.dequeue()
        probes += 1
        if c.d > best.d then best = c
        if c.max - best.d > precision then
          val q = c.half / 2
          queue.enqueue(Cell(c.x - q, c.y - q, q))
          queue.enqueue(Cell(c.x + q, c.y - q, q))
          queue.enqueue(Cell(c.x - q, c.y + q, q))
          queue.enqueue(Cell(c.x + q, c.y + q, q))
      Pt(best.x, best.y)

  private def centroidCell(r: Vector[Pt]): Option[(Double, Double)] =
    var area = 0.0
    var x = 0.0
    var y = 0.0
    var j = r.length - 1
    var i = 0
    while i < r.length do
      val a = r(i)
      val b = r(j)
      val f = a.x * b.y - b.x * a.y
      x += (a.x + b.x) * f
      y += (a.y + b.y) * f
      area += f * 3
      j = i
      i += 1
    if area == 0 then None else Some((x / area, y / area))

  /** Pure: the distance from (x, y) to the polygon's nearest edge, positive inside, negative outside. */
  def signedDistance(x: Double, y: Double, rings: Poly): Double =
    var inside = false
    var minSq = Double.PositiveInfinity
    for r <- rings do
      var j = r.length - 1
      var i = 0
      while i < r.length do
        val a = r(i)
        val b = r(j)
        if (a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x then inside = !inside
        minSq = math.min(minSq, segDistSq(x, y, a, b))
        j = i
        i += 1
    (if inside then 1 else -1) * math.sqrt(minSq)

  private def segDistSq(px: Double, py: Double, a: Pt, b: Pt): Double =
    var x = a.x
    var y = a.y
    val dx = b.x - x
    val dy = b.y - y
    if dx != 0 || dy != 0 then
      val t = ((px - x) * dx + (py - y) * dy) / (dx * dx + dy * dy)
      if t > 1 then { x = b.x; y = b.y }
      else if t > 0 then { x += dx * t; y += dy * t }
    val ex = px - x
    val ey = py - y
    ex * ex + ey * ey

  // ── how big, and whether at all ──────────────────────────────────────────────────────────────────

  /** A name's size class (`.hd-map-label--s/m/l`): small shapes get small names. */
  enum Tier(val css: String):
    case S extends Tier("hd-map-label--s")
    case M extends Tier("hd-map-label--m")
    case L extends Tier("hd-map-label--l")

  /** A shape smaller than this many screen pixels on a side is not named at all at that zoom. */
  val MinSidePx = 44.0
  val MediumSidePx = 150.0
  val LargeSidePx = 340.0

  /** Pure: the size of a shape's name at `zoom`, from the side of a square of the shape's on-screen
    * area — `None` when it is too small to name. */
  def tier(area0: Double, zoom: Double): Option[Tier] =
    val side = math.sqrt(area0) * math.pow(2, zoom)
    if side < MinSidePx then None
    else if side >= LargeSidePx then Some(Tier.L)
    else if side >= MediumSidePx then Some(Tier.M)
    else Some(Tier.S)

  /** Pure: does a name `labelW` px wide belong on a shape whose largest part is `width0` wide at zoom
    * 0? A name may overhang its shape a little (a narrow, tall state), never run across its neighbours. */
  def fits(labelW: Double, width0: Double, zoom: Double): Boolean =
    labelW <= width0 * math.pow(2, zoom) * 1.6

  // ── collisions ───────────────────────────────────────────────────────────────────────────────────

  /** A label as the culling sees it: its key, its centre in screen (or layer) pixels, its size, and
    * its rank — higher wins a collision. */
  final case class Box(key: String, cx: Double, cy: Double, w: Double, h: Double, rank: Double)

  /** Room kept between two names, px. */
  val PadPx = 3.0

  /** Pure: which labels are drawn — greedily, highest rank first (ties by key, so the answer is
    * stable), a label is kept unless its box (padded by [[PadPx]]) overlaps one already kept. A grid
    * of `cell`-pixel buckets keeps it near-linear: 400 labels are a few thousand box tests. */
  def cull(boxes: Seq[Box], cell: Double = 128.0): Set[String] =
    val grid = mutable.HashMap.empty[(Int, Int), mutable.ArrayBuffer[Box]]
    val kept = mutable.Set.empty[String]
    def cells(b: Box): Seq[(Int, Int)] =
      val x0 = math.floor((b.cx - b.w / 2 - PadPx) / cell).toInt
      val x1 = math.floor((b.cx + b.w / 2 + PadPx) / cell).toInt
      val y0 = math.floor((b.cy - b.h / 2 - PadPx) / cell).toInt
      val y1 = math.floor((b.cy + b.h / 2 + PadPx) / cell).toInt
      for gx <- x0 to x1; gy <- y0 to y1 yield (gx, gy)
    def overlaps(a: Box, b: Box): Boolean =
      math.abs(a.cx - b.cx) * 2 < a.w + b.w + 2 * PadPx && math.abs(a.cy - b.cy) * 2 < a.h + b.h + 2 * PadPx
    for b <- boxes.sortBy(b => (-b.rank, b.key)) do
      val cs = cells(b)
      if !cs.exists(c => grid.get(c).exists(_.exists(overlaps(b, _)))) then
        kept += b.key
        cs.foreach(c => grid.getOrElseUpdate(c, mutable.ArrayBuffer.empty) += b)
    kept.toSet
