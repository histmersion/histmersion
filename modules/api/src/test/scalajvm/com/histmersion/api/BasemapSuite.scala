package com.histmersion.api

/** The terrain base map's shared pure parts: layer names, the status as Markdown. */

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.basemap.{Basemap, BasemapApi, BasemapMarkdown, BaseMapSource, BaseStyles}
class BasemapSuite extends munit.FunSuite:

  test("layers by name; all is every one"):
    assertEquals(
      Basemap.Layer.parseMany(com.histmersion.api.basemap.Basemap.Layer.Wires("all")),
      Right(List(Basemap.Layer.Vector, Basemap.Layer.Terrain, Basemap.Layer.Labels, Basemap.Layer.Bathymetry))
    )
    assertEquals(Basemap.Layer.parseMany(com.histmersion.api.basemap.Basemap.Layer.Wires("labels")), Right(List(Basemap.Layer.Labels)))
    assertEquals(Basemap.Layer.parseMany(com.histmersion.api.basemap.Basemap.Layer.Wires(" Terrain ")), Right(List(Basemap.Layer.Terrain)))
    assert(Basemap.Layer.parseMany(com.histmersion.api.basemap.Basemap.Layer.Wires("roads")).isLeft)
    assertEquals(BasemapApi.tileTemplate(Basemap.Layer.Vector, com.histmersion.api.basemap.Basemap.LayerInfo.Version("v1")), "/api/basemap/vector/{z}/{x}/{y}?v=v1")

  test("a tile subset: layers, their geometries and kinds, merged, written back sorted"):
    import com.histmersion.api.basemap.Basemap.Read
    assertEquals(
      Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("earth:a, water:pl,pois")),
      Right(Map("earth" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3))), "water" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(1), com.histmersion.api.basemap.Basemap.Read.Geoms(2))), "pois" -> Read()))
    )
    assertEquals(Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("water:p,water:l")), Right(Map("water" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(1), com.histmersion.api.basemap.Basemap.Read.Geoms(2))))))
    assertEquals(Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("water:p,water")), Right(Map("water" -> Read())), "all wins")
    assert(Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("water:x")).isLeft)
    assertEquals(
      Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("landuse:a~forest|wood,roads~highway")),
      Right(Map("landuse" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3)), Set(com.histmersion.api.basemap.Basemap.Read.Kinds("forest"), com.histmersion.api.basemap.Basemap.Read.Kinds("wood"))), "roads" -> Read(Set.empty, Set(com.histmersion.api.basemap.Basemap.Read.Kinds("highway")))))
    )
    assertEquals(
      Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("landuse~forest,landuse~park")),
      Right(Map("landuse" -> Read(Set.empty, Set(com.histmersion.api.basemap.Basemap.Read.Kinds("forest"), com.histmersion.api.basemap.Basemap.Read.Kinds("park")))))
    )
    assertEquals(Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("landuse~forest,landuse")), Right(Map("landuse" -> Read())), "every kind wins")
    assert(Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("landuse~")).isLeft)
    assert(Basemap.parseSubset(com.histmersion.api.basemap.Basemap.Subset.Spec("landuse~a b")).isLeft)
    assertEquals(Basemap.subsetParam(Map("water" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(2), com.histmersion.api.basemap.Basemap.Read.Geoms(1))), "earth" -> Read())), "earth,water:pl")
    assertEquals(
      Basemap.subsetParam(Map("roads" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(2)), Set(com.histmersion.api.basemap.Basemap.Read.Kinds("major_road"), com.histmersion.api.basemap.Basemap.Read.Kinds("highway"))))),
      "roads:l~highway|major_road"
    )
    val m = Map("a" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3))), "b" -> Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(1)), Set(com.histmersion.api.basemap.Basemap.Read.Kinds("x"), com.histmersion.api.basemap.Basemap.Read.Kinds("y"))))
    assertEquals(Basemap.parseSubset(Basemap.subsetParam(m)), Right(m))

  test("the status reads as Markdown: what is there, the command when nothing is, credits without links"):
    val v = Basemap.LayerInfo(
      com.histmersion.api.basemap.Basemap.LayerInfo.Layer("vector"),
      present = com.histmersion.api.basemap.Basemap.LayerInfo.Present(true),
      file = com.histmersion.api.basemap.Basemap.LayerInfo.File("/c/vector.pmtiles"),
      bytes = com.histmersion.api.ByteCount(138000000),
      minZoom = Some(com.histmersion.api.basemap.Basemap.LayerInfo.MinZoom(0)),
      maxZoom = Some(com.histmersion.api.basemap.Basemap.Focus.MaxZoom(10)),
      bbox = Some(com.histmersion.api.BBox.Text("-12,20,90,62")),
      source = Some("20261019.pmtiles"),
      attribution = """© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>"""
    )
    val t = Basemap.LayerInfo(com.histmersion.api.basemap.Basemap.LayerInfo.Layer("terrain"), present = com.histmersion.api.basemap.Basemap.LayerInfo.Present(false), file = com.histmersion.api.basemap.Basemap.LayerInfo.File("/c/terrain.pmtiles"))
    val md = BasemapMarkdown.status(
      Basemap.Status(available = com.histmersion.api.basemap.Basemap.Status.Available(true), com.histmersion.api.basemap.Basemap.Status.Dir("/c"), com.histmersion.api.basemap.Basemap.Status.Bbox("-12,20,90,62"), List(v, t), Basemap.FetchCommand, com.histmersion.api.basemap.Basemap.Status.FreeBytes(5000000000L))
    )
    assert(md.startsWith("The local base map is downloaded."), md)
    assert(md.contains("| vector | coast, water and land cover | 138.0 MB | 0–10 |"), md)
    assert(md.contains("| terrain | relief | not downloaded |"), md)
    assert(md.contains("Credits: © OpenStreetMap"), md)
    val hinted = BasemapMarkdown.status(
      Basemap.Status(
        available = com.histmersion.api.basemap.Basemap.Status.Available(true),
        com.histmersion.api.basemap.Basemap.Status.Dir("/c"),
        com.histmersion.api.basemap.Basemap.Status.Bbox("x"),
        List(v, t),
        Basemap.FetchCommand,
        hints = List("Run `histmersion basemap fetch --layer labels`.")
      )
    )
    assert(hinted.contains("Run `histmersion basemap fetch --layer labels`."), hinted)
    val none = BasemapMarkdown.status(Basemap.Status(available = com.histmersion.api.basemap.Basemap.Status.Available(false), com.histmersion.api.basemap.Basemap.Status.Dir("/c"), com.histmersion.api.basemap.Basemap.Status.Bbox("x"), List(t), Basemap.FetchCommand))
    assert(none.contains("run `histmersion basemap fetch`"), none)
    assertEquals(BasemapMarkdown.removed(Basemap.Removed(Nil, com.histmersion.api.ByteCount(0))), "Nothing to remove: no base-map file is downloaded.")

  test("a subset by zoom: a layer drawn only closer in is not asked for in shallower tiles"):
    val q = Basemap.subsetParam(
      Map("earth" -> Basemap.Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3))), "landuse" -> Basemap.Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3)), Set(com.histmersion.api.basemap.Basemap.Read.Kinds("wood")))),
      Map("landuse" -> 8)
    )
    assertEquals(q, "earth:a,landuse:a~wood@8")
    assertEquals(Basemap.subsetAt(q, com.histmersion.api.basemap.Basemap.Zoom(7)), "earth:a")
    assertEquals(Basemap.subsetAt(q, com.histmersion.api.basemap.Basemap.Zoom(8)), "earth:a,landuse:a~wood")
    assertEquals(
      Basemap.parseSubset(Basemap.subsetAt(q, com.histmersion.api.basemap.Basemap.Zoom(9))),
      Right(Map("earth" -> Basemap.Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3))), "landuse" -> Basemap.Read(Set(com.histmersion.api.basemap.Basemap.Read.Geoms(3)), Set(com.histmersion.api.basemap.Basemap.Read.Kinds("wood")))))
    )

  test("focus regions, the on-demand relief and the sea floor in the status, in Markdown"):
    val t = Basemap.LayerInfo(
      com.histmersion.api.basemap.Basemap.LayerInfo.Layer("terrain"),
      present = com.histmersion.api.basemap.Basemap.LayerInfo.Present(true),
      com.histmersion.api.basemap.Basemap.LayerInfo.File("/c/terrain.pmtiles"),
      bytes = com.histmersion.api.ByteCount(1),
      maxZoom = Some(8),
      onDemand = Some(Basemap.OnDemand(com.histmersion.api.basemap.Basemap.OnDemand.Enabled(true), com.histmersion.api.basemap.Basemap.OnDemand.MaxZoom(12), com.histmersion.api.basemap.Basemap.OnDemand.Dir("/c/terrain-cache"), com.histmersion.api.basemap.Basemap.OnDemand.Tiles(3), com.histmersion.api.ByteCount(300000), com.histmersion.api.basemap.Basemap.OnDemand.MaxBytes(3000000000L))),
      focus =
        List(Basemap.Focus(com.histmersion.api.basemap.Basemap.Focus.Name("aegean"), com.histmersion.api.basemap.Basemap.Focus.File("/c/terrain-focus/aegean.pmtiles"), com.histmersion.api.ByteCount(430000000), com.histmersion.api.basemap.Basemap.Focus.Bbox("21.5,35,28.5,41.5"), com.histmersion.api.basemap.Basemap.Focus.MinZoom(9), com.histmersion.api.basemap.Basemap.Focus.MaxZoom(12), com.histmersion.api.basemap.Basemap.Focus.Tiles(10299)))
    )
    assertEquals(t.deepest, Some(12))
    assertEquals(t.modify(_.onDemand).setTo(None).modify(_.focus).setTo(Nil).deepest, Some(8))
    val md = BasemapMarkdown.status(Basemap.Status(available = com.histmersion.api.basemap.Basemap.Status.Available(true), com.histmersion.api.basemap.Basemap.Status.Dir("/c"), com.histmersion.api.basemap.Basemap.Status.Bbox("x"), List(t), Basemap.FetchCommand))
    assert(md.contains("read when a page shows an area, to zoom 12; cached 3 tiles"), md)
    assert(md.contains("focus region `aegean`: zoom 9–12"), md)
    assert(BasemapMarkdown.onDemand(Basemap.OnDemand(com.histmersion.api.basemap.Basemap.OnDemand.Enabled(false), com.histmersion.api.basemap.Basemap.OnDemand.MaxZoom(12), com.histmersion.api.basemap.Basemap.OnDemand.Dir("/c"), com.histmersion.api.basemap.Basemap.OnDemand.Tiles(0), com.histmersion.api.ByteCount(0), com.histmersion.api.basemap.Basemap.OnDemand.MaxBytes(1))).contains("off"))
    assertEquals(Basemap.Layer.parseMany(com.histmersion.api.basemap.Basemap.Layer.Wires("bathymetry")), Right(List(Basemap.Layer.Bathymetry)))
    assertEquals(Basemap.validFocusName(com.histmersion.api.basemap.Basemap.Focus.Name(" Nile-Delta ")), Right("nile-delta"))
    assertEquals(Basemap.focusNameOf(BBox(-1.5, com.histmersion.api.catalogue.Latitude(35), com.histmersion.api.catalogue.Longitude(2), 40.25)), "bbox-m1p5-35-2-40p25")
  test("what an extract left out, and what a style that draws some layers lacks in it"):
    assertEquals(Basemap.droppedLayers(keepAll = com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll(true), Some(com.histmersion.api.basemap.Basemap.DropRule.Spec("roads"))), Nil)
    assertEquals(
      Basemap.droppedLayers(keepAll = com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll(false), Some(com.histmersion.api.basemap.Basemap.DropRule.Spec("roads; places; pois +peak; water fountain"))),
      List("places", "roads"),
      "only layers dropped whole: a style reading pois still finds the peaks"
    )
    assertEquals(Basemap.droppedLayers(keepAll = com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll(false), Some(com.histmersion.api.basemap.Basemap.DropRule.Spec(""))), Nil)
    assertEquals(
      Basemap.droppedLayers(keepAll = com.histmersion.api.basemap.Basemap.FetchQuery.KeepAll(false), None),
      Basemap.LegacyLacks,
      "made before the drop list: earth, water, land cover only"
    )
    assertEquals(
      Basemap.missingFor(List(com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("earth"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("places"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("water")), List(com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("places"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("transit"))),
      List("places", "roads")
    )
    assertEquals(Basemap.missingFor(List(com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("earth")), List(com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads"))), Nil)
    // the vector styles: what each needs of the extract
    assertEquals(
      BaseStyles.states(Some(List(com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("boundaries"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("places"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads")))).map(st => st.id -> st.missing),
      List("readable" -> List("boundaries", "places", "roads"), "physical" -> Nil)
    )
    assertEquals(BaseStyles.states(None).map(_.missing), List(Nil, Nil))

  test("the base-map source: local and online as set; auto is local once a vector style can be drawn whole"):
    def st(available: Boolean, readable: List[String], physical: List[String] = Nil) = Basemap.Status(
      available,
      com.histmersion.api.basemap.Basemap.Status.Dir("/c"),
      com.histmersion.api.basemap.Basemap.Status.Bbox("x"),
      Nil,
      Basemap.FetchCommand,
      styles =
        List(Basemap.StyleState(com.histmersion.api.basemap.Basemap.StyleState.Id("readable"), com.histmersion.api.basemap.Basemap.StyleState.Name("Readable"), readable), Basemap.StyleState(com.histmersion.api.basemap.Basemap.StyleState.Id("physical"), com.histmersion.api.basemap.Basemap.StyleState.Name("Physical"), physical))
    )
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire("local"), st(false, Nil)), "local")
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire(" Online "), st(true, Nil)), "online")
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire("auto"), st(true, Nil)), "local")
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire("auto"), st(true, List("roads"))), "local", "Physical is whole without the roads")
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire("auto"), st(true, List("roads"), List("landuse"))), "online", "neither is whole")
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire("auto"), st(false, Nil)), "online", "nothing downloaded")
    assertEquals(BaseMapSource.resolve(com.histmersion.api.basemap.BaseMapSource.Wire("whatever"), st(true, Nil)), "local", "anything else is auto")

  test("the status says which styles lack layers"):
    val v = Basemap.LayerInfo(com.histmersion.api.basemap.Basemap.LayerInfo.Layer("vector"), present = com.histmersion.api.basemap.Basemap.LayerInfo.Present(true), file = com.histmersion.api.basemap.Basemap.LayerInfo.File("/c/vector.pmtiles"), dropped = List("places", "roads"))
    val md = BasemapMarkdown.status(
      Basemap.Status(
        available = com.histmersion.api.basemap.Basemap.Status.Available(true),
        com.histmersion.api.basemap.Basemap.Status.Dir("/c"),
        com.histmersion.api.basemap.Basemap.Status.Bbox("x"),
        List(v),
        Basemap.FetchCommand,
        styles = List(
          Basemap.StyleState(com.histmersion.api.basemap.Basemap.StyleState.Id("readable"), com.histmersion.api.basemap.Basemap.StyleState.Name("Readable"), List(com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("places"), com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads"))),
          Basemap.StyleState(com.histmersion.api.basemap.Basemap.StyleState.Id("physical"), com.histmersion.api.basemap.Basemap.StyleState.Name("Physical"))
        )
      )
    )
    assert(md.contains("Styles: Readable (lacks places, roads), Physical."), md)

  test("#3760: `world` is a region a fetch accepts — Web Mercator's whole map"):
    assertEquals(Basemap.validBBox(com.histmersion.api.basemap.Basemap.Focus.Bbox("world")), Right(BBox(com.histmersion.api.catalogue.Longitude(-180), -85.05, com.histmersion.api.catalogue.Longitude(180), 85.05)))
    assertEquals(Basemap.validBBox(com.histmersion.api.basemap.Basemap.Focus.Bbox(" World ")), Right(Basemap.World))
    assert(Basemap.validBBox(com.histmersion.api.basemap.Basemap.Focus.Bbox("worlds")).isLeft)

  test("#3760: the map and the sea floor read outside the region, in a line each"):
    val on = BasemapMarkdown.outside(
      "The map",
      com.histmersion.api.basemap.Basemap.Layer.Wire("vector"),
      Basemap.OnDemand(com.histmersion.api.basemap.Basemap.OnDemand.Enabled(true), com.histmersion.api.basemap.Basemap.OnDemand.MaxZoom(12), com.histmersion.api.basemap.Basemap.OnDemand.Dir("/c/vector-cache"), com.histmersion.api.basemap.Basemap.OnDemand.Tiles(3), com.histmersion.api.ByteCount(3000), 2_000_000_000L, None)
    )
    assert(
      on.startsWith(
        "The map outside the downloaded region (and deeper than it): read when a page shows it, to zoom 12; cached 3 tiles"
      ),
      on
    )
    assert(on.contains("`/c/vector-cache`"), on)
    assertEquals(
      BasemapMarkdown.outside("The sea floor", com.histmersion.api.basemap.Basemap.Layer.Wire("bathymetry"), Basemap.OnDemand(com.histmersion.api.basemap.Basemap.OnDemand.Enabled(false), com.histmersion.api.basemap.Basemap.OnDemand.MaxZoom(7), com.histmersion.api.basemap.Basemap.OnDemand.Dir("/c"), com.histmersion.api.basemap.Basemap.OnDemand.Tiles(0), com.histmersion.api.ByteCount(0), com.histmersion.api.basemap.Basemap.OnDemand.MaxBytes(0), None)),
      "The sea floor outside the downloaded region: off (basemap.bathymetry.on-demand.enabled)."
    )
