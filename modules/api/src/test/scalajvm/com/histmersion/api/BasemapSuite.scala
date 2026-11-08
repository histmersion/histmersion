package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

/** The terrain base map's shared pure parts: layer names, the status as Markdown. */

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.basemap.{Basemap, BasemapApi, BasemapMarkdown, BaseMapSource, BaseStyles}
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
class BasemapSuite extends munit.FunSuite:

  test("layers by name; all is every one"):
    assertEquals(
      Basemap.Layer.parseMany(Basemap.Layer.Wires("all")),
      Right(List(Basemap.Layer.Vector, Basemap.Layer.Terrain, Basemap.Layer.Labels, Basemap.Layer.Bathymetry))
    )
    assertEquals(Basemap.Layer.parseMany(Basemap.Layer.Wires("labels")), Right(List(Basemap.Layer.Labels)))
    assertEquals(Basemap.Layer.parseMany(Basemap.Layer.Wires(" Terrain ")), Right(List(Basemap.Layer.Terrain)))
    assert(Basemap.Layer.parseMany(Basemap.Layer.Wires("roads")).isLeft)
    assertEquals(BasemapApi.tileTemplate(Basemap.Layer.Vector, Basemap.LayerInfo.Version("v1")), "/api/basemap/vector/{z}/{x}/{y}?v=v1")

  test("a tile subset: layers, their geometries and kinds, merged, written back sorted"):
    import com.histmersion.api.basemap.Basemap.Read
    assertEquals(
      Basemap.parseSubset(Basemap.Subset.Spec("earth:a, water:pl,pois")),
      Right(Map(Basemap.Layer.Wire("earth") -> Read(Set(Read.Geoms(3))), Basemap.Layer.Wire("water") -> Read(Set(Read.Geoms(1), Read.Geoms(2))), Basemap.Layer.Wire("pois") -> Read()))
    )
    assertEquals(Basemap.parseSubset(Basemap.Subset.Spec("water:p,water:l")), Right(Map(Basemap.Layer.Wire("water") -> Read(Set(Read.Geoms(1), Read.Geoms(2))))))
    assertEquals(Basemap.parseSubset(Basemap.Subset.Spec("water:p,water")), Right(Map(Basemap.Layer.Wire("water") -> Read())), "all wins")
    assert(Basemap.parseSubset(Basemap.Subset.Spec("water:x")).isLeft)
    assertEquals(
      Basemap.parseSubset(Basemap.Subset.Spec("landuse:a~forest|wood,roads~highway")),
      Right(Map(Basemap.Layer.Wire("landuse") -> Read(Set(Read.Geoms(3)), Set(Read.Kinds("forest"), Read.Kinds("wood"))), Basemap.Layer.Wire("roads") -> Read(Set.empty, Set(Read.Kinds("highway")))))
    )
    assertEquals(
      Basemap.parseSubset(Basemap.Subset.Spec("landuse~forest,landuse~park")),
      Right(Map(Basemap.Layer.Wire("landuse") -> Read(Set.empty, Set(Read.Kinds("forest"), Read.Kinds("park")))))
    )
    assertEquals(Basemap.parseSubset(Basemap.Subset.Spec("landuse~forest,landuse")), Right(Map(Basemap.Layer.Wire("landuse") -> Read())), "every kind wins")
    assert(Basemap.parseSubset(Basemap.Subset.Spec("landuse~")).isLeft)
    assert(Basemap.parseSubset(Basemap.Subset.Spec("landuse~a b")).isLeft)
    assertEquals(Basemap.subsetParam(Map(Basemap.Layer.Wire("water") -> Read(Set(Read.Geoms(2), Read.Geoms(1))), Basemap.Layer.Wire("earth") -> Read())), "earth,water:pl")
    assertEquals(
      Basemap.subsetParam(Map(Basemap.Layer.Wire("roads") -> Read(Set(Read.Geoms(2)), Set(Read.Kinds("major_road"), Read.Kinds("highway"))))),
      "roads:l~highway|major_road"
    )
    val m = Map(Basemap.Layer.Wire("a") -> Read(Set(Read.Geoms(3))), Basemap.Layer.Wire("b") -> Read(Set(Read.Geoms(1)), Set(Read.Kinds("x"), Read.Kinds("y"))))
    assertEquals(Basemap.parseSubset(Basemap.subsetParam(m)), Right(m))

  test("the status reads as Markdown: what is there, the command when nothing is, credits without links"):
    val v = Basemap.LayerInfo(
      Basemap.LayerInfo.Layer("vector"),
      present = Basemap.LayerInfo.Present(true),
      file = Basemap.LayerInfo.File("/c/vector.pmtiles"),
      bytes = ByteCount(138000000),
      minZoom = Some(Basemap.LayerInfo.MinZoom(0)),
      maxZoom = Some(Basemap.Focus.MaxZoom(10)),
      bbox = Some(BBox.Text("-12,20,90,62")),
      source = Some(Basemap.LayerInfo.Source("20261019.pmtiles")),
      attribution = Basemap.LayerInfo.Attribution("""© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>""")
    )
    val t = Basemap.LayerInfo(Basemap.LayerInfo.Layer("terrain"), present = Basemap.LayerInfo.Present(false), file = Basemap.LayerInfo.File("/c/terrain.pmtiles"))
    val md = BasemapMarkdown.status(
      Basemap.Status(available = Basemap.Status.Available(true), Basemap.Status.Dir("/c"), Basemap.Status.Bbox("-12,20,90,62"), List(v, t), Basemap.Status.FetchCommand(Basemap.FetchCommand), Basemap.Status.FreeBytes(5000000000L))
    )
    assert(md.startsWith("The local base map is downloaded."), md)
    assert(md.contains("| vector | coast, water and land cover | 138.0 MB | 0–10 |"), md)
    assert(md.contains("| terrain | relief | not downloaded |"), md)
    assert(md.contains("Credits: © OpenStreetMap"), md)
    val hinted = BasemapMarkdown.status(
      Basemap.Status(
        available = Basemap.Status.Available(true),
        Basemap.Status.Dir("/c"),
        Basemap.Status.Bbox("x"),
        List(v, t),
        Basemap.Status.FetchCommand(Basemap.FetchCommand),
        hints = List(Basemap.Status.Hints("Run `histmersion basemap fetch --layer labels`."))
      )
    )
    assert(hinted.contains("Run `histmersion basemap fetch --layer labels`."), hinted)
    val none = BasemapMarkdown.status(Basemap.Status(available = Basemap.Status.Available(false), Basemap.Status.Dir("/c"), Basemap.Status.Bbox("x"), List(t), Basemap.Status.FetchCommand(Basemap.FetchCommand)))
    assert(none.contains("run `histmersion basemap fetch`"), none)
    assertEquals(BasemapMarkdown.removed(Basemap.Removed(Nil, ByteCount(0))), "Nothing to remove: no base-map file is downloaded.")

  test("a subset by zoom: a layer drawn only closer in is not asked for in shallower tiles"):
    val q = Basemap.subsetParam(
      Map(Basemap.Layer.Wire("earth") -> Basemap.Read(Set(Basemap.Read.Geoms(3))), Basemap.Layer.Wire("landuse") -> Basemap.Read(Set(Basemap.Read.Geoms(3)), Set(Basemap.Read.Kinds("wood")))),
      Map(Basemap.Layer.Wire("landuse") -> Basemap.Zoom(8))
    )
    assertEquals(q, "earth:a,landuse:a~wood@8")
    assertEquals(Basemap.subsetAt(q, Basemap.Zoom(7)), "earth:a")
    assertEquals(Basemap.subsetAt(q, Basemap.Zoom(8)), "earth:a,landuse:a~wood")
    assertEquals(
      Basemap.parseSubset(Basemap.subsetAt(q, Basemap.Zoom(9))),
      Right(Map(Basemap.Layer.Wire("earth") -> Basemap.Read(Set(Basemap.Read.Geoms(3))), Basemap.Layer.Wire("landuse") -> Basemap.Read(Set(Basemap.Read.Geoms(3)), Set(Basemap.Read.Kinds("wood")))))
    )

  test("focus regions, the on-demand relief and the sea floor in the status, in Markdown"):
    val t = Basemap.LayerInfo(
      Basemap.LayerInfo.Layer("terrain"),
      present = Basemap.LayerInfo.Present(true),
      Basemap.LayerInfo.File("/c/terrain.pmtiles"),
      bytes = ByteCount(1),
      maxZoom = Some(Basemap.Focus.MaxZoom(8)),
      onDemand = Some(Basemap.OnDemand(Basemap.OnDemand.Enabled(true), Basemap.OnDemand.MaxZoom(12), Basemap.OnDemand.Dir("/c/terrain-cache"), Basemap.OnDemand.Tiles(3), ByteCount(300000), Basemap.OnDemand.MaxBytes(3000000000L))),
      focus =
        List(Basemap.Focus(Basemap.Focus.Name("aegean"), Basemap.Focus.File("/c/terrain-focus/aegean.pmtiles"), ByteCount(430000000), Basemap.Focus.Bbox("21.5,35,28.5,41.5"), Basemap.Focus.MinZoom(9), Basemap.Focus.MaxZoom(12), Basemap.Focus.Tiles(10299)))
    )
    assertEquals(t.deepest, Some(12))
    assertEquals(t.modify(_.onDemand).setTo(None).modify(_.focus).setTo(Nil).deepest, Some(8))
    val md = BasemapMarkdown.status(Basemap.Status(available = Basemap.Status.Available(true), Basemap.Status.Dir("/c"), Basemap.Status.Bbox("x"), List(t), Basemap.Status.FetchCommand(Basemap.FetchCommand)))
    assert(md.contains("read when a page shows an area, to zoom 12; cached 3 tiles"), md)
    assert(md.contains("focus region `aegean`: zoom 9–12"), md)
    assert(BasemapMarkdown.onDemand(Basemap.OnDemand(Basemap.OnDemand.Enabled(false), Basemap.OnDemand.MaxZoom(12), Basemap.OnDemand.Dir("/c"), Basemap.OnDemand.Tiles(0), ByteCount(0), Basemap.OnDemand.MaxBytes(1))).contains("off"))
    assertEquals(Basemap.Layer.parseMany(Basemap.Layer.Wires("bathymetry")), Right(List(Basemap.Layer.Bathymetry)))
    assertEquals(Basemap.validFocusName(Basemap.Focus.Name(" Nile-Delta ")), Right("nile-delta"))
    assertEquals(Basemap.focusNameOf(BBox(Longitude(-1.5), Latitude(35), Longitude(2), Latitude(40.25))), "bbox-m1p5-35-2-40p25")
  test("what an extract left out, and what a style that draws some layers lacks in it"):
    assertEquals(Basemap.droppedLayers(keepAll = Basemap.FetchQuery.KeepAll(true), Some(Basemap.DropRule.Spec("roads"))), Nil)
    assertEquals(
      Basemap.droppedLayers(keepAll = Basemap.FetchQuery.KeepAll(false), Some(Basemap.DropRule.Spec("roads; places; pois +peak; water fountain"))),
      List("places", "roads"),
      "only layers dropped whole: a style reading pois still finds the peaks"
    )
    assertEquals(Basemap.droppedLayers(keepAll = Basemap.FetchQuery.KeepAll(false), Some(Basemap.DropRule.Spec(""))), Nil)
    assertEquals(
      Basemap.droppedLayers(keepAll = Basemap.FetchQuery.KeepAll(false), None),
      Basemap.LegacyLacks,
      "made before the drop list: earth, water, land cover only"
    )
    assertEquals(
      Basemap.missingFor(List(Basemap.LayerInfo.Dropped("earth"), Basemap.LayerInfo.Dropped("roads"), Basemap.LayerInfo.Dropped("places"), Basemap.LayerInfo.Dropped("water")), List(Basemap.LayerInfo.Dropped("places"), Basemap.LayerInfo.Dropped("roads"), Basemap.LayerInfo.Dropped("transit"))),
      List("places", "roads")
    )
    assertEquals(Basemap.missingFor(List(Basemap.LayerInfo.Dropped("earth")), List(Basemap.LayerInfo.Dropped("roads"))), Nil)
    // the vector styles: what each needs of the extract
    assertEquals(
      BaseStyles.states(Some(List(Basemap.LayerInfo.Dropped("boundaries"), Basemap.LayerInfo.Dropped("places"), Basemap.LayerInfo.Dropped("roads")))).map(st => st.id -> st.missing),
      List("readable" -> List("boundaries", "places", "roads"), "physical" -> Nil)
    )
    assertEquals(BaseStyles.states(None).map(_.missing), List(Nil, Nil))

  test("the base-map source: local and online as set; auto is local once a vector style can be drawn whole"):
    def st(available: Basemap.Status.Available, readable: List[Basemap.StyleState.Missing], physical: List[Basemap.StyleState.Missing] = Nil) = Basemap.Status(
      available,
      Basemap.Status.Dir("/c"),
      Basemap.Status.Bbox("x"),
      Nil,
      Basemap.Status.FetchCommand(Basemap.FetchCommand),
      styles =
        List(Basemap.StyleState(Basemap.StyleState.Id("readable"), Basemap.StyleState.Name("Readable"), readable), Basemap.StyleState(Basemap.StyleState.Id("physical"), Basemap.StyleState.Name("Physical"), physical))
    )
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("local"), st(Basemap.Status.Available(false), Nil)), "local")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire(" Online "), st(Basemap.Status.Available(true), Nil)), "online")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("auto"), st(Basemap.Status.Available(true), Nil)), "local")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("auto"), st(Basemap.Status.Available(true), List(Basemap.LayerInfo.Dropped("roads")))), "local", "Physical is whole without the roads")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("auto"), st(Basemap.Status.Available(true), List(Basemap.LayerInfo.Dropped("roads")), List(Basemap.LayerInfo.Dropped("landuse")))), "online", "neither is whole")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("auto"), st(Basemap.Status.Available(false), Nil)), "online", "nothing downloaded")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("whatever"), st(Basemap.Status.Available(true), Nil)), "local", "anything else is auto")

  test("the status says which styles lack layers"):
    val v = Basemap.LayerInfo(Basemap.LayerInfo.Layer("vector"), present = Basemap.LayerInfo.Present(true), file = Basemap.LayerInfo.File("/c/vector.pmtiles"), dropped = List(Basemap.LayerInfo.Dropped("places"), Basemap.LayerInfo.Dropped("roads")))
    val md = BasemapMarkdown.status(
      Basemap.Status(
        available = Basemap.Status.Available(true),
        Basemap.Status.Dir("/c"),
        Basemap.Status.Bbox("x"),
        List(v),
        Basemap.Status.FetchCommand(Basemap.FetchCommand),
        styles = List(
          Basemap.StyleState(Basemap.StyleState.Id("readable"), Basemap.StyleState.Name("Readable"), List(Basemap.LayerInfo.Dropped("places"), Basemap.LayerInfo.Dropped("roads"))),
          Basemap.StyleState(Basemap.StyleState.Id("physical"), Basemap.StyleState.Name("Physical"))
        )
      )
    )
    assert(md.contains("Styles: Readable (lacks places, roads), Physical."), md)

  test("#3760: `world` is a region a fetch accepts — Web Mercator's whole map"):
    assertEquals(Basemap.validBBox(Basemap.Focus.Bbox("world")), Right(BBox(Longitude(-180), Latitude(-85.05), Longitude(180), Latitude(85.05))))
    assertEquals(Basemap.validBBox(Basemap.Focus.Bbox(" World ")), Right(Basemap.World))
    assert(Basemap.validBBox(Basemap.Focus.Bbox("worlds")).isLeft)

  test("#3760: the map and the sea floor read outside the region, in a line each"):
    val on = BasemapMarkdown.outside(
      "The map",
      Basemap.Layer.Wire("vector"),
      Basemap.OnDemand(Basemap.OnDemand.Enabled(true), Basemap.OnDemand.MaxZoom(12), Basemap.OnDemand.Dir("/c/vector-cache"), Basemap.OnDemand.Tiles(3), ByteCount(3000), Basemap.OnDemand.MaxBytes(2_000_000_000L), None)
    )
    assert(
      on.startsWith(
        "The map outside the downloaded region (and deeper than it): read when a page shows it, to zoom 12; cached 3 tiles"
      ),
      on
    )
    assert(on.contains("`/c/vector-cache`"), on)
    assertEquals(
      BasemapMarkdown.outside("The sea floor", Basemap.Layer.Wire("bathymetry"), Basemap.OnDemand(Basemap.OnDemand.Enabled(false), Basemap.OnDemand.MaxZoom(7), Basemap.OnDemand.Dir("/c"), Basemap.OnDemand.Tiles(0), ByteCount(0), Basemap.OnDemand.MaxBytes(0), None)),
      "The sea floor outside the downloaded region: off (basemap.bathymetry.on-demand.enabled)."
    )
