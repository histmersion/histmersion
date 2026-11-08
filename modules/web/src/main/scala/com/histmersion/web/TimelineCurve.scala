package com.histmersion
package web

import com.histmersion.api.library.TimeBucket
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Years

/** The timeline's count curve, pure: buckets a few pixels wide, each one point at its middle, joined by a MONOTONE
  * cubic (Fritsch–Carlson, the rule of d3's `curveMonotoneX`). Between two points the curve never leaves the band their
  * counts span — it does not overshoot a peak, and never dips under the baseline (a count below zero) — and it is
  * smooth through each point. One bucket a pixel drawn with an S-curve per step was a staircase of 1-px risers.
  */
object TimelineCurve:
  type ElementSize = ElementSize.T
  object ElementSize extends Newtype[Double, Pure]
  type ScreenPixels = ScreenPixels.T
  object ScreenPixels extends Newtype[Double, Pure]

  /** How wide one bucket is drawn, px: coarse enough for a smooth line, fine enough for a peak. */

  val PxPerBucket: ScreenPixels = ScreenPixels(5.0)

  /** A bucket's point on the strip: `x`, `y` in the strip's pixels (y down), and the bucket it draws. */
  final case class Pt(x: ScreenPixels, y: ScreenPixels, bucket: TimeBucket)

  /** Pure: each bucket's point over `[from, to]` drawn `w` px wide — at its middle year, its height its share of the
    * largest count between `baseY` (none) and `top` (the largest).
    */
  def points(bs: Seq[TimeBucket], from: Years.Astronomical, to: Years.Astronomical, w: ElementSize, baseY: ScreenPixels, top: ScreenPixels): Vector[Pt] =
    val maxCount = bs.iterator.map(_.count.value).maxOption.getOrElse(0)
    if maxCount <= 0 then Vector.empty
    else
      val span = (to.value - from.value).toDouble.max(1)
      bs.iterator.map { b =>
        val mid = (b.start.value.toDouble + b.end.value.toDouble) / 2
        val x = (mid - from.value.toDouble) / span * w.value
        Pt(ScreenPixels(x), ScreenPixels(baseY.value - b.count.value.toDouble / maxCount * (baseY.value - top.value)), b)
      }.toVector

  /** Pure: the tangent (dy/dx) at each point of a monotone cubic through them (`xs` increasing). Where the neighbouring
    * slopes differ in sign (a peak or a valley) or one is flat, it is flat.
    */
  def tangents(px: IndexedSeq[ScreenPixels], py: IndexedSeq[ScreenPixels]): Array[Double] =
    val xs = px.map(_.value)
    val ys = py.map(_.value)
    val n = xs.length
    val t = new Array[Double](n)
    if n >= 2 then
      def sign(v: Double): Double = if v > 0 then 1.0 else if v < 0 then -1.0 else 0.0
      def secant(i: Int): Double =
        val h = xs(i + 1) - xs(i)
        if h == 0 then 0.0 else (ys(i + 1) - ys(i)) / h
      var i = 1
      while i < n - 1 do
        val h0 = xs(i) - xs(i - 1)
        val h1 = xs(i + 1) - xs(i)
        val s0 = secant(i - 1)
        val s1 = secant(i)
        val p = if h0 + h1 == 0 then 0.0 else (s0 * h1 + s1 * h0) / (h0 + h1)
        t(i) = (sign(s0) + sign(s1)) * math.min(math.min(math.abs(s0), math.abs(s1)), 0.5 * math.abs(p))
        i += 1
      // the ends: the one-sided three-point estimate, as d3's
      def end(a: Int, b: Int, inner: Double): Double =
        val h = xs(b) - xs(a)
        if h == 0 then inner else (3 * (ys(b) - ys(a)) / h - inner) / 2
      if n == 2 then
        val s = secant(0)
        t(0) = s; t(1) = s
      else
        t(0) = end(0, 1, t(1))
        t(n - 1) = end(n - 2, n - 1, t(n - 2))
    t

  /** One cubic segment: its two control points and its end. */
  final case class Segment(c1x: ScreenPixels, c1y: ScreenPixels, c2x: ScreenPixels, c2y: ScreenPixels, x: ScreenPixels, y: ScreenPixels)

  /** Pure: the cubic segments from each point to the next (none for fewer than two). */
  def segments(pts: IndexedSeq[Pt]): Vector[Segment] =
    if pts.length < 2 then Vector.empty
    else
      val xs = pts.map(_.x)
      val ys = pts.map(_.y)
      val t = tangents(xs, ys)
      Vector.tabulate(pts.length - 1) { i =>
        val dx = (xs(i + 1).value - xs(i).value) / 3
        Segment(
          ScreenPixels(xs(i).value + dx),
          ScreenPixels(ys(i).value + dx * t(i)),
          ScreenPixels(xs(i + 1).value - dx),
          ScreenPixels(ys(i + 1).value - dx * t(i + 1)),
          xs(i + 1),
          ys(i + 1)
        )
      }

  private def num(v: Double): String =
    val r = math.round(v * 100) / 100.0
    if r == r.toLong.toDouble then r.toLong.toString else r.toString

  /** Pure: the path's commands after its first point (` C …` per segment). */
  def curve(pts: IndexedSeq[Pt]): String =
    val sb = new StringBuilder
    segments(pts).foreach { s =>
      sb.append(" C ")
        .append(num(s.c1x.value))
        .append(' ')
        .append(num(s.c1y.value))
        .append(' ')
        .append(num(s.c2x.value))
        .append(' ')
        .append(num(s.c2y.value))
        .append(' ')
        .append(num(s.x.value))
        .append(' ')
        .append(num(s.y.value))
    }
    sb.toString

  /** Pure: the line through the points. */
  def linePath(pts: IndexedSeq[Pt]): String =
    if pts.isEmpty then ""
    else s"M ${num(pts.head.x.value)} ${num(pts.head.y.value)}" + curve(pts)

  /** Pure: the area under the line, closed along `baseY` (one point: a sliver a pixel wide). */
  def areaPath(pts: IndexedSeq[Pt], baseY: ScreenPixels): String =
    if pts.isEmpty then ""
    else
      val h = pts.head
      val start = s"M ${num(h.x.value)} ${num(baseY.value)} L ${num(h.x.value)} ${num(h.y.value)}"
      if pts.length == 1 then
        s"$start L ${num(h.x.value + 1)} ${num(h.y.value)} L ${num(h.x.value + 1)} ${num(baseY.value)} Z"
      else s"$start${curve(pts)} L ${num(pts.last.x.value)} ${num(baseY.value)} Z"

  /** Pure: the point nearest screen x `x` (points in increasing x), by binary search — the hover's. */
  def nearest(pts: IndexedSeq[Pt], x: ScreenPixels): Option[Pt] =
    if pts.isEmpty then None
    else
      var lo = 0
      var hi = pts.length - 1
      while lo < hi do
        val mid = (lo + hi) >>> 1
        if pts(mid).x.value < x.value then lo = mid + 1 else hi = mid
      val right = pts(lo)
      if lo > 0 && math.abs(pts(lo - 1).x.value - x.value) <= math.abs(right.x.value - x.value) then Some(pts(lo - 1)) else Some(right)
