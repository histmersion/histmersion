package com.histmersion.web

import com.histmersion.api.library.TimeBucket

/** The timeline's count curve, pure: buckets a few pixels wide, each one point at its middle, joined by a MONOTONE
  * cubic (Fritsch–Carlson, the rule of d3's `curveMonotoneX`). Between two points the curve never leaves the band their
  * counts span — it does not overshoot a peak, and never dips under the baseline (a count below zero) — and it is
  * smooth through each point. One bucket a pixel drawn with an S-curve per step was a staircase of 1-px risers.
  */
object TimelineCurve:

  /** How wide one bucket is drawn, px: coarse enough for a smooth line, fine enough for a peak. */
  type TangentsSignResult = TangentsSignResult.T
  object TangentsSignResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsSignV = TangentsSignV.T
  object TangentsSignV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsSecantResult = TangentsSecantResult.T
  object TangentsSecantResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsSecantI = TangentsSecantI.T
  object TangentsSecantI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsEndResult = TangentsEndResult.T
  object TangentsEndResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsEndA = TangentsEndA.T
  object TangentsEndA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsEndB = TangentsEndB.T
  object TangentsEndB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsEndInner = TangentsEndInner.T
  object TangentsEndInner extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PxPerBucket = PxPerBucket.T
  object PxPerBucket extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointsFrom = PointsFrom.T
  object PointsFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointsTo = PointsTo.T
  object PointsTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointsW = PointsW.T
  object PointsW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointsBaseY = PointsBaseY.T
  object PointsBaseY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointsTop = PointsTop.T
  object PointsTop extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsXs = TangentsXs.T
  object TangentsXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TangentsYs = TangentsYs.T
  object TangentsYs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumResult = NumResult.T
  object NumResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumV = NumV.T
  object NumV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurveResult = CurveResult.T
  object CurveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinePathResult = LinePathResult.T
  object LinePathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AreaPathResult = AreaPathResult.T
  object AreaPathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AreaPathBaseY = AreaPathBaseY.T
  object AreaPathBaseY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearestX = NearestX.T
  object NearestX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val PxPerBucket: com.histmersion.web.TimelineCurve.PxPerBucket = 5.0

  /** A bucket's point on the strip: `x`, `y` in the strip's pixels (y down), and the bucket it draws. */
  final case class Pt(x: Pt.X, y: Pt.Y, bucket: TimeBucket)

  object Pt:
    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: each bucket's point over `[from, to]` drawn `w` px wide — at its middle year, its height its share of the
    * largest count between `baseY` (none) and `top` (the largest).
    */
  def points(bs: Seq[TimeBucket], from: com.histmersion.web.TimelineCurve.PointsFrom, to: com.histmersion.web.TimelineCurve.PointsTo, w: com.histmersion.web.TimelineCurve.PointsW, baseY: com.histmersion.web.TimelineCurve.PointsBaseY, top: com.histmersion.web.TimelineCurve.PointsTop): Vector[Pt] =
    val maxCount = bs.iterator.map(_.count).maxOption.getOrElse(0)
    if maxCount <= 0 then Vector.empty
    else
      val span = (to - from).toDouble.max(1)
      bs.iterator.map { b =>
        val mid = (b.start.toDouble + b.end.toDouble) / 2
        val x = (mid - from.toDouble) / span * w
        Pt(x, baseY - b.count.toDouble / maxCount * (baseY - top), b)
      }.toVector

  /** Pure: the tangent (dy/dx) at each point of a monotone cubic through them (`xs` increasing). Where the neighbouring
    * slopes differ in sign (a peak or a valley) or one is flat, it is flat.
    */
  def tangents(xs: IndexedSeq[com.histmersion.web.TimelineCurve.TangentsXs], ys: IndexedSeq[com.histmersion.web.TimelineCurve.TangentsYs]): Array[Double] =
    val n = xs.length
    val t = new Array[Double](n)
    if n >= 2 then
      def sign(v: com.histmersion.web.TimelineCurve.TangentsSignV): com.histmersion.web.TimelineCurve.TangentsSignResult = if v > 0 then 1.0 else if v < 0 then -1.0 else 0.0
      def secant(i: com.histmersion.web.TimelineCurve.TangentsSecantI): com.histmersion.web.TimelineCurve.TangentsSecantResult =
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
      def end(a: com.histmersion.web.TimelineCurve.TangentsEndA, b: com.histmersion.web.TimelineCurve.TangentsEndB, inner: com.histmersion.web.TimelineCurve.TangentsEndInner): com.histmersion.web.TimelineCurve.TangentsEndResult =
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
  final case class Segment(c1x: Segment.C1x, c1y: Segment.C1y, c2x: Segment.C2x, c2y: Segment.C2y, x: Segment.X, y: Segment.Y)

  object Segment:
    type C1x = C1x.T
    object C1x extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type C1y = C1y.T
    object C1y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type C2x = C2x.T
    object C2x extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type C2y = C2y.T
    object C2y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: the cubic segments from each point to the next (none for fewer than two). */
  def segments(pts: IndexedSeq[Pt]): Vector[Segment] =
    if pts.length < 2 then Vector.empty
    else
      val xs = pts.map(_.x)
      val ys = pts.map(_.y)
      val t = tangents(xs, ys)
      Vector.tabulate(pts.length - 1) { i =>
        val dx = (xs(i + 1) - xs(i)) / 3
        Segment(xs(i) + dx, ys(i) + dx * t(i), xs(i + 1) - dx, ys(i + 1) - dx * t(i + 1), xs(i + 1), ys(i + 1))
      }

  private def num(v: com.histmersion.web.TimelineCurve.NumV): com.histmersion.web.TimelineCurve.NumResult =
    val r = math.round(v * 100) / 100.0
    if r == r.toLong.toDouble then r.toLong.toString else r.toString

  /** Pure: the path's commands after its first point (` C …` per segment). */
  def curve(pts: IndexedSeq[Pt]): com.histmersion.web.TimelineCurve.CurveResult =
    val sb = new StringBuilder
    segments(pts).foreach { s =>
      sb.append(" C ")
        .append(num(s.c1x))
        .append(' ')
        .append(num(s.c1y))
        .append(' ')
        .append(num(s.c2x))
        .append(' ')
        .append(num(s.c2y))
        .append(' ')
        .append(num(s.x))
        .append(' ')
        .append(num(s.y))
    }
    sb.toString

  /** Pure: the line through the points. */
  def linePath(pts: IndexedSeq[Pt]): com.histmersion.web.TimelineCurve.LinePathResult =
    if pts.isEmpty then ""
    else s"M ${num(pts.head.x)} ${num(pts.head.y)}" + curve(pts)

  /** Pure: the area under the line, closed along `baseY` (one point: a sliver a pixel wide). */
  def areaPath(pts: IndexedSeq[Pt], baseY: com.histmersion.web.TimelineCurve.AreaPathBaseY): com.histmersion.web.TimelineCurve.AreaPathResult =
    if pts.isEmpty then ""
    else
      val h = pts.head
      val start = s"M ${num(h.x)} ${num(baseY)} L ${num(h.x)} ${num(h.y)}"
      if pts.length == 1 then s"$start L ${num(h.x + 1)} ${num(h.y)} L ${num(h.x + 1)} ${num(baseY)} Z"
      else s"$start${curve(pts)} L ${num(pts.last.x)} ${num(baseY)} Z"

  /** Pure: the point nearest screen x `x` (points in increasing x), by binary search — the hover's. */
  def nearest(pts: IndexedSeq[Pt], x: com.histmersion.web.TimelineCurve.NearestX): Option[Pt] =
    if pts.isEmpty then None
    else
      var lo = 0
      var hi = pts.length - 1
      while lo < hi do
        val mid = (lo + hi) >>> 1
        if pts(mid).x < x then lo = mid + 1 else hi = mid
      val right = pts(lo)
      if lo > 0 && math.abs(pts(lo - 1).x - x) <= math.abs(right.x - x) then Some(pts(lo - 1)) else Some(right)
