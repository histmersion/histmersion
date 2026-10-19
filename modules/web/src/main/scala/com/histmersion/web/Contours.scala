package com.histmersion.web

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

/** Contour lines traced from an elevation grid (marching squares), for the terrain map's contours
  * (docs/user-guide/how-to/terrain-styles.md, "Contours"): every `interval` metres above sea level, every
  * `indexEvery`-th level an index line. Each crossing of a level is found once per grid edge, at the
  * same place by both cells sharing it, so the segments join exactly and two neighbouring tiles traced
  * from grids that share their edge values join at the tile edge too.
  *
  * Pure: a grid in, segments and polylines out. One pass over the cells, each cell visiting only the
  * levels between its lowest and highest corner. */
object Contours:

  /** Segments `x1 y1 x2 y2 …` in grid coordinates (column, row of the grid's samples), the ordinary
    * lines and the index lines apart, and the index lines' levels and grid edges (to join them). */
  final case class Traced(lines: js.Array[Double], index: js.Array[Double], indexLevels: js.Array[Double],
      indexEdges: js.Array[Double])

  /** The levels of a contour interval between `lo` and `hi`, above `min` (sea level is the coast, not a
    * contour). Pure. */
  def levels(lo: Double, hi: Double, interval: Double, min: Double = 0): List[Double] =
    if interval <= 0 || hi < lo then Nil
    else
      val first = math.ceil(math.max(lo, min + 1e-9) / interval)
      Iterator.iterate(first)(_ + 1).map(_ * interval).takeWhile(_ <= hi).filter(_ > min).toList

  /** Whether `level` is an index level of this interval. Pure. */
  def isIndex(level: Double, interval: Double, indexEvery: Int): Boolean =
    indexEvery > 0 && { val q = level / (interval * indexEvery); math.abs(q - math.rint(q)) < 1e-6 }

  /** The marching-squares segments of a `w`×`h` grid (row-major) at every level of `interval`. */
  def trace(g: Float32Array, w: Int, h: Int, interval: Double, indexEvery: Int, min: Double = 0,
      max: Double = Double.PositiveInfinity): Traced =
    val lines = js.Array[Double]()
    val index = js.Array[Double]()
    val idxLevels = js.Array[Double]()
    val idxEdges = js.Array[Double]()
    // grid edges: horizontal (i, j)–(i, j+1) is 2·(i·w + j), vertical (i, j)–(i+1, j) is 2·(i·w + j) + 1
    def hx(i: Int, j: Int, l: Double): Double = j + frac(g(i * w + j), g(i * w + j + 1), l)
    def vy(i: Int, j: Int, l: Double): Double = i + frac(g(i * w + j), g((i + 1) * w + j), l)
    var i = 0
    while i < h - 1 do
      var j = 0
      while j < w - 1 do
        val tl = g(i * w + j); val tr = g(i * w + j + 1); val bl = g((i + 1) * w + j); val br = g((i + 1) * w + j + 1)
        val lo = math.min(math.min(tl, tr), math.min(bl, br))
        val hi = math.max(math.max(tl, tr), math.max(bl, br))
        if hi > min && lo < max && hi - lo > 0 then
          var l = math.ceil(math.max(lo, min + 1e-9) / interval) * interval
          while l <= hi && l < max do
            if l > min && l != 0 then
              val c = (if tl >= l then 8 else 0) | (if tr >= l then 4 else 0) | (if br >= l then 2 else 0) | (if bl >= l then 1 else 0)
              if c != 0 && c != 15 then
                val ix = isIndex(l, interval, indexEvery)
                val out = if ix then index else lines
                // an edge's point and its id: 0 top, 1 right, 2 bottom, 3 left
                def px(e: Int): Double = e match
                  case 0 => hx(i, j, l); case 1 => j + 1.0; case 2 => hx(i + 1, j, l); case _ => j.toDouble
                def py(e: Int): Double = e match
                  case 0 => i.toDouble; case 1 => vy(i, j + 1, l); case 2 => i + 1.0; case _ => vy(i, j, l)
                def id(e: Int): Double = e match
                  case 0 => 2.0 * (i * w + j); case 1 => 2.0 * (i * w + j + 1) + 1; case 2 => 2.0 * ((i + 1) * w + j)
                  case _ => 2.0 * (i * w + j) + 1
                def seg(a: Int, b: Int): Unit =
                  out.push(px(a), py(a), px(b), py(b))
                  if ix then { idxLevels.push(l); idxEdges.push(id(a), id(b)) }
                val centre = (tl + tr + bl + br) / 4 >= l
                c match
                  case 1 | 14 => seg(3, 2)
                  case 2 | 13 => seg(2, 1)
                  case 3 | 12 => seg(3, 1)
                  case 4 | 11 => seg(0, 1)
                  case 6 | 9  => seg(0, 2)
                  case 7 | 8  => seg(3, 0)
                  case 5 => if centre then { seg(3, 0); seg(2, 1) } else { seg(0, 1); seg(3, 2) }
                  case _ => if centre then { seg(0, 1); seg(3, 2) } else { seg(3, 0); seg(2, 1) }   // 10
            l += interval
        j += 1
      i += 1
    Traced(lines, index, idxLevels, idxEdges)

  /** Where level `l` crosses from `a` to `b` (0 at a, 1 at b). */
  private def frac(a: Double, b: Double, l: Double): Double =
    if b == a then 0.5 else math.max(0.0, math.min(1.0, (l - a) / (b - a)))

  /** The index segments joined into polylines (`x y x y …`), each with its level: segments of one level
    * sharing a grid edge are one line. Pure. */
  def polylines(t: Traced): List[(Double, js.Array[Double])] =
    val n = t.indexLevels.length
    // per (level, edge): the segments touching it
    val touching = mutable.HashMap.empty[(Double, Double), List[Int]]
    var s = 0
    while s < n do
      val l = t.indexLevels(s)
      touching((l, t.indexEdges(2 * s))) = s :: touching.getOrElse((l, t.indexEdges(2 * s)), Nil)
      touching((l, t.indexEdges(2 * s + 1))) = s :: touching.getOrElse((l, t.indexEdges(2 * s + 1)), Nil)
      s += 1
    val used = new Array[Boolean](n)
    def point(s: Int, end: Int): (Double, Double) = (t.index(4 * s + 2 * end), t.index(4 * s + 2 * end + 1))
    def edge(s: Int, end: Int): Double = t.indexEdges(2 * s + end)
    // walk from segment s out of its end `end`, appending points
    def walk(s0: Int, end0: Int, acc: mutable.ArrayBuffer[(Double, Double)]): Unit =
      var s = s0; var end = end0
      var go = true
      while go do
        val l = t.indexLevels(s)
        touching.getOrElse((l, edge(s, end)), Nil).find(o => !used(o)) match
          case Some(o) =>
            used(o) = true
            val oe = if edge(o, 0) == edge(s, end) then 1 else 0   // the other end of `o`
            acc += point(o, oe)
            s = o; end = oe
          case None => go = false
    (0 until n).flatMap { s0 =>
      if used(s0) then None
      else
        used(s0) = true
        val fwd = mutable.ArrayBuffer(point(s0, 0), point(s0, 1))
        walk(s0, 1, fwd)
        val back = mutable.ArrayBuffer.empty[(Double, Double)]
        walk(s0, 0, back)
        val pts = back.reverse ++ fwd
        val arr = js.Array[Double]()
        pts.foreach((x, y) => arr.push(x, y))
        Some((t.indexLevels(s0), arr))
    }.toList

  /** A polyline's length. Pure. */
  def length(p: js.Array[Double]): Double =
    var d = 0.0
    var k = 2
    while k < p.length do
      d += math.hypot(p(k) - p(k - 2), p(k + 1) - p(k - 1))
      k += 2
    d

  /** Where a label of `width` goes on a polyline: the point half way along it and the line's angle
    * there (radians, turned so the text is never upside down) — `None` when the line is shorter than
    * `width` + 2·`clear` or the point is within `margin` of the box `0…size`. Pure. */
  def labelAt(p: js.Array[Double], width: Double, clear: Double, size: Double, margin: Double): Option[(Double, Double, Double)] =
    val total = length(p)
    if total < width + 2 * clear then None
    else
      // the point at half the length, and two points half a label's width either side of it
      def at(d: Double): (Double, Double) =
        var acc = 0.0
        var k = 2
        var out = (p(p.length - 2), p(p.length - 1))
        var found = false
        while !found && k < p.length do
          val seg = math.hypot(p(k) - p(k - 2), p(k + 1) - p(k - 1))
          if acc + seg >= d then
            val t = if seg == 0 then 0 else (d - acc) / seg
            out = (p(k - 2) + (p(k) - p(k - 2)) * t, p(k - 1) + (p(k + 1) - p(k - 1)) * t)
            found = true
          acc += seg
          k += 2
        out
      val (x, y) = at(total / 2)
      val (ax, ay) = at(total / 2 - width / 2)
      val (bx, by) = at(total / 2 + width / 2)
      if x < margin || y < margin || x > size - margin || y > size - margin then None
      else
        val a = math.atan2(by - ay, bx - ax)
        Some((x, y, if a > math.Pi / 2 then a - math.Pi else if a < -math.Pi / 2 then a + math.Pi else a))
