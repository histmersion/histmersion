package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The clustering of points on screen, pure: which points are drawn as one bubble at a zoom. */
object Clustering:

  /** World pixels of a point at zoom `z` on the 256-pixel grid (Web Mercator). Pure. */
  def world(lat: Double, lon: Double, z: Double): (Double, Double) =
    val scale = 256.0 * math.pow(2, z)
    val l = math.max(-85.05112878, math.min(85.05112878, lat))
    val s = math.sin(math.toRadians(l))
    ((lon + 180) / 360 * scale, (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.Pi)) * scale)

  /** Greedy grouping, in the points' order (the rule markercluster had): a point joins the nearest group whose FIRST
    * point is within `radius` px, else starts one. Answers each point's group number (0, 1, … in the order the groups
    * were started). Pure; one pass with a grid of `radius` cells.
    */
  def group(xs: Array[Double], ys: Array[Double], radius: Double): Array[Int] =
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
      def cell(cx: Double, cy: Double): Double = cx * 67108864.0 + cy
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
  def groups(pts: Seq[(String, LatLon)], z: Int, radius: Double): Vector[(Vector[String], LatLon)] =
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
  def inView(s: MapSurface, at: LatLon, margin: Double): Boolean =
    val (w, h) = s.size
    val c = s.center.lon
    val q = s.project(LatLon(at.lat, at.lon + 360 * math.round((c - at.lon) / 360).toDouble))
    q.x >= -margin && q.y >= -margin && q.x <= w + margin && q.y <= h + margin

  /** What a cluster layer draws of `groups` on `s`: the points drawn alone and the bubbles (their points), those in
    * view and a margin of a quarter of the view. Pure but for the projection.
    */
  def drawn(groups: Vector[(Vector[String], LatLon)], s: MapSurface): (Set[String], Map[String, Vector[String]]) =
    val (w, h) = s.size
    val margin = 0.25 * math.max(w, h)
    val shown = groups.filter((_, at) => inView(s, at, margin))
    (
      shown.collect { case (ks, _) if ks.size == 1 => ks.head }.toSet,
      shown.collect { case (ks, _) if ks.size > 1 => s"${ks.head}|${ks.size}" -> ks }.toMap
    )

  /** Pure: do `pts` all stand at one spot — a bubble no zoom can part? */
  def onePlace(pts: Seq[LatLon]): Boolean =
    pts.nonEmpty && pts.forall(p => p.lat == pts.head.lat && p.lon == pts.head.lon)

  /** The zoom a map at `zoom` clusters at: the nearest whole zoom (the bubbles regroup as the map passes the middle
    * between two). Pure.
    */
  def level(zoom: Double): Int = math.round(zoom).toInt
