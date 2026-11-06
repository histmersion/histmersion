package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The clustering of points on screen, pure: which points are drawn as one bubble at a zoom. */
object Clustering:

  /** World pixels of a point at zoom `z` on the 256-pixel grid (Web Mercator). Pure. */
  type GroupCellResult = GroupCellResult.T
  object GroupCellResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupCellCx = GroupCellCx.T
  object GroupCellCx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupCellCy = GroupCellCy.T
  object GroupCellCy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GroupResult = GroupResult.T
  object GroupResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WorldResultPart1 = WorldResultPart1.T
  object WorldResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldResultPart2 = WorldResultPart2.T
  object WorldResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldLat = WorldLat.T
  object WorldLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldLon = WorldLon.T
  object WorldLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorldZ = WorldZ.T
  object WorldZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupRadius = GroupRadius.T
  object GroupRadius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupsResultPart1 = GroupsResultPart1.T
  object GroupsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupsPtsPart1 = GroupsPtsPart1.T
  object GroupsPtsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupsZ = GroupsZ.T
  object GroupsZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupsRadius = GroupsRadius.T
  object GroupsRadius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewResult = InViewResult.T
  object InViewResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewMargin = InViewMargin.T
  object InViewMargin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnResultPart1 = DrawnResultPart1.T
  object DrawnResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnResultPart2Part1 = DrawnResultPart2Part1.T
  object DrawnResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnResultPart2Part2 = DrawnResultPart2Part2.T
  object DrawnResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnGroupsPart1 = DrawnGroupsPart1.T
  object DrawnGroupsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnePlaceResult = OnePlaceResult.T
  object OnePlaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LevelResult = LevelResult.T
  object LevelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LevelZoom = LevelZoom.T
  object LevelZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def world(lat: com.histmersion.web.map.Clustering.WorldLat, lon: com.histmersion.web.map.Clustering.WorldLon, z: com.histmersion.web.map.Clustering.WorldZ): (com.histmersion.web.map.Clustering.WorldResultPart1, com.histmersion.web.map.Clustering.WorldResultPart2) =
    val scale = 256.0 * math.pow(2, z)
    val l = math.max(-85.05112878, math.min(85.05112878, lat))
    val s = math.sin(math.toRadians(l))
    ((lon + 180) / 360 * scale, (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.Pi)) * scale)

  /** Greedy grouping, in the points' order (the rule markercluster had): a point joins the nearest group whose FIRST
    * point is within `radius` px, else starts one. Answers each point's group number (0, 1, … in the order the groups
    * were started). Pure; one pass with a grid of `radius` cells.
    */
  def group(xs: Array[Double], ys: Array[Double], radius: com.histmersion.web.map.Clustering.GroupRadius): com.histmersion.web.map.Clustering.GroupResult =
    val n = xs.length
    val out = new Array[Int](n)
    if radius <= 0 then
      var i = 0
      while i < n do { out(i) = i; i += 1 }
      out
    else
      // cell → the groups seeded there; a JS map over a number key (cells are whole numbers well inside
      // 2^53: the grid of a world at zoom 20 is 2^28 / radius cells a side)
      val cells = js.Map.empty[Double, js.Array[Int]]
      val seedX = js.Array[Double]()
      val seedY = js.Array[Double]()
      def cell(cx: com.histmersion.web.map.Clustering.GroupCellCx, cy: com.histmersion.web.map.Clustering.GroupCellCy): com.histmersion.web.map.Clustering.GroupCellResult = cx * 67108864.0 + cy
      val r2 = radius * radius
      var i = 0
      while i < n do
        val x = xs(i)
        val y = ys(i)
        val cx = math.floor(x / radius)
        val cy = math.floor(y / radius)
        var best = -1
        var bestD = Double.MaxValue
        var dx = -1.0
        while dx <= 1 do
          var dy = -1.0
          while dy <= 1 do
            val gs = cells.getOrElse(cell(cx + dx, cy + dy), null)
            if gs != null then
              var k = 0
              while k < gs.length do
                val g = gs(k)
                val ex = seedX(g) - x
                val ey = seedY(g) - y
                val d = ex * ex + ey * ey
                if d <= r2 && (d < bestD || (d == bestD && g < best)) then { best = g; bestD = d }
                k += 1
            dy += 1
          dx += 1
        if best < 0 then
          best = seedX.length
          seedX.push(x)
          seedY.push(y)
          val c = cell(cx, cy)
          val gs = cells.getOrElse(c, null)
          if gs == null then cells(c) = js.Array(best) else gs.push(best)
        out(i) = best
        i += 1
      out

  /** The groups of `pts` at zoom `z`: each group's keys (in the points' order) and where its bubble stands — the mean
    * of its points. Pure.
    */
  def groups(pts: Seq[(com.histmersion.web.map.Clustering.GroupsPtsPart1, LatLon)], z: com.histmersion.web.map.Clustering.GroupsZ, radius: com.histmersion.web.map.Clustering.GroupsRadius): Vector[(Vector[com.histmersion.web.map.Clustering.GroupsResultPart1], LatLon)] =
    val n = pts.length
    val arr = pts.toArray
    val xs = new Array[Double](n)
    val ys = new Array[Double](n)
    var i = 0
    while i < n do
      val (x, y) = world(arr(i)._2.lat, arr(i)._2.lon, z)
      xs(i) = x; ys(i) = y
      i += 1
    val g = group(xs, ys, radius)
    val count = if n == 0 then 0 else g.max + 1
    val keys = Array.fill(count)(js.Array[String]())
    val lat = new Array[Double](count)
    val lon = new Array[Double](count)
    i = 0
    while i < n do
      val k = g(i)
      keys(k).push(arr(i)._1)
      lat(k) += arr(i)._2.lat
      lon(k) += arr(i)._2.lon
      i += 1
    Vector.tabulate(count)(k => (keys(k).toVector, LatLon(lat(k) / keys(k).length, lon(k) / keys(k).length)))

  /** Is `at` on `s`'s screen, or within `margin` px of it (the copy of the world nearest the view)? */
  def inView(s: MapSurface, at: LatLon, margin: com.histmersion.web.map.Clustering.InViewMargin): com.histmersion.web.map.Clustering.InViewResult =
    val (w, h) = s.size
    val c = s.center.lon
    val q = s.project(LatLon(at.lat, at.lon + 360 * math.round((c - at.lon) / 360).toDouble))
    q.x >= -margin && q.y >= -margin && q.x <= w + margin && q.y <= h + margin

  /** What a cluster layer draws of `groups` on `s`: the points drawn alone and the bubbles (their points), those in
    * view and a margin of a quarter of the view. Pure but for the projection.
    */
  def drawn(groups: Vector[(Vector[com.histmersion.web.map.Clustering.DrawnGroupsPart1], LatLon)], s: MapSurface): (Set[com.histmersion.web.map.Clustering.DrawnResultPart1], Map[com.histmersion.web.map.Clustering.DrawnResultPart2Part1, Vector[com.histmersion.web.map.Clustering.DrawnResultPart2Part2]]) =
    val (w, h) = s.size
    val margin = 0.25 * math.max(w, h)
    val shown = groups.filter((_, at) => inView(s, at, margin))
    (
      shown.collect { case (ks, _) if ks.size == 1 => ks.head }.toSet,
      shown.collect { case (ks, _) if ks.size > 1 => s"${ks.head}|${ks.size}" -> ks }.toMap
    )

  /** Pure: do `pts` all stand at one spot — a bubble no zoom can part? */
  def onePlace(pts: Seq[LatLon]): com.histmersion.web.map.Clustering.OnePlaceResult =
    pts.nonEmpty && pts.forall(p => p.lat == pts.head.lat && p.lon == pts.head.lon)

  /** The zoom a map at `zoom` clusters at: the nearest whole zoom (the bubbles regroup as the map passes the middle
    * between two). Pure.
    */
  def level(zoom: com.histmersion.web.map.Clustering.LevelZoom): com.histmersion.web.map.Clustering.LevelResult = math.round(zoom).toInt
