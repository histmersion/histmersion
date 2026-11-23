package com.histmersion
package web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Geo.{Latitude, Longitude}
import com.histmersion.web.LabelPlacement.*

/** #1442 / #1447 — every region and polity is named at its shape's centre or, near the screen edge, in its visible
  * part; sized by the whole shape on screen; hidden only by collisions (larger shapes first). The placement is pure;
  * these are its rules.
  */
class LabelPlacementSuite extends munit.FunSuite:

  private def ring(pts: (Double, Double)*): Vector[Pt] = pts.map((x, y) => Pt(x, y)).toVector

  test("projection: the equator and the prime meridian are the plane's centre, and it round-trips"):
    val c = project(Latitude(0), Longitude(0))
    assertEqualsDouble(c.x, 128.0, 1e-9)
    assertEqualsDouble(c.y, 128.0, 1e-9)
    for (lat, lon) <- List(
        (Latitude(35.1), Longitude(33.4)),
        (Latitude(-33.9), Longitude(151.2)),
        (Latitude(64.1), Longitude(-21.9))
      )
    do
      val (la, lo) = unproject(project(lat, lon))
      assertEqualsDouble(la.value, lat.value, 1e-9)
      assertEqualsDouble(lo.value, lon.value, 1e-9)
    assert(
      project(Latitude(60), Longitude(0)).y < project(Latitude(10), Longitude(0)).y,
      "north is up: y grows southwards"
    )

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

  test("a multipolygon is named in its largest part when that part is most of its land; its size is all the parts"):
    // a Cyprus-sized island and a large mainland part
    val small = Seq(
      Seq(
        (Latitude(35.0), Longitude(33.0)),
        (Latitude(35.0), Longitude(34.0)),
        (Latitude(35.5), Longitude(34.0)),
        (Latitude(35.5), Longitude(33.0))
      )
    )
    val large = Seq(
      Seq(
        (Latitude(36.0), Longitude(28.0)),
        (Latitude(36.0), Longitude(40.0)),
        (Latitude(41.0), Longitude(40.0)),
        (Latitude(41.0), Longitude(28.0))
      )
    )
    val s = shape(Seq(small, large)).get
    assert(s.lat.value > 36 && s.lat.value < 41 && s.lon.value > 28 && s.lon.value < 40, s.toString)
    assert(!s.archipelago)
    val only = shape(Seq(large)).get
    assert(s.size0 > only.size0)
    assertEquals(s.parts.length, 2, "either part can take the name when the other is off screen")
    assertEquals(shape(Nil), None)
    assertEquals(
      shape(Seq(Seq(Seq((Latitude(1.0), Longitude(1.0)), (Latitude(2.0), Longitude(2.0)))))),
      None,
      "a ring of two points names nothing"
    )

  /** A 4 × 4 grid of small square islands, 0.2° a side, 1° apart — no island is half the land. */
  private val islands: Seq[Seq[Seq[(Latitude, Longitude)]]] =
    for i <- 0 until 4; j <- 0 until 4 yield
      val la = 36.0 + i
      val lo = 24.0 + j
      Seq(
        Seq(
          (Latitude(la), Longitude(lo)),
          (Latitude(la), Longitude(lo + 0.2)),
          (Latitude(la + 0.2), Longitude(lo + 0.2)),
          (Latitude(la + 0.2), Longitude(lo))
        )
      )

  test("an archipelago is named among its islands, at the middle of their hull, and is as big as the hull"):
    val s = shape(islands).get
    assert(s.archipelago)
    assertEqualsDouble(s.lat.value, 37.6, 0.15)
    assertEqualsDouble(s.lon.value, 25.6, 0.15)
    val land = islands.map(p => polyArea(Vector(p.head.map((la, lo) => project(la, lo)).toVector))).sum
    assert(s.size0 > 10 * land, s"the sea between the islands counts: ${s.size0} vs $land")
    assertEquals(s.parts.length, 1, "it moves within its hull")

  test("an archipelago is named at a zoom where its largest island alone was too small (#1447)"):
    val s = shape(islands).get
    val one = shape(Seq(islands.head)).get
    val z = 5.0
    // the 1442 rule: a shape under 44 px a side on screen was not named — each island is ~4 px here
    assert(math.sqrt(one.size0) * math.pow(2, z) < 44.0)
    assert(tier(s, z).isDefined, "named")
    val c = s.home
    val scale = math.pow(2, z)
    val view = Rect(c.x - 600 / scale, c.y - 400 / scale, c.x + 600 / scale, c.y + 400 / scale)
    assertEquals(place(s, view, scale, 60, 8), Some(s.home))

  test("hull: the corners of a square with points inside and on its edges"):
    val pts = Seq(Pt(0, 0), Pt(4, 0), Pt(4, 4), Pt(0, 4), Pt(2, 2), Pt(2, 0), Pt(1, 3))
    assertEquals(hull(pts).toSet, Set(Pt(0, 0), Pt(4, 0), Pt(4, 4), Pt(0, 4)))
    assertEqualsDouble(ringArea(hull(pts)), 16.0, 1e-9)
    assertEquals(hull(Seq(Pt(0, 0), Pt(1, 1))).length, 2)

  test("clip: a square to a rectangle; a concave shape keeps its area; a ring outside is gone"):
    val sq = Vector(ring((0, 0), (10, 0), (10, 10), (0, 10)))
    val c = clip(sq, Rect(5, -5, 20, 5))
    assertEqualsDouble(polyArea(c), 25.0, 1e-9)
    val u = Vector(ring((0, 0), (10, 0), (10, 10), (8, 10), (8, 2), (2, 2), (2, 10), (0, 10)))
    assertEqualsDouble(polyArea(clip(u, Rect(-1, 5, 11, 11))), 2 * 2 * 5.0, 1e-9)
    assertEquals(clip(sq, Rect(20, 20, 30, 30)), Vector.empty)
    val donut = Vector(ring((0, 0), (12, 0), (12, 12), (0, 12)), ring((3, 3), (9, 3), (9, 9), (3, 9)))
    assertEqualsDouble(polyArea(clip(donut, Rect(0, 0, 6, 12))), 72.0 - 18.0, 1e-9)

  test("tier: the size of a name follows the whole shape on screen; only a shape a few pixels across has none"):
    val size0 = 1.0 // 1 px² at zoom 0 → side 2^z px
    assertEquals(tier(size0, 1.0, 2), None) // 4 px across
    assertEquals(tier(size0, 1.0, 3), Some(Tier.S)) // 8 px: named, small — the collisions decide
    assertEquals(tier(size0, 1.0, 6), Some(Tier.S))
    assertEquals(tier(size0, 1.0, 8), Some(Tier.M)) // 256 px
    assertEquals(tier(size0, 1.0, 9), Some(Tier.L)) // 512 px
    // zooming in never shrinks a name
    val tiers = (0 to 12).flatMap(z => tier(0.05, 0.3, z)).map(_.ordinal)
    assertEquals(tiers, tiers.sorted)
    // a narrow shape (tiny area, long) is named by its extent, never dropped for being thin
    assert(tier(1e-6, 1.0, 4).isDefined)

  // ── placement in a viewport ──────────────────────────────────────────────────────────────────────

  /** A 5° × 8° box around (35, 30) and a 1200 × 800 px viewport at zoom 6 centred on `c`. */
  private val box = shape(
    Seq(
      Seq(
        Seq(
          (Latitude(32.5), Longitude(26.0)),
          (Latitude(32.5), Longitude(34.0)),
          (Latitude(37.5), Longitude(34.0)),
          (Latitude(37.5), Longitude(26.0))
        )
      )
    )
  ).get
  private val z6 = math.pow(2, 6)
  private def viewAt(c: Pt, w: Double = 1200, h: Double = 800, scale: Double = z6): Rect =
    Rect(c.x - w / 2 / scale, c.y - h / 2 / scale, c.x + w / 2 / scale, c.y + h / 2 / scale)

  /** Is a `halfW` × `halfH` name at `p` wholly inside `view`? */
  private def onScreen(p: Pt, view: Rect, halfW: Double, halfH: Double, scale: Double = z6): Boolean =
    p.x - halfW / scale >= view.minX && p.x + halfW / scale <= view.maxX &&
      p.y - halfH / scale >= view.minY && p.y + halfH / scale <= view.maxY

  test("place: the centre on screen → the name is at the centre, and stays there through small pans"):
    for dx <- Seq(-200.0, -50.0, 0.0, 120.0, 300.0) do
      val v = viewAt(Pt(box.home.x + dx / z6, box.home.y + dx / 2 / z6))
      assertEquals(place(box, v, z6, 50, 8, prev = Some(Pt(0, 0))), Some(box.home), s"pan $dx")

  test("place: the centre off screen → the name moves into the visible part, whole on screen"):
    // the viewport's left edge cuts the box 150 px in from its right side
    val right = project(Latitude(35.0), Longitude(34.0))
    val v = Rect(right.x - 150 / z6, right.y - 400 / z6, right.x - 150 / z6 + 1200 / z6, right.y + 400 / z6)
    assert(!v.contains(box.home))
    val p = place(box, v, z6, 50, 8).get
    assert(onScreen(p, v, 50, 8), s"$p is not wholly on screen")
    val outline = box.parts.head.rings
    assert(signedDistance(p.x, p.y, outline) > 0, "on the shape")
    // the centre near the edge: on screen, but the name would not fit there → it moves inward
    val near =
      Rect(box.home.x - 20 / z6, box.home.y - 400 / z6, box.home.x - 20 / z6 + 1200 / z6, box.home.y + 400 / z6)
    assert(near.contains(box.home))
    val q = place(box, near, z6, 50, 8).get
    assert(q != box.home && onScreen(q, near, 50, 8), q.toString)

  test("place: a name moved off its centre stays put on a small pan; a name with no room on screen is hidden"):
    val right = project(Latitude(35.0), Longitude(34.0))
    def v(shift: Double) = Rect(
      right.x - (150 + shift) / z6,
      right.y - 400 / z6,
      right.x - (150 + shift) / z6 + 1200 / z6,
      right.y + 400 / z6
    )
    val p = place(box, v(0), z6, 50, 8).get
    assertEquals(place(box, v(10), z6, 50, 8, prev = Some(p)), Some(p))
    // only 30 px of the shape is on screen: the name is pulled on screen, still over that sliver
    val sliver = Rect(right.x - 30 / z6, right.y - 400 / z6, right.x - 30 / z6 + 1200 / z6, right.y + 400 / z6)
    val q = place(box, sliver, z6, 50, 8).get
    assert(onScreen(q, sliver, 50, 8), q.toString)
    assert((q.x - 50 / z6) < right.x, "the name overlaps what is left of the shape")
    // the shape wholly off screen
    assertEquals(place(box, viewAt(Pt(right.x + 1000 / z6, right.y)), z6, 50, 8), None)

  test("place: a small island in the band along the screen edge keeps its name beside it"):
    val isle = shape(
      Seq(
        Seq(
          Seq(
            (Latitude(35.0), Longitude(30.0)),
            (Latitude(35.0), Longitude(30.1)),
            (Latitude(35.1), Longitude(30.1)),
            (Latitude(35.1), Longitude(30.0))
          )
        )
      )
    ).get
    val c = isle.home
    // the island 20 px from the left edge; a 100 px name cannot be centred on it and stay on screen
    val v = Rect(c.x - 20 / z6, c.y - 400 / z6, c.x - 20 / z6 + 1200 / z6, c.y + 400 / z6)
    val p = place(isle, v, z6, 50, 8).get
    assert(onScreen(p, v, 50, 8), p.toString)
    assert(math.abs(p.x - c.x) * z6 <= 50 + NearPx, "beside its island")

  test("collision still culls: two placed names that overlap keep the larger shape's"):
    val big = shape(
      Seq(
        Seq(
          Seq(
            (Latitude(30.0), Longitude(20.0)),
            (Latitude(30.0), Longitude(40.0)),
            (Latitude(40.0), Longitude(40.0)),
            (Latitude(40.0), Longitude(20.0))
          )
        )
      )
    ).get
    val small = shape(
      Seq(
        Seq(
          Seq(
            (Latitude(35.0), Longitude(29.5)),
            (Latitude(35.0), Longitude(30.5)),
            (Latitude(35.5), Longitude(30.5)),
            (Latitude(35.5), Longitude(29.5))
          )
        )
      )
    ).get
    val z = 4.0
    val scale = math.pow(2, z)
    val v = viewAt(big.home, scale = scale)
    val boxes = Seq("big" -> big, "small" -> small).flatMap { (k, s) =>
      place(s, v, scale, 60, 8).map(p => Box(k, p.x * scale, p.y * scale, 120, 16, s.size0))
    }
    assertEquals(boxes.length, 2, "both have a place")
    assertEquals(cull(boxes), Set("big"))

  test("placing 140 shapes in a viewport, many cut by its edges, stays well under a frame"):
    val rnd = scala.util.Random(11)
    val shapes = (0 until 140).map { _ =>
      val la = 30 + rnd.nextDouble() * 20
      val lo = 10 + rnd.nextDouble() * 30
      val n = 300
      val ring = (0 until n).map { i =>
        val a = 2 * math.Pi * i / n
        val r = 1 + 0.3 * math.sin(a * 7)
        (Latitude(la + r * math.sin(a)), Longitude(lo + 1.5 * r * math.cos(a)))
      }
      shape(Seq(Seq(ring))).get
    }
    val v = viewAt(project(Latitude(40), Longitude(25)), 1400, 900, math.pow(2, 5))
    def pass() = shapes.flatMap(s => place(s, v, math.pow(2, 5), 50, 8))
    (0 until 5).foreach(_ => pass()) // warm the JIT
    val t0 = System.nanoTime()
    val placedN = pass().length
    val ms = (System.nanoTime() - t0) / 1e6
    assert(placedN > 20, placedN.toString)
    assert(ms < 20, s"placing took $ms ms")

  test("glide: the centre on screen → the centre; else the last exact place pulled into what is on screen"):
    val v = viewAt(box.home)
    assertEquals(glide(box, v, z6, 50, 8, None), Some(box.home))
    // panned so the centre is off the left edge: the name heads for the visible part, whole on screen
    val right = project(Latitude(35.0), Longitude(34.0))
    val off = Rect(right.x - 150 / z6, right.y - 400 / z6, right.x - 150 / z6 + 1200 / z6, right.y + 400 / z6)
    val g = glide(box, off, z6, 50, 8, Some(box.home)).get
    assert(onScreen(g, off, 50, 8), g.toString)
    assert(
      g.x <= right.x && g.y >= project(Latitude(37.5), Longitude(30)).y && g.y <= project(
        Latitude(32.5),
        Longitude(30)
      ).y,
      "within the shape's box"
    )
    // the exact place of a settled view is a fixed point: a frame does not move it
    val exact = place(box, off, z6, 50, 8).get
    assertEquals(glide(box, off, z6, 50, 8, Some(exact)), Some(exact))
    // nothing of the shape on screen → nowhere
    assertEquals(glide(box, viewAt(Pt(right.x + 1000 / z6, right.y)), z6, 50, 8, Some(exact)), None)

  test("glide: a pan moves the target continuously — no step larger than the pan itself"):
    val right = project(Latitude(35.0), Longitude(34.0))
    var prev = Option.empty[Pt]
    for i <- 0 to 60 do
      val shift = 300.0 + i * 5 // the left edge sweeps across the shape's right side, 5 px a frame
      val v = Rect(right.x - shift / z6, right.y - 400 / z6, right.x - shift / z6 + 1200 / z6, right.y + 400 / z6)
      val g = glide(box, v, z6, 50, 8, Some(box.home))
      g.foreach { p =>
        prev.foreach(q => assert(math.hypot(p.x - q.x, p.y - q.y) * z6 <= 5.0 + 1e-6, s"jump at $i: $q → $p"))
      }
      prev = g.orElse(prev)

  test("cull hysteresis: a shown name keeps its slot until the overlap is real; a hidden one returns only when clear"):
    val big = Box("big", 100, 100, 80, 14, rank = 50)
    // 4 px of clear space: more than PadPx, less than PadPx + AppearPadPx
    val near = Box("near", 100 + 40 + 30 + PadPx + 4, 100, 60, 12, rank = 5)
    assertEquals(cull(Seq(big, near), shown = Set("big", "near")), Set("big", "near"), "shown: it stays")
    assertEquals(cull(Seq(big, near), shown = Set("big")), Set("big"), "hidden: it waits until clearly apart")
    val clear = near.modify(_.cx).setTo((100 + 40 + 30 + PadPx + AppearPadPx + 1: Double))
    assertEquals(cull(Seq(big, clear), shown = Set("big")), Set("big", "clear".replace("clear", "near")))
    // two names drifting together then apart: shown, hidden at the touch, back only past the margin
    val states =
      (0 to 40).map(i => 100 + 40 + 30 + PadPx + 20 - i).map(cx => near.modify(_.cx).setTo((cx.toDouble: Double)))
    var shown = Set("big", "near")
    val trace = (states ++ states.reverse).map { b =>
      shown = cull(Seq(big, b), shown = shown)
      shown("near")
    }
    val flips = trace.sliding(2).count(w => w(0) != w(1))
    assertEquals(flips, 2, s"hidden once, shown once: $trace")

  test("cull: larger shapes win a collision; apart, everyone stays"):
    val big = Box("big", 100, 100, 80, 14, rank = 50)
    val small = Box("small", 120, 104, 60, 12, rank = 5)
    val far = Box("far", 400, 400, 60, 12, rank = 1)
    assertEquals(cull(Seq(small, big, far)), Set("big", "far"))
    // just past the padding: both stay
    val beside = small.modify(_.cx).setTo((100 + 40 + 30 + PadPx + 1: Double))
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

  test(
    "cull of 400 labels stays well under a frame: a few thousand box tests, growing with the labels, not their square"
  ):
    def counted(n: Int, side: Double, cell: Double = 128.0): (Set[String], Int) =
      val rnd = scala.util.Random(7)
      val boxes = (0 until n).map(i =>
        Box(
          s"k$i",
          rnd.nextDouble() * 1600 * side,
          rnd.nextDouble() * 900 * side,
          40 + rnd.nextDouble() * 120,
          14,
          rnd.nextDouble()
        )
      )
      var tests = 0
      val kept = cull(boxes, cell, tested = () => tests += 1)
      (kept, tests)
    val (kept, tests) = counted(400, 1)
    assert(kept.nonEmpty && kept.size < 400)
    val (_, wider) = counted(1600, 2)
    val (same, oneCell) = counted(400, 1, cell = 1e9)
    assert(tests < 3000 && wider < 5 * tests, s"400 labels: $tests box tests; 1600 at the same density: $wider")
    assertEquals(same, kept)
    assert(oneCell > 3000 && oneCell > 10 * tests, s"without the grid (one cell): $oneCell box tests against $tests")

  test("placing a 20 000-point outline stays bounded (thinned, probe-capped)"):
    val n = 20000
    val circle = (0 until n).map { i =>
      val a = 2 * math.Pi * i / n
      (Latitude(35.0 + 5 * math.sin(a) + 0.01 * math.sin(a * 300)), Longitude(30.0 + 8 * math.cos(a)))
    }
    val t0 = System.nanoTime()
    val s = shape(Seq(Seq(circle))).get
    val ms = (System.nanoTime() - t0) / 1e6
    assertEqualsDouble(s.lat.value, 35.0, 0.6)
    assertEqualsDouble(s.lon.value, 30.0, 0.6)
    assert(ms < 100, s"placing took $ms ms")
