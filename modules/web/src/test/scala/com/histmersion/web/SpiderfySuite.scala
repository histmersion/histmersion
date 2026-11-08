package com.histmersion
package web

import com.histmersion.web.map.{Clustering, LatLon, Spiderfy}
import com.histmersion.web.ui.Layout

class SpiderfySuite extends munit.FunSuite:

  private def dist(
      a: (Spiderfy.OffsetCoordinate, Spiderfy.OffsetCoordinate),
      b: (Spiderfy.OffsetCoordinate, Spiderfy.OffsetCoordinate)
  ) =
    math.hypot(a._1.value - b._1.value, a._2.value - b._2.value)

  test("a few dots go on one ring around the spot, a marker apart or more, none on the spot") {
    for n <- 2 to Spiderfy.RingMax do
      val o = Spiderfy.offsets(Spiderfy.Count(n), Spiderfy.Separation(30))
      assertEquals(o.size, n)
      val radii = o.map((x, y) => math.hypot(x.value, y.value))
      radii.foreach(r => assertEqualsDouble(r, radii.head, 1e-9))
      assert(radii.head >= 30 - 1e-9, s"n=$n: on the spot's own marker")
      for i <- o.indices; j <- o.indices if i < j do
        assert(dist(o(i), o(j)) >= 30 - 1e-9, s"n=$n: ${o(i)} and ${o(j)} overlap")
  }

  test("more dots go on a spiral, each clear of every other") {
    for n <- List(9, 15, 40) do
      val o = Spiderfy.offsets(Spiderfy.Count(n), Spiderfy.Separation(30))
      assertEquals(o.size, n)
      assert(o.forall((x, y) => math.hypot(x.value, y.value) >= 30 - 1e-9), s"n=$n: a dot on the spot")
      for i <- o.indices; j <- o.indices if i < j do
        assert(dist(o(i), o(j)) >= 30 * 0.9, s"n=$n: ${o(i)} and ${o(j)} overlap (${dist(o(i), o(j))})")
  }

  test("none or one") {
    assertEquals(Spiderfy.offsets(Spiderfy.Count(0), Spiderfy.Separation(30)), Vector.empty)
    assertEquals(Spiderfy.offsets(Spiderfy.Count(1), Spiderfy.Separation(30)).size, 1)
  }

  test("the legs: a line from the centre of the square to each dot") {
    val svg = Spiderfy.legsSvg(
      Vector(
        (Spiderfy.OffsetCoordinate(10.0), Spiderfy.OffsetCoordinate(0.0)),
        (Spiderfy.OffsetCoordinate(0.0), Spiderfy.OffsetCoordinate(-10.0))
      ),
      Spiderfy.Side(24)
    )
    assert(svg.value.startsWith("""<svg width="24" height="24" viewBox="0 0 24 24""""), svg.value)
    assert(svg.value.contains("""<line x1="12" y1="12" x2="22" y2="12"/>"""), svg.value)
    assert(svg.value.contains("""<line x1="12" y1="12" x2="12" y2="2"/>"""), svg.value)
  }

  test("one spot: a bubble no zoom parts") {
    assert(
      Clustering.onePlace(
        Seq(
          LatLon(LatLon.Lat(34.5576), LatLon.Lon(36.52)),
          LatLon(LatLon.Lat(34.5576), LatLon.Lon(36.52)),
          LatLon(LatLon.Lat(34.5576), LatLon.Lon(36.52))
        )
      )
    )
    assert(
      !Clustering.onePlace(
        Seq(LatLon(LatLon.Lat(34.5576), LatLon.Lon(36.52)), LatLon(LatLon.Lat(34.5577), LatLon.Lon(36.52)))
      )
    )
    assert(!Clustering.onePlace(Nil))
  }

  test("a bubble is fanned out when it holds dots only, and not too many") {
    assert(MapView.spiderfies(DotCluster.Total(4), holdsCell = MapView.HoldsCell(false)))
    assert(
      !MapView.spiderfies(DotCluster.Total(4), holdsCell = MapView.HoldsCell(true)),
      "a tile's counted cell is no one dot"
    )
    assert(!MapView.spiderfies(DotCluster.Total(1), holdsCell = MapView.HoldsCell(false)))
    assert(!MapView.spiderfies(DotCluster.Total(Layout.spiderMax.value + 1), holdsCell = MapView.HoldsCell(false)))
  }
