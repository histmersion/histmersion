package com.histmersion.web

import com.histmersion.web.LabelPlacement.*

/** #1442 — every region and polity is named inside its shape, sized by zoom, thinned by collisions
  * (larger shapes first). The placement is pure; these are its rules. */
class LabelPlacementSuite extends munit.FunSuite:

  private def ring(pts: (Double, Double)*): Vector[Pt] = pts.map((x, y) => Pt(x, y)).toVector

  test("projection: the equator and the prime meridian are the plane's centre, and it round-trips"):
    val c = project(0, 0)
    assertEqualsDouble(c.x, 128.0, 1e-9)
    assertEqualsDouble(c.y, 128.0, 1e-9)
    for (lat, lon) <- List((35.1, 33.4), (-33.9, 151.2), (64.1, -21.9)) do
      val (la, lo) = unproject(project(lat, lon))
      assertEqualsDouble(la, lat, 1e-9)
      assertEqualsDouble(lo, lon, 1e-9)
    assert(project(60, 0).y < project(10, 0).y, "north is up: y grows southwards")

  test("area: a square, a square with a hole, and a degenerate ring"):
    val sq = ring((0, 0), (10, 0), (10, 10), (0, 10))
    assertEqualsDouble(ringArea(sq), 100.0, 1e-9)
    assertEqualsDouble(ringArea(sq.reverse), 100.0, 1e-9, "orientation does not matter")
    assertEqualsDouble(polyArea(Vector(sq, ring((2, 2), (4, 2), (4, 4), (2, 4)))), 96.0, 1e-9)
    assertEqualsDouble(ringArea(ring((0, 0), (1, 1))), 0.0, 1e-9)

  test("a square's name goes in its middle"):
    val p = anchor(Vector(ring((0, 0), (10, 0), (10, 10), (0, 10), (0, 0))))
    assertEqualsDouble(p.x, 5.0, 0.15)
    assertEqualsDouble(p.y, 5.0, 0.15)

  test("a U-shaped state is named inside one of its arms, never in the bay its centroid falls in"):
    // a U: two 2-wide arms joined by a 2-high base; the centroid (5, ~4) is in the open middle
    val u = Vector(ring((0, 0), (10, 0), (10, 10), (8, 10), (8, 2), (2, 2), (2, 10), (0, 10)))
    val p = anchor(u)
    assert(signedDistance(p.x, p.y, u) > 0, s"$p is outside the shape")
    assert(signedDistance(p.x, p.y, u) >= 0.9, s"$p hugs an edge")

  test("a hole is avoided: a ring-shaped polygon is named in the ring, not the hole"):
    val donut = Vector(ring((0, 0), (12, 0), (12, 12), (0, 12)), ring((3, 3), (9, 3), (9, 9), (3, 9)))
    val p = anchor(donut)
    assert(signedDistance(p.x, p.y, donut) > 0, s"$p falls in the hole")

  test("thinning keeps at most the limit, starting from the first point; short rings are untouched"):
    val long = (0 until 1000).map(i => Pt(i.toDouble, 0)).toVector
    val t = thin(long, 400)
    assert(t.length <= 400, t.length.toString)
    assertEquals(t.head, long.head)
    val short = long.take(10)
    assertEquals(thin(short, 400), short)

  test("a multipolygon is named in its largest part; its area is all the parts"):
    // Cyprus-sized island and a large mainland part
    val small = Seq(Seq((35.0, 33.0), (35.0, 34.0), (35.5, 34.0), (35.5, 33.0)))
    val large = Seq(Seq((36.0, 28.0), (36.0, 40.0), (41.0, 40.0), (41.0, 28.0)))
    val s = shape(Seq(small, large)).get
    assert(s.lat > 36 && s.lat < 41 && s.lon > 28 && s.lon < 40, s.toString)
    val only = shape(Seq(large)).get
    assert(s.area0 > only.area0)
    assertEqualsDouble(s.width0, only.width0, 1e-9)
    assertEquals(shape(Nil), None)
    assertEquals(shape(Seq(Seq(Seq((1.0, 1.0), (2.0, 2.0))))), None, "a ring of two points names nothing")

  test("tier: a shape too small on screen gets no name; bigger on screen, bigger name"):
    val area0 = 1.0   // 1 px² at zoom 0 → side 2^z px
    assertEquals(tier(area0, 4), None)                 // 16 px
    assertEquals(tier(area0, 6), Some(Tier.S))         // 64 px
    assertEquals(tier(area0, 8), Some(Tier.M))         // 256 px
    assertEquals(tier(area0, 9), Some(Tier.L))         // 512 px
    // zooming in never shrinks a name
    val tiers = (0 to 12).flatMap(z => tier(0.05, z)).map(_.ordinal)
    assertEquals(tiers, tiers.sorted)

  test("fits: a long name over a narrow shape is dropped until the zoom gives it room"):
    assert(!fits(120, 10.0 / 1024, 10))   // 10 px wide on screen
    assert(fits(120, 100.0 / 1024, 10))   // 100 px: overhang allowed
    assert(fits(120, 10.0 / 1024, 14))    // zoomed in 16x: 160 px

  test("cull: larger shapes win a collision; apart, everyone stays"):
    val big   = Box("big", 100, 100, 80, 14, rank = 50)
    val small = Box("small", 120, 104, 60, 12, rank = 5)
    val far   = Box("far", 400, 400, 60, 12, rank = 1)
    assertEquals(cull(Seq(small, big, far)), Set("big", "far"))
    // just past the padding: both stay
    val beside = small.copy(cx = 100 + 40 + 30 + PadPx + 1)
    assertEquals(cull(Seq(big, beside)), Set("big", "small"))

  test("cull: the answer does not depend on input order, and ties go by key"):
    val a = Box("a", 0, 0, 50, 10, rank = 1)
    val b = Box("b", 10, 0, 50, 10, rank = 1)
    assertEquals(cull(Seq(a, b)), Set("a"))
    assertEquals(cull(Seq(b, a)), Set("a"))

  test("cull: boxes straddling grid cells still collide; a big label blocks many small ones"):
    val wide = Box("wide", 128, 128, 400, 20, rank = 100)
    val under = (0 until 10).map(i => Box(s"s$i", 0.0 + i * 30, 130, 20, 10, rank = 1))
    assertEquals(cull(wide +: under, cell = 64), Set("wide"))

  test("cull of 400 labels stays well under a frame"):
    val rnd = scala.util.Random(7)
    val boxes = (0 until 400).map(i => Box(s"k$i", rnd.nextDouble() * 1600, rnd.nextDouble() * 900,
      40 + rnd.nextDouble() * 120, 14, rnd.nextDouble()))
    val t0 = System.nanoTime()
    val kept = cull(boxes)
    val ms = (System.nanoTime() - t0) / 1e6
    assert(kept.nonEmpty && kept.size < 400)
    assert(ms < 50, s"cull took $ms ms")

  test("placing a 20 000-point outline stays bounded (thinned, probe-capped)"):
    val n = 20000
    val circle = (0 until n).map { i =>
      val a = 2 * math.Pi * i / n
      (35.0 + 5 * math.sin(a) + 0.01 * math.sin(a * 300), 30.0 + 8 * math.cos(a))
    }
    val t0 = System.nanoTime()
    val s = shape(Seq(Seq(circle))).get
    val ms = (System.nanoTime() - t0) / 1e6
    assertEqualsDouble(s.lat, 35.0, 0.6)
    assertEqualsDouble(s.lon, 30.0, 0.6)
    assert(ms < 100, s"placing took $ms ms")
