package com.histmersion.core

/** The terrain base map's shared pure parts: layer names, the status as Markdown. */
class BasemapSuite extends munit.FunSuite:

  test("layers by name; all is both"):
    assertEquals(BasemapLayer.parseMany("all"), Right(List(BasemapLayer.Vector, BasemapLayer.Terrain)))
    assertEquals(BasemapLayer.parseMany(" Terrain "), Right(List(BasemapLayer.Terrain)))
    assert(BasemapLayer.parseMany("roads").isLeft)
    assertEquals(BasemapApi.tileTemplate(BasemapLayer.Vector, "v1"), "/api/basemap/vector/{z}/{x}/{y}?v=v1")

  test("the status reads as Markdown: what is there, the command when nothing is, credits without links"):
    val v = BasemapLayerInfo("vector", present = true, file = "/c/vector.pmtiles", bytes = 138000000, minZoom = Some(0),
      maxZoom = Some(10), bbox = Some("-12,20,90,62"), source = Some("20261019.pmtiles"),
      attribution = """© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>""")
    val t = BasemapLayerInfo("terrain", present = false, file = "/c/terrain.pmtiles")
    val md = BasemapMarkdown.status(BasemapStatus(available = true, "/c", "-12,20,90,62", List(v, t), Basemap.FetchCommand, 5000000000L))
    assert(md.startsWith("The terrain base map is downloaded."), md)
    assert(md.contains("| vector | coast, water and land cover | 138.0 MB | 0–10 |"), md)
    assert(md.contains("| terrain | relief | not downloaded |"), md)
    assert(md.contains("Credits: © OpenStreetMap"), md)
    val none = BasemapMarkdown.status(BasemapStatus(available = false, "/c", "x", List(t), Basemap.FetchCommand))
    assert(none.contains("run `histmersion basemap fetch`"), none)
    assertEquals(BasemapMarkdown.removed(BasemapRemoved(Nil, 0)), "Nothing to remove: no base-map file is downloaded.")
