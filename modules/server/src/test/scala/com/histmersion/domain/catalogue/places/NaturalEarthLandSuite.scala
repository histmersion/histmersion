package com.histmersion
package domain.catalogue.places

class NaturalEarthLandSuite extends munit.FunSuite:

  test("polygon geometries are extracted one by one; points, lines and null geometries are skipped"):
    val fc =
      """{"type":"FeatureCollection","features":[
        |{"type":"Feature","properties":{"featurecla":"Land"},"geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}},
        |{"type":"Feature","properties":{},"geometry":{"type":"MultiPolygon","coordinates":[[[[2,2],[3,2],[3,3],[2,2]]]]}},
        |{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[5,5]}},
        |{"type":"Feature","properties":{},"geometry":null}]}""".stripMargin
    val gs = NaturalEarthLand.geometries(fc).toOption.get
    assertEquals(gs.size, 2)
    assert(gs.head.value.startsWith("""{"type":"Polygon""""), gs.head)
    assert(gs(1).value.contains("MultiPolygon"), gs(1))

  test("anything but a FeatureCollection is an error, not an empty coastline"):
    assert(NaturalEarthLand.geometries("""{"type":"Polygon"}""").isLeft)
    assert(NaturalEarthLand.geometries("not json").isLeft)
