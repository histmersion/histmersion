package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.TimeBucket
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class TimelineCurveSuite extends munit.FunSuite:
  import TimelineCurve.*

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def buckets(counts: Int*): List[TimeBucket] =
    counts.toList.zipWithIndex.map((c, i) =>
      TimeBucket(TimeBucket.Start(i * 10L), TimeBucket.End(i * 10L + 9), TimeBucket.Count(c))
    )

  private val base = ScreenPixels(40.0)
  private val top = ScreenPixels(4.0)

  test("a point per bucket at its middle year, the largest count at the top, none on the baseline") {
    val pts = points(buckets(0, 5, 10), Year.Astronomical(0L), Year.Astronomical(30L), ElementSize(300.0), base, top)
    assertEquals(pts.map(_.x), Vector(45.0, 145.0, 245.0))
    assertEquals(pts.map(_.y), Vector(base, ScreenPixels((base.value + top.value) / 2), top))
  }

  test("no counts: no points (nothing to draw)") {
    assertEquals(
      points(buckets(0, 0, 0), Year.Astronomical(0L), Year.Astronomical(30L), ElementSize(300.0), base, top),
      Vector.empty
    )
    assertEquals(
      points(Nil, Year.Astronomical(0L), Year.Astronomical(30L), ElementSize(300.0), base, top),
      Vector.empty
    )
  }

  test("the curve passes through every point and is smooth there (one tangent each side)") {
    val pts = points(
      buckets(1, 4, 2, 9, 3, 3, 7, 0, 5),
      Year.Astronomical(0L),
      Year.Astronomical(90L),
      ElementSize(450.0),
      base,
      top
    )
    val segs = segments(pts)
    assertEquals(segs.length, pts.length - 1)
    segs.zip(pts.tail).foreach { (s, p) =>
      assertEqualsDouble(s.x.value, p.x.value, 1e-9); assertEqualsDouble(s.y.value, p.y.value, 1e-9)
    }
    // C1: the control points either side of an inner point are collinear with it
    segs.zip(segs.tail).foreach { (a, b) =>
      val inSlope = (a.y.value - a.c2y.value) / (a.x.value - a.c2x.value)
      val outSlope = (b.c1y.value - a.y.value) / (b.c1x.value - a.x.value)
      assertEqualsDouble(inSlope, outSlope, 1e-9)
    }
  }

  test("monotone: no segment leaves the band of its two counts — no overshoot, never under the baseline") {
    // spiky data with plateaus and zeros, where an ordinary cubic spline overshoots
    val counts = Seq(0, 0, 12, 0, 1, 1, 1, 30, 29, 0, 0, 5, 40, 2, 0, 0, 0, 17, 17, 3)
    val pts = points(
      buckets(counts*),
      Year.Astronomical(0L),
      Year.Astronomical(counts.length * 10L),
      ElementSize(1000.0),
      base,
      top
    )
    val segs = segments(pts)
    pts.zip(pts.tail).zip(segs).foreach { case ((p0, p1), s) =>
      val lo = math.min(p0.y.value, p1.y.value) - 1e-9
      val hi = math.max(p0.y.value, p1.y.value) + 1e-9
      // a Bézier stays inside its control points' hull: these inside the band keep the curve inside
      assert(s.c1y.value >= lo && s.c1y.value <= hi, s"control 1 of ${p0.x}→${p1.x} at ${s.c1y} outside [$lo, $hi]")
      assert(s.c2y.value >= lo && s.c2y.value <= hi, s"control 2 of ${p0.x}→${p1.x} at ${s.c2y} outside [$lo, $hi]")
      assert(s.c1y.value <= base.value + 1e-9 && s.c2y.value <= base.value + 1e-9, "the curve dips under the baseline")
      // and the controls stay between the two x's (the curve never turns back)
      assert(s.c1x >= p0.x && s.c2x <= p1.x)
    }
  }

  test("a flat run is drawn flat, and a peak is a level top") {
    val pts =
      points(buckets(3, 3, 3, 9, 3), Year.Astronomical(0L), Year.Astronomical(50L), ElementSize(250.0), base, top)
    val t = tangents(pts.map(_.x), pts.map(_.y))
    assertEqualsDouble(t(1), 0.0, 1e-12) // inside the plateau
    assertEqualsDouble(t(3), 0.0, 1e-12) // the peak
  }

  test("the paths: an area closed on the baseline, a line through the points; one point is a sliver") {
    val pts = points(buckets(2, 4), Year.Astronomical(0L), Year.Astronomical(20L), ElementSize(200.0), base, top)
    val area = areaPath(pts, base)
    assert(area.startsWith("M 45 40 L 45 22 C "), area)
    assert(area.endsWith(" L 145 40 Z"), area)
    assert(linePath(pts).startsWith("M 45 22 C "))
    assertEquals(
      areaPath(
        points(buckets(2), Year.Astronomical(0L), Year.Astronomical(10L), ElementSize(100.0), base, top),
        base
      ),
      "M 45 40 L 45 4 L 46 4 L 46 40 Z"
    )
    assertEquals(areaPath(Vector.empty, base), "")
  }

  test("nearest: the point closest to a screen x, either side") {
    val pts = points(
      buckets(1, 2, 3, 4),
      Year.Astronomical(0L),
      Year.Astronomical(40L),
      ElementSize(400.0),
      base,
      top
    ) // x = 45, 145, 245, 345
    assertEquals(nearest(pts, ScreenPixels(-10)).map(_.x), Some(45.0))
    assertEquals(nearest(pts, ScreenPixels(94)).map(_.x), Some(45.0))
    assertEquals(nearest(pts, ScreenPixels(96)).map(_.x), Some(145.0))
    assertEquals(nearest(pts, ScreenPixels(1000)).map(_.x), Some(345.0))
    assertEquals(nearest(Vector.empty, ScreenPixels(10)), None)
  }

  test("the hover follows a curve moved on screen (a pan or zoom before its redraw)") {
    val pts =
      points(buckets(1, 2, 3, 4), Year.Astronomical(0L), Year.Astronomical(40L), ElementSize(400.0), base, top)
    // drawn at 400 px, now shown at half the width, shifted 100 px right
    val Some((x, p)) = TimelineView.hoverAt(pts, 100 + 125, 100, 0.5): @unchecked
    assertEqualsDouble(p.x.value, 245.0, 1e-9)
    assertEqualsDouble(x, 222.5, 1e-9)
    assertEquals(TimelineView.hoverAt(pts, 10, 0, 0), None)
  }

  test("the hover says the bucket's years and its exact count") {
    assertEquals(
      TimelineView.hoverText(TimeBucket(TimeBucket.Start(-1200L), TimeBucket.End(-1151L), TimeBucket.Count(12))),
      s"${Fmt.yr(Year(-1200L))}–${Fmt.yr(Year(-1151L))}: 12 sources"
    )
    assertEquals(
      TimelineView.hoverText(TimeBucket(TimeBucket.Start(500L), TimeBucket.End(500L), TimeBucket.Count(1))),
      s"${Fmt.yr(Year(500L))}: 1 source"
    )
  }

  test("buckets are a few pixels wide: ~5 px each on a wide strip, never more than the limit") {
    val size = TimelineView.bucketSizeFor(Year.Shift(3000L), 1000.0)
    val n = math.ceil(3000.0 / size.value)
    assert(n >= 1000.0 / 6 && n <= 1000.0 / 4, s"$n buckets over 1000 px")
  }
