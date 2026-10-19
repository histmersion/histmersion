package com.histmersion.core

/** The terrain base map's shared pure parts: layer names, the status as Markdown. */
class BasemapSuite extends munit.FunSuite:

  test("layers by name; all is every one"):
    assertEquals(BasemapLayer.parseMany("all"), Right(List(BasemapLayer.Vector, BasemapLayer.Terrain, BasemapLayer.Labels)))
    assertEquals(BasemapLayer.parseMany("labels"), Right(List(BasemapLayer.Labels)))
    assertEquals(BasemapLayer.parseMany(" Terrain "), Right(List(BasemapLayer.Terrain)))
    assert(BasemapLayer.parseMany("roads").isLeft)
    assertEquals(BasemapApi.tileTemplate(BasemapLayer.Vector, "v1"), "/api/basemap/vector/{z}/{x}/{y}?v=v1")

  test("a tile subset: layers, their geometries and kinds, merged, written back sorted"):
    import Basemap.Read
    assertEquals(Basemap.parseSubset("earth:a, water:pl,pois"), Right(Map("earth" -> Read(Set(3)), "water" -> Read(Set(1, 2)), "pois" -> Read())))
    assertEquals(Basemap.parseSubset("water:p,water:l"), Right(Map("water" -> Read(Set(1, 2)))))
    assertEquals(Basemap.parseSubset("water:p,water"), Right(Map("water" -> Read())), "all wins")
    assert(Basemap.parseSubset("water:x").isLeft)
    assertEquals(Basemap.parseSubset("landuse:a~forest|wood,roads~highway"),
      Right(Map("landuse" -> Read(Set(3), Set("forest", "wood")), "roads" -> Read(Set.empty, Set("highway")))))
    assertEquals(Basemap.parseSubset("landuse~forest,landuse~park"), Right(Map("landuse" -> Read(Set.empty, Set("forest", "park")))))
    assertEquals(Basemap.parseSubset("landuse~forest,landuse"), Right(Map("landuse" -> Read())), "every kind wins")
    assert(Basemap.parseSubset("landuse~").isLeft)
    assert(Basemap.parseSubset("landuse~a b").isLeft)
    assertEquals(Basemap.subsetParam(Map("water" -> Read(Set(2, 1)), "earth" -> Read())), "earth,water:pl")
    assertEquals(Basemap.subsetParam(Map("roads" -> Read(Set(2), Set("major_road", "highway")))), "roads:l~highway|major_road")
    val m = Map("a" -> Read(Set(3)), "b" -> Read(Set(1), Set("x", "y")))
    assertEquals(Basemap.parseSubset(Basemap.subsetParam(m)), Right(m))

  test("the status reads as Markdown: what is there, the command when nothing is, credits without links"):
    val v = BasemapLayerInfo("vector", present = true, file = "/c/vector.pmtiles", bytes = 138000000, minZoom = Some(0),
      maxZoom = Some(10), bbox = Some("-12,20,90,62"), source = Some("20261019.pmtiles"),
      attribution = """© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>""")
    val t = BasemapLayerInfo("terrain", present = false, file = "/c/terrain.pmtiles")
    val md = BasemapMarkdown.status(BasemapStatus(available = true, "/c", "-12,20,90,62", List(v, t), Basemap.FetchCommand, 5000000000L))
    assert(md.startsWith("The local base map is downloaded."), md)
    assert(md.contains("| vector | coast, water and land cover | 138.0 MB | 0–10 |"), md)
    assert(md.contains("| terrain | relief | not downloaded |"), md)
    assert(md.contains("Credits: © OpenStreetMap"), md)
    val hinted = BasemapMarkdown.status(BasemapStatus(available = true, "/c", "x", List(v, t), Basemap.FetchCommand,
      hints = List("Run `histmersion basemap fetch --layer labels`.")))
    assert(hinted.contains("Run `histmersion basemap fetch --layer labels`."), hinted)
    val none = BasemapMarkdown.status(BasemapStatus(available = false, "/c", "x", List(t), Basemap.FetchCommand))
    assert(none.contains("run `histmersion basemap fetch`"), none)
    assertEquals(BasemapMarkdown.removed(BasemapRemoved(Nil, 0)), "Nothing to remove: no base-map file is downloaded.")

  test("what an extract left out, and what a style that draws some layers lacks in it"):
    assertEquals(Basemap.droppedLayers(keepAll = true, Some("roads")), Nil)
    assertEquals(Basemap.droppedLayers(keepAll = false, Some("roads; places; pois +peak; water fountain")), List("places", "roads"),
      "only layers dropped whole: a style reading pois still finds the peaks")
    assertEquals(Basemap.droppedLayers(keepAll = false, Some("")), Nil)
    assertEquals(Basemap.droppedLayers(keepAll = false, None), Basemap.LegacyLacks, "made before the drop list: earth, water, land cover only")
    assertEquals(Basemap.missingFor(List("earth", "roads", "places", "water"), List("places", "roads", "transit")), List("places", "roads"))
    assertEquals(Basemap.missingFor(List("earth"), List("roads")), Nil)
    val style = TerrainStyle("s", "S", background = "#fff", layers = List(
      StyleLayer("a", "fill", dataLayers = List("earth"), fill = Some("#000")),
      StyleLayer("b", "line", dataLayers = List("roads", "earth"), color = Some("#000")),
      StyleLayer("c", "label", source = "labels", dataLayers = List("physical"), color = Some("#000"))))
    assertEquals(style.needs, List("earth", "roads"), "the vector layers only, once each")

  test("the base-map source: local and online as set; auto is local only when the default style can be drawn whole"):
    def st(available: Boolean, missing: List[String]) = BasemapStatus(available, "/c", "x", Nil, Basemap.FetchCommand,
      styles = List(BasemapStyleState("standard", "Standard", missing), BasemapStyleState("terrain", "Terrain")))
    assertEquals(BaseMapSource.resolve("local", st(false, Nil), Some("standard")), "local")
    assertEquals(BaseMapSource.resolve(" Online ", st(true, Nil), Some("standard")), "online")
    assertEquals(BaseMapSource.resolve("auto", st(true, Nil), Some("standard")), "local")
    assertEquals(BaseMapSource.resolve("auto", st(true, List("roads")), Some("standard")), "online", "the street map lacks roads")
    assertEquals(BaseMapSource.resolve("auto", st(true, List("roads")), Some("terrain")), "local", "a terrain default is whole")
    assertEquals(BaseMapSource.resolve("auto", st(false, Nil), Some("standard")), "online", "nothing downloaded")
    assertEquals(BaseMapSource.resolve("auto", st(true, Nil), Some("gone")), "online", "a default that is no style")
    assertEquals(BaseMapSource.resolve("whatever", st(true, Nil), Some("standard")), "local", "anything else is auto")

  test("the status says which styles lack layers; the styles list what each draws"):
    val v = BasemapLayerInfo("vector", present = true, file = "/c/vector.pmtiles", dropped = List("places", "roads"))
    val md = BasemapMarkdown.status(BasemapStatus(available = true, "/c", "x", List(v), Basemap.FetchCommand,
      styles = List(BasemapStyleState("standard", "Standard", List("places", "roads")), BasemapStyleState("terrain", "Terrain"))))
    assert(md.contains("Styles: Standard (lacks places, roads), Terrain."), md)
    val styles = TerrainStyles(List(TerrainStyle("standard", "Standard", background = "#fff", modern = true,
      layers = List(StyleLayer("r", "line", dataLayers = List("roads"), color = Some("#000"))))), "standard", "en", "built-in")
    val list = BasemapMarkdown.styles(styles)
    assert(list.contains("| `standard` | Standard | roads | default, modern, no names |"), list)
