package com.histmersion.web

import com.histmersion.web.map.{Clustering, LatLon, Spiderfy}

class SpiderfySuite extends munit.FunSuite:

  private def dist(a: (Double, Double), b: (Double, Double)) = math.hypot(a._1 - b._1, a._2 - b._2)

  test("a few dots go on one ring around the spot, a marker apart or more, none on the spot") {
    for n <- 2 to Spiderfy.RingMax do
      val o = Spiderfy.offsets(n, 30)
      assertEquals(o.size, n)
      val radii = o.map((x, y) => math.hypot(x, y))
      radii.foreach(r => assertEqualsDouble(r, radii.head, 1e-9))
      assert(radii.head >= 30 - 1e-9, s"n=$n: on the spot's own marker")
      for i <- o.indices; j <- o.indices if i < j do
        assert(dist(o(i), o(j)) >= 30 - 1e-9, s"n=$n: ${o(i)} and ${o(j)} overlap")
  }

  test("more dots go on a spiral, each clear of every other") {
    for n <- List(9, 15, 40) do
      val o = Spiderfy.offsets(n, 30)
      assertEquals(o.size, n)
      assert(o.forall((x, y) => math.hypot(x, y) >= 30 - 1e-9), s"n=$n: a dot on the spot")
      for i <- o.indices; j <- o.indices if i < j do
        assert(dist(o(i), o(j)) >= 30 * 0.9, s"n=$n: ${o(i)} and ${o(j)} overlap (${dist(o(i), o(j))})")
  }

  test("none or one") {
    assertEquals(Spiderfy.offsets(0, 30), Vector.empty)
    assertEquals(Spiderfy.offsets(1, 30).size, 1)
  }

  test("the legs: a line from the centre of the square to each dot") {
    val svg = Spiderfy.legsSvg(Vector((10.0, 0.0), (0.0, -10.0)), 24)
    assert(svg.startsWith("""<svg width="24" height="24" viewBox="0 0 24 24""""), svg)
    assert(svg.contains("""<line x1="12" y1="12" x2="22" y2="12"/>"""), svg)
    assert(svg.contains("""<line x1="12" y1="12" x2="12" y2="2"/>"""), svg)
  }

  test("one spot: a bubble no zoom parts") {
    assert(Clustering.onePlace(Seq(LatLon(34.5576, 36.52), LatLon(34.5576, 36.52), LatLon(34.5576, 36.52))))
    assert(!Clustering.onePlace(Seq(LatLon(34.5576, 36.52), LatLon(34.5577, 36.52))))
    assert(!Clustering.onePlace(Nil))
  }

  test("a bubble is fanned out when it holds dots only, and not too many") {
    assert(MapView.spiderfies(4, holdsCell = false))
    assert(!MapView.spiderfies(4, holdsCell = true), "a tile's counted cell is no one dot")
    assert(!MapView.spiderfies(1, holdsCell = false))
    assert(!MapView.spiderfies(ui.Layout.spiderMax + 1, holdsCell = false))
  }
