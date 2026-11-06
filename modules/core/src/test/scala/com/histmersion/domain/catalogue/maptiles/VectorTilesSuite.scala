package com.histmersion.domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given

/** The map's vector tiles, pure: the grid (which tiles exist, their bounds, where a point falls), the grouping rule,
  * and the writer read back as MapLibre reads it.
  */
class VectorTilesSuite extends munit.FunSuite:

  private def tile(z: Int, x: Long, y: Long) = TileCoord.of(z, x, y, com.histmersion.domain.catalogue.maptiles.TileCoord.Z(22)).toOption.get

  test("only a tile inside the grid exists, no deeper than the server cuts"):
    assert(TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(0), com.histmersion.domain.catalogue.maptiles.TileCoord.X(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(12)).isRight)
    assert(TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(2), com.histmersion.domain.catalogue.maptiles.TileCoord.X(3), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(3), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(12)).isRight)
    assert(TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(2), com.histmersion.domain.catalogue.maptiles.TileCoord.X(4), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(12)).isLeft)
    assert(TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(2), com.histmersion.domain.catalogue.maptiles.TileCoord.X(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(-1), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(12)).isLeft)
    assert(TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(13), com.histmersion.domain.catalogue.maptiles.TileCoord.X(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(12)).isLeft)
    assert(TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(-1), com.histmersion.domain.catalogue.maptiles.TileCoord.X(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(0), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(12)).isLeft)

  test("a tile's bounds: the world at zoom 0, a quarter at zoom 1"):
    val (w, s, e, n) = tile(0, 0, 0).bounds
    assertEqualsDouble(w, -180, 1e-9)
    assertEqualsDouble(e, 180, 1e-9)
    assertEqualsDouble(n, TileCoord.MaxLat, 1e-6)
    assertEqualsDouble(s, -TileCoord.MaxLat, 1e-6)
    val (w1, s1, e1, n1) = tile(1, 1, 0).bounds
    assertEqualsDouble(w1, 0, 1e-9)
    assertEqualsDouble(e1, 180, 1e-9)
    assertEqualsDouble(s1, 0, 1e-9)
    assertEqualsDouble(n1, TileCoord.MaxLat, 1e-6)

  test("a point falls in exactly one tile: its units are inside that tile's extent and outside its neighbour's"):
    val (lat, lon) = (37.97, 23.72) // Athens
    val z = 6
    val (mx, my) = TileCoord.mercator(lat, lon)
    val t = tile(z, math.floor(mx * (1 << z)).toLong, math.floor(my * (1 << z)).toLong)
    val (px, py) = t.unitsOf(lat, lon)
    assert(px >= 0 && px < TileCoord.Extent && py >= 0 && py < TileCoord.Extent, s"$px $py")
    val (qx, _) = tile(z, t.x + 1, t.y).unitsOf(lat, lon)
    assert(qx < 0)
    val (w, s, e, n) = t.bounds
    assert(lon >= w && lon < e && lat > s && lat <= n)

  test("the poles land on the grid's edge, not off it"):
    val (_, top) = tile(0, 0, 0).unitsOf(90, 0)
    val (_, bottom) = tile(0, 0, 0).unitsOf(-90, 0)
    assertEqualsDouble(top, 0, 1e-3)
    assertEqualsDouble(bottom, TileCoord.Extent.toDouble, 1e-3)

  test("latOf is mercator's inverse"):
    Seq(-80.0, -33.3, 0.0, 12.5, 61.2).foreach { lat =>
      assertEqualsDouble(TileCoord.latOf(TileCoord.mercator(lat, com.histmersion.Longitude(0))._2), lat, 1e-9)
    }

  test("the grid groups only below its deepest zoom, and cells are tile units"):
    val g = TileGrid.of(maxZoom = com.histmersion.domain.catalogue.maptiles.TileGrid.MaxZoom(12), maxPoints = com.histmersion.domain.catalogue.maptiles.TileGrid.MaxPoints(600), cellPx = com.histmersion.domain.catalogue.maptiles.TileGrid.CellPx(32))
    assertEquals(g.cellUnits, 256)
    assertEquals(g.pointsAt(tile(5, 0, 0)), 600)
    assertEquals(g.pointsAt(tile(12, 0, 0)), Int.MaxValue)
    assertEquals(TileGrid.of(com.histmersion.domain.catalogue.maptiles.TileGrid.MaxZoom(12), com.histmersion.domain.catalogue.maptiles.TileGrid.MaxPoints(600), com.histmersion.domain.catalogue.maptiles.TileGrid.CellPx(0)).cellUnits, 8)
    assertEquals(TileGrid.of(com.histmersion.domain.catalogue.maptiles.TileGrid.MaxZoom(12), com.histmersion.domain.catalogue.maptiles.TileGrid.MaxPoints(600), com.histmersion.domain.catalogue.maptiles.TileGrid.CellPx(4096)).cellUnits, TileCoord.Extent)

  test("a written tile reads back: layer, extent, places and typed properties"):
    val pts = Seq(
      TilePoint(com.histmersion.domain.catalogue.maptiles.TilePoint.X(10), com.histmersion.domain.catalogue.maptiles.TilePoint.Y(20), Seq("k" -> TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("a")), "n" -> TileValue.Whole(com.histmersion.domain.catalogue.maptiles.TileValue.Whole.Value(-3)), "lo" -> TileValue.Real(-1500.5))),
      TilePoint(com.histmersion.domain.catalogue.maptiles.TilePoint.X(4095), com.histmersion.domain.catalogue.maptiles.TilePoint.Y(0), Seq("k" -> TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("b")), "f" -> TileValue.Flag(com.histmersion.domain.catalogue.maptiles.TileValue.Flag.Value(true)), "n" -> TileValue.Whole(com.histmersion.domain.catalogue.maptiles.TileValue.Whole.Value(-3))))
    )
    val layers = VectorTileReader.read(VectorTileWriter.tile(Seq("sites" -> pts, "empty" -> Nil)))
    assertEquals(layers.map(_.name), Vector("sites"))
    val l = layers.head
    assertEquals(l.extent, 4096)
    assertEquals(l.features.map(f => (f.x, f.y)), Vector((10, 20), (4095, 0)))
    assertEquals(l.features(0).text("k"), Some("a"))
    assertEquals(l.features(0).whole("n"), Some(-3L))
    assertEquals(l.features(0).real("lo"), Some(-1500.5))
    assertEquals(l.features(1).props.get("f"), Some(TileValue.Flag(com.histmersion.domain.catalogue.maptiles.TileValue.Flag.Value(true))))
    assertEquals(l.features(1).whole("n"), Some(-3L))

  test("a key and a value are stored once per layer"):
    val pts = (1 to 50).map(i => TilePoint(i, i, Seq("kind" -> TileValue.Text(com.histmersion.domain.catalogue.maptiles.TileValue.Text.Value("site")))))
    val bytes = VectorTileWriter.layer(com.histmersion.domain.catalogue.maptiles.VectorTileWriter.LayerName("sites"), pts)
    assertEquals(new String(bytes, "UTF-8").sliding(4).count(_ == "site"), 2) // the layer's name and the value
