package com.histmersion.domain.catalogue.maptiles

/** The map's vector tiles, pure: the grid (which tiles exist, their bounds, where a point falls), the grouping rule,
  * and the writer read back as MapLibre reads it.
  */
class VectorTilesSuite extends munit.FunSuite:

  private def tile(z: Int, x: Long, y: Long) = TileCoord.of(z, x, y, 22).toOption.get

  test("only a tile inside the grid exists, no deeper than the server cuts"):
    assert(TileCoord.of(0, 0, 0, 12).isRight)
    assert(TileCoord.of(2, 3, 3, 12).isRight)
    assert(TileCoord.of(2, 4, 0, 12).isLeft)
    assert(TileCoord.of(2, 0, -1, 12).isLeft)
    assert(TileCoord.of(13, 0, 0, 12).isLeft)
    assert(TileCoord.of(-1, 0, 0, 12).isLeft)

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
      assertEqualsDouble(TileCoord.latOf(TileCoord.mercator(lat, 0)._2), lat, 1e-9)
    }

  test("the grid groups only below its deepest zoom, and cells are tile units"):
    val g = TileGrid.of(maxZoom = 12, maxPoints = 600, cellPx = 32)
    assertEquals(g.cellUnits, 256)
    assertEquals(g.pointsAt(tile(5, 0, 0)), 600)
    assertEquals(g.pointsAt(tile(12, 0, 0)), Int.MaxValue)
    assertEquals(TileGrid.of(12, 600, 0).cellUnits, 8)
    assertEquals(TileGrid.of(12, 600, 4096).cellUnits, TileCoord.Extent)

  test("a written tile reads back: layer, extent, places and typed properties"):
    val pts = Seq(
      TilePoint(10, 20, Seq("k" -> TileValue.Text("a"), "n" -> TileValue.Whole(-3), "lo" -> TileValue.Real(-1500.5))),
      TilePoint(4095, 0, Seq("k" -> TileValue.Text("b"), "f" -> TileValue.Flag(true), "n" -> TileValue.Whole(-3)))
    )
    val layers = VectorTileReader.read(VectorTileWriter.tile(Seq("sites" -> pts, "empty" -> Nil)))
    assertEquals(layers.map(_.name), Vector("sites"))
    val l = layers.head
    assertEquals(l.extent, 4096)
    assertEquals(l.features.map(f => (f.x, f.y)), Vector((10, 20), (4095, 0)))
    assertEquals(l.features(0).text("k"), Some("a"))
    assertEquals(l.features(0).whole("n"), Some(-3L))
    assertEquals(l.features(0).real("lo"), Some(-1500.5))
    assertEquals(l.features(1).props.get("f"), Some(TileValue.Flag(true)))
    assertEquals(l.features(1).whole("n"), Some(-3L))

  test("a key and a value are stored once per layer"):
    val pts = (1 to 50).map(i => TilePoint(i, i, Seq("kind" -> TileValue.Text("site"))))
    val bytes = VectorTileWriter.layer("sites", pts)
    assertEquals(new String(bytes, "UTF-8").sliding(4).count(_ == "site"), 2) // the layer's name and the value
