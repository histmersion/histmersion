package com.histmersion.core

/** The terrain base map's shared pure parts: layer names, the status as Markdown. */
class BasemapSuite extends munit.FunSuite:

  test("layers by name; all is every one"):
    assertEquals(BasemapLayer.parseMany("all"), Right(List(BasemapLayer.Vector, BasemapLayer.Terrain, BasemapLayer.Labels, BasemapLayer.Bathymetry)))
    assertEquals(BasemapLayer.parseMany("labels"), Right(List(BasemapLayer.Labels)))
    assertEquals(BasemapLayer.parseMany(" Terrain "), Right(List(BasemapLayer.Terrain)))
    assert(BasemapLayer.parseMany("roads").isLeft)
    assertEquals(BasemapApi.tileTemplate(BasemapLayer.Vector, "v1"), "/api/basemap/vector/{z}/{x}/{y}?v=v1")

  test("a tile subset: layers and their geometries, merged, written back sorted"):
    assertEquals(Basemap.parseSubset("earth:a, water:pl,pois"), Right(Map("earth" -> Set(3), "water" -> Set(1, 2), "pois" -> Set.empty[Int])))
    assertEquals(Basemap.parseSubset("water:p,water:l"), Right(Map("water" -> Set(1, 2))))
    assertEquals(Basemap.parseSubset("water:p,water"), Right(Map("water" -> Set.empty[Int])), "all wins")
    assert(Basemap.parseSubset("water:x").isLeft)
    assertEquals(Basemap.subsetParam(Map("water" -> Set(2, 1), "earth" -> Set.empty)), "earth,water:pl")
    assertEquals(Basemap.parseSubset(Basemap.subsetParam(Map("a" -> Set(3), "b" -> Set(1)))), Right(Map("a" -> Set(3), "b" -> Set(1))))

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
    val hinted = BasemapMarkdown.status(BasemapStatus(available = true, "/c", "x", List(v, t), Basemap.FetchCommand,
      hints = List("Run `histmersion basemap fetch --layer labels`.")))
    assert(hinted.contains("Run `histmersion basemap fetch --layer labels`."), hinted)
    val none = BasemapMarkdown.status(BasemapStatus(available = false, "/c", "x", List(t), Basemap.FetchCommand))
    assert(none.contains("run `histmersion basemap fetch`"), none)
    assertEquals(BasemapMarkdown.removed(BasemapRemoved(Nil, 0)), "Nothing to remove: no base-map file is downloaded.")

  test("a subset by zoom: a layer drawn only closer in is not asked for in shallower tiles"):
    val q = Basemap.subsetParam(Map("earth" -> Set(3), "landuse" -> Set(3)), Map("landuse" -> 8))
    assertEquals(q, "earth:a,landuse:a@8")
    assertEquals(Basemap.subsetAt(q, 7), "earth:a")
    assertEquals(Basemap.subsetAt(q, 8), "earth:a,landuse:a")
    assertEquals(Basemap.parseSubset(Basemap.subsetAt(q, 9)), Right(Map("earth" -> Set(3), "landuse" -> Set(3))))

  test("focus regions, the on-demand relief and the sea floor in the status, in Markdown"):
    val t = BasemapLayerInfo("terrain", present = true, "/c/terrain.pmtiles", bytes = 1, maxZoom = Some(8),
      onDemand = Some(BasemapOnDemand(true, 12, "/c/terrain-cache", 3, 300000, 3000000000L)),
      focus = List(BasemapFocus("aegean", "/c/terrain-focus/aegean.pmtiles", 430000000, "21.5,35,28.5,41.5", 9, 12, 10299)))
    assertEquals(t.deepest, Some(12))
    assertEquals(t.copy(onDemand = None, focus = Nil).deepest, Some(8))
    val md = BasemapMarkdown.status(BasemapStatus(available = true, "/c", "x", List(t), Basemap.FetchCommand))
    assert(md.contains("read when a page shows an area, to zoom 12; cached 3 tiles"), md)
    assert(md.contains("focus region `aegean`: zoom 9–12"), md)
    assert(BasemapMarkdown.onDemand(BasemapOnDemand(false, 12, "/c", 0, 0, 1)).contains("off"))
    assertEquals(BasemapLayer.parseMany("bathymetry"), Right(List(BasemapLayer.Bathymetry)))
    assertEquals(Basemap.validFocusName(" Nile-Delta "), Right("nile-delta"))
    assertEquals(Basemap.focusNameOf(BBox(-1.5, 35, 2, 40.25)), "bbox-m1p5-35-2-40p25")
