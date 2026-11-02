package com.histmersion.api

/** The terrain base map's shared pure parts: layer names, the status as Markdown. */

import com.histmersion.api.basemap.{Basemap, BasemapApi, BasemapMarkdown, BaseMapSource, BaseStyles}
class BasemapSuite extends munit.FunSuite:

  test("layers by name; all is every one"):
    assertEquals(
      Basemap.Layer.parseMany("all"),
      Right(List(Basemap.Layer.Vector, Basemap.Layer.Terrain, Basemap.Layer.Labels, Basemap.Layer.Bathymetry))
    )
    assertEquals(Basemap.Layer.parseMany("labels"), Right(List(Basemap.Layer.Labels)))
    assertEquals(Basemap.Layer.parseMany(" Terrain "), Right(List(Basemap.Layer.Terrain)))
    assert(Basemap.Layer.parseMany("roads").isLeft)
    assertEquals(BasemapApi.tileTemplate(Basemap.Layer.Vector, "v1"), "/api/basemap/vector/{z}/{x}/{y}?v=v1")

  test("a tile subset: layers, their geometries and kinds, merged, written back sorted"):
    import com.histmersion.api.basemap.Basemap.Read
    assertEquals(
      Basemap.parseSubset("earth:a, water:pl,pois"),
      Right(Map("earth" -> Read(Set(3)), "water" -> Read(Set(1, 2)), "pois" -> Read()))
    )
    assertEquals(Basemap.parseSubset("water:p,water:l"), Right(Map("water" -> Read(Set(1, 2)))))
    assertEquals(Basemap.parseSubset("water:p,water"), Right(Map("water" -> Read())), "all wins")
    assert(Basemap.parseSubset("water:x").isLeft)
    assertEquals(
      Basemap.parseSubset("landuse:a~forest|wood,roads~highway"),
      Right(Map("landuse" -> Read(Set(3), Set("forest", "wood")), "roads" -> Read(Set.empty, Set("highway"))))
    )
    assertEquals(
      Basemap.parseSubset("landuse~forest,landuse~park"),
      Right(Map("landuse" -> Read(Set.empty, Set("forest", "park"))))
    )
    assertEquals(Basemap.parseSubset("landuse~forest,landuse"), Right(Map("landuse" -> Read())), "every kind wins")
    assert(Basemap.parseSubset("landuse~").isLeft)
    assert(Basemap.parseSubset("landuse~a b").isLeft)
    assertEquals(Basemap.subsetParam(Map("water" -> Read(Set(2, 1)), "earth" -> Read())), "earth,water:pl")
    assertEquals(
      Basemap.subsetParam(Map("roads" -> Read(Set(2), Set("major_road", "highway")))),
      "roads:l~highway|major_road"
    )
    val m = Map("a" -> Read(Set(3)), "b" -> Read(Set(1), Set("x", "y")))
    assertEquals(Basemap.parseSubset(Basemap.subsetParam(m)), Right(m))

  test("the status reads as Markdown: what is there, the command when nothing is, credits without links"):
    val v = Basemap.LayerInfo(
      "vector",
      present = true,
      file = "/c/vector.pmtiles",
      bytes = 138000000,
      minZoom = Some(0),
      maxZoom = Some(10),
      bbox = Some("-12,20,90,62"),
      source = Some("20261019.pmtiles"),
      attribution = """© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>"""
    )
    val t = Basemap.LayerInfo("terrain", present = false, file = "/c/terrain.pmtiles")
    val md = BasemapMarkdown.status(
      Basemap.Status(available = true, "/c", "-12,20,90,62", List(v, t), Basemap.FetchCommand, 5000000000L)
    )
    assert(md.startsWith("The local base map is downloaded."), md)
    assert(md.contains("| vector | coast, water and land cover | 138.0 MB | 0–10 |"), md)
    assert(md.contains("| terrain | relief | not downloaded |"), md)
    assert(md.contains("Credits: © OpenStreetMap"), md)
    val hinted = BasemapMarkdown.status(
      Basemap.Status(
        available = true,
        "/c",
        "x",
        List(v, t),
        Basemap.FetchCommand,
        hints = List("Run `histmersion basemap fetch --layer labels`.")
      )
    )
    assert(hinted.contains("Run `histmersion basemap fetch --layer labels`."), hinted)
    val none = BasemapMarkdown.status(Basemap.Status(available = false, "/c", "x", List(t), Basemap.FetchCommand))
    assert(none.contains("run `histmersion basemap fetch`"), none)
    assertEquals(BasemapMarkdown.removed(Basemap.Removed(Nil, 0)), "Nothing to remove: no base-map file is downloaded.")

  test("a subset by zoom: a layer drawn only closer in is not asked for in shallower tiles"):
    val q = Basemap.subsetParam(
      Map("earth" -> Basemap.Read(Set(3)), "landuse" -> Basemap.Read(Set(3), Set("wood"))),
      Map("landuse" -> 8)
    )
    assertEquals(q, "earth:a,landuse:a~wood@8")
    assertEquals(Basemap.subsetAt(q, 7), "earth:a")
    assertEquals(Basemap.subsetAt(q, 8), "earth:a,landuse:a~wood")
    assertEquals(
      Basemap.parseSubset(Basemap.subsetAt(q, 9)),
      Right(Map("earth" -> Basemap.Read(Set(3)), "landuse" -> Basemap.Read(Set(3), Set("wood"))))
    )

  test("focus regions, the on-demand relief and the sea floor in the status, in Markdown"):
    val t = Basemap.LayerInfo(
      "terrain",
      present = true,
      "/c/terrain.pmtiles",
      bytes = 1,
      maxZoom = Some(8),
      onDemand = Some(Basemap.OnDemand(true, 12, "/c/terrain-cache", 3, 300000, 3000000000L)),
      focus =
        List(Basemap.Focus("aegean", "/c/terrain-focus/aegean.pmtiles", 430000000, "21.5,35,28.5,41.5", 9, 12, 10299))
    )
    assertEquals(t.deepest, Some(12))
    assertEquals(t.copy(onDemand = None, focus = Nil).deepest, Some(8))
    val md = BasemapMarkdown.status(Basemap.Status(available = true, "/c", "x", List(t), Basemap.FetchCommand))
    assert(md.contains("read when a page shows an area, to zoom 12; cached 3 tiles"), md)
    assert(md.contains("focus region `aegean`: zoom 9–12"), md)
    assert(BasemapMarkdown.onDemand(Basemap.OnDemand(false, 12, "/c", 0, 0, 1)).contains("off"))
    assertEquals(Basemap.Layer.parseMany("bathymetry"), Right(List(Basemap.Layer.Bathymetry)))
    assertEquals(Basemap.validFocusName(" Nile-Delta "), Right("nile-delta"))
    assertEquals(Basemap.focusNameOf(BBox(-1.5, 35, 2, 40.25)), "bbox-m1p5-35-2-40p25")
  test("what an extract left out, and what a style that draws some layers lacks in it"):
    assertEquals(Basemap.droppedLayers(keepAll = true, Some("roads")), Nil)
    assertEquals(
      Basemap.droppedLayers(keepAll = false, Some("roads; places; pois +peak; water fountain")),
      List("places", "roads"),
      "only layers dropped whole: a style reading pois still finds the peaks"
    )
    assertEquals(Basemap.droppedLayers(keepAll = false, Some("")), Nil)
    assertEquals(
      Basemap.droppedLayers(keepAll = false, None),
      Basemap.LegacyLacks,
      "made before the drop list: earth, water, land cover only"
    )
    assertEquals(
      Basemap.missingFor(List("earth", "roads", "places", "water"), List("places", "roads", "transit")),
      List("places", "roads")
    )
    assertEquals(Basemap.missingFor(List("earth"), List("roads")), Nil)
    // the vector styles: what each needs of the extract
    assertEquals(
      BaseStyles.states(Some(List("boundaries", "places", "roads"))).map(st => st.id -> st.missing),
      List("readable" -> List("boundaries", "places", "roads"), "physical" -> Nil)
    )
    assertEquals(BaseStyles.states(None).map(_.missing), List(Nil, Nil))

  test("the base-map source: local and online as set; auto is local once a vector style can be drawn whole"):
    def st(available: Boolean, readable: List[String], physical: List[String] = Nil) = Basemap.Status(
      available,
      "/c",
      "x",
      Nil,
      Basemap.FetchCommand,
      styles =
        List(Basemap.StyleState("readable", "Readable", readable), Basemap.StyleState("physical", "Physical", physical))
    )
    assertEquals(BaseMapSource.resolve("local", st(false, Nil)), "local")
    assertEquals(BaseMapSource.resolve(" Online ", st(true, Nil)), "online")
    assertEquals(BaseMapSource.resolve("auto", st(true, Nil)), "local")
    assertEquals(BaseMapSource.resolve("auto", st(true, List("roads"))), "local", "Physical is whole without the roads")
    assertEquals(BaseMapSource.resolve("auto", st(true, List("roads"), List("landuse"))), "online", "neither is whole")
    assertEquals(BaseMapSource.resolve("auto", st(false, Nil)), "online", "nothing downloaded")
    assertEquals(BaseMapSource.resolve("whatever", st(true, Nil)), "local", "anything else is auto")

  test("the status says which styles lack layers"):
    val v = Basemap.LayerInfo("vector", present = true, file = "/c/vector.pmtiles", dropped = List("places", "roads"))
    val md = BasemapMarkdown.status(
      Basemap.Status(
        available = true,
        "/c",
        "x",
        List(v),
        Basemap.FetchCommand,
        styles = List(
          Basemap.StyleState("readable", "Readable", List("places", "roads")),
          Basemap.StyleState("physical", "Physical")
        )
      )
    )
    assert(md.contains("Styles: Readable (lacks places, roads), Physical."), md)

  test("#3760: `world` is a region a fetch accepts — Web Mercator's whole map"):
    assertEquals(Basemap.validBBox("world"), Right(BBox(-180, -85.05, 180, 85.05)))
    assertEquals(Basemap.validBBox(" World "), Right(Basemap.World))
    assert(Basemap.validBBox("worlds").isLeft)

  test("#3760: the map and the sea floor read outside the region, in a line each"):
    val on = BasemapMarkdown.outside(
      "The map",
      "vector",
      Basemap.OnDemand(true, 12, "/c/vector-cache", 3, 3000, 2_000_000_000L, None)
    )
    assert(
      on.startsWith(
        "The map outside the downloaded region (and deeper than it): read when a page shows it, to zoom 12; cached 3 tiles"
      ),
      on
    )
    assert(on.contains("`/c/vector-cache`"), on)
    assertEquals(
      BasemapMarkdown.outside("The sea floor", "bathymetry", Basemap.OnDemand(false, 7, "/c", 0, 0, 0, None)),
      "The sea floor outside the downloaded region: off (basemap.bathymetry.on-demand.enabled)."
    )
