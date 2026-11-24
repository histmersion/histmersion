package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.lint.{LintRule, tolerated}

/** The map's vector tiles, pure: the grid (which tiles exist, their bounds, where a point falls), the grouping rule,
  * and the writer read back as MapLibre reads it.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class VectorTilesSuite extends munit.FunSuite:

  private def tile(z: TileCoord.Z, x: TileCoord.X, y: TileCoord.Y) = TileCoord.of(z, x, y, TileCoord.Z(22)).toOption.get

  test("only a tile inside the grid exists, no deeper than the server cuts"):
    assert(TileCoord.of(TileCoord.Z(0), TileCoord.X(0), TileCoord.Y(0), TileCoord.Z(12)).isRight)
    assert(TileCoord.of(TileCoord.Z(2), TileCoord.X(3), TileCoord.Y(3), TileCoord.Z(12)).isRight)
    assert(TileCoord.of(TileCoord.Z(2), TileCoord.X(4), TileCoord.Y(0), TileCoord.Z(12)).isLeft)
    assert(TileCoord.of(TileCoord.Z(2), TileCoord.X(0), TileCoord.Y(-1), TileCoord.Z(12)).isLeft)
    assert(TileCoord.of(TileCoord.Z(13), TileCoord.X(0), TileCoord.Y(0), TileCoord.Z(12)).isLeft)
    assert(TileCoord.of(TileCoord.Z(-1), TileCoord.X(0), TileCoord.Y(0), TileCoord.Z(12)).isLeft)

  test("a tile's bounds: the world at zoom 0, a quarter at zoom 1"):
    val (w, s, e, n) = tile(TileCoord.Z(0), TileCoord.X(0), TileCoord.Y(0)).bounds
    assertEqualsDouble(w, -180, 1e-9)
    assertEqualsDouble(e, 180, 1e-9)
    assertEqualsDouble(n, TileCoord.MaxLat.value, 1e-6)
    assertEqualsDouble(s, -TileCoord.MaxLat.value, 1e-6)
    val (w1, s1, e1, n1) = tile(TileCoord.Z(1), TileCoord.X(1), TileCoord.Y(0)).bounds
    assertEqualsDouble(w1, 0, 1e-9)
    assertEqualsDouble(e1, 180, 1e-9)
    assertEqualsDouble(s1, 0, 1e-9)
    assertEqualsDouble(n1, TileCoord.MaxLat.value, 1e-6)

  test("a point falls in exactly one tile: its units are inside that tile's extent and outside its neighbour's"):
    val (lat, lon) = (Latitude(37.97), Longitude(23.72)) // Athens
    val z = TileCoord.Z(6)
    val (mx, my) = TileCoord.mercator(lat, lon)
    val t =
      tile(z, TileCoord.X(math.floor(mx * (1 << z.value)).toLong), TileCoord.Y(math.floor(my * (1 << z.value)).toLong))
    val (px, py) = t.unitsOf(lat, lon)
    assert(px >= 0 && px < TileCoord.Extent.value && py >= 0 && py < TileCoord.Extent.value, s"$px $py")
    val (qx, _) = tile(z, TileCoord.X(t.x.value + 1), t.y).unitsOf(lat, lon)
    assert(qx < 0)
    val (w, s, e, n) = t.bounds
    assert(lon.value >= w && lon.value < e && lat.value > s && lat.value <= n)

  test("the poles land on the grid's edge, not off it"):
    val (_, top) = tile(TileCoord.Z(0), TileCoord.X(0), TileCoord.Y(0)).unitsOf(Latitude(90.0), Longitude(0.0))
    val (_, bottom) = tile(TileCoord.Z(0), TileCoord.X(0), TileCoord.Y(0)).unitsOf(Latitude(-90.0), Longitude(0.0))
    assertEqualsDouble(top, 0, 1e-3)
    assertEqualsDouble(bottom, TileCoord.Extent.value.toDouble, 1e-3)

  test("latOf is mercator's inverse"):
    Seq(-80.0, -33.3, 0.0, 12.5, 61.2).foreach { lat =>
      assertEqualsDouble(TileCoord.latOf(TileCoord.mercator(Latitude.assume(lat), Longitude(0.0))._2).value, lat, 1e-9)
    }

  test("the grid groups only below its deepest zoom, and cells are tile units"):
    val g =
      TileGrid.of(maxZoom = TileGrid.MaxZoom(12), maxPoints = TileGrid.MaxPoints(600), cellPx = TileGrid.CellPx(32))
    assertEquals(g.cellUnits, 256)
    assertEquals(g.pointsAt(tile(TileCoord.Z(5), TileCoord.X(0), TileCoord.Y(0))), 600)
    assertEquals(g.pointsAt(tile(TileCoord.Z(12), TileCoord.X(0), TileCoord.Y(0))), Int.MaxValue)
    assertEquals(TileGrid.of(TileGrid.MaxZoom(12), TileGrid.MaxPoints(600), TileGrid.CellPx(0)).cellUnits, 8)
    assertEquals(
      TileGrid.of(TileGrid.MaxZoom(12), TileGrid.MaxPoints(600), TileGrid.CellPx(4096)).cellUnits.value,
      TileCoord.Extent.value
    )

  test("a written tile reads back: layer, extent, places and typed properties"):
    val pts = Seq(
      TilePoint(
        TilePoint.X(10),
        TilePoint.Y(20),
        Seq(
          TilePoint.PropKey("k") -> TileValue.Text(TileValue.Text.Value("a")),
          TilePoint.PropKey("n") -> TileValue.Whole(TileValue.Whole.Value(-3)),
          TilePoint.PropKey("lo") -> TileValue.Real(TileValue.Real.Value(-1500.5))
        )
      ),
      TilePoint(
        TilePoint.X(4095),
        TilePoint.Y(0),
        Seq(
          TilePoint.PropKey("k") -> TileValue.Text(TileValue.Text.Value("b")),
          TilePoint.PropKey("f") -> TileValue.Flag(TileValue.Flag.Value(true)),
          TilePoint.PropKey("n") -> TileValue.Whole(TileValue.Whole.Value(-3))
        )
      )
    )
    val layers = VectorTileReader.read(
      VectorTileWriter
        .tile(Seq(VectorTileWriter.LayerName("sites") -> pts, VectorTileWriter.LayerName("empty") -> Nil))
        .value
    )
    assertEquals(layers.map(_.name), Vector("sites"))
    val l = layers.head
    assertEquals(l.extent, 4096)
    assertEquals(l.features.map(f => (f.x, f.y)), Vector((10, 20), (4095, 0)))
    assertEquals(l.features(0).text("k"), Some("a"))
    assertEquals(l.features(0).whole("n"), Some(-3L))
    assertEquals(l.features(0).real("lo"), Some(-1500.5))
    assertEquals(l.features(1).props.get("f"), Some(TileValue.Flag(TileValue.Flag.Value(true))))
    assertEquals(l.features(1).whole("n"), Some(-3L))

  test("a key and a value are stored once per layer"):
    val pts = (1 to 50).map(i =>
      TilePoint(
        TilePoint.X(i),
        TilePoint.Y(i),
        Seq(TilePoint.PropKey("kind") -> TileValue.Text(TileValue.Text.Value("site")))
      )
    )
    val bytes = VectorTileWriter.layer(VectorTileWriter.LayerName("sites"), pts)
    assertEquals(new String(bytes.value, "UTF-8").sliding(4).count(_ == "site"), 2) // the layer's name and the value
