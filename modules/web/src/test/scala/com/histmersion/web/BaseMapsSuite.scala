package com.histmersion
package web

import hearth.kindlings.optics.syntax.*
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.Html
import com.histmersion.api.Html.html

import com.histmersion.api.BBox
import com.histmersion.web.map.*
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.instance.{DebounceConfig, EventsUiConfig, TimeConfig, UiConfig}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.ByteCount
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.lint.{tolerated, LintRule}

/** The base map's choices and what each draws, on the fake engine's terms (no browser). */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class BaseMapsSuite extends munit.FunSuite:

  private val ui = UiConfig(
    UiConfig.Tiles("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"),
    UiConfig.TilesMaxZoom(12),
    UiConfig.MarkerRadius(5),
    UiConfig.ViewportDebounceMs(250),
    UiConfig.PageSize(20),
    UiConfig.FacetSuggestions(12),
    UiConfig.TreeDepth(2),
    UiConfig.TreeSamples(3),
    UiConfig.MaxBuckets(40),
    DebounceConfig(
      DebounceConfig.SuggestMs(150),
      DebounceConfig.SearchMs(250),
      DebounceConfig.ReloadMs(400),
      DebounceConfig.PreviewMs(120)
    ),
    TimeConfig(
      Year(-5000),
      Year(2000),
      Year(-3500),
      Year(-500)
    ),
    UiConfig.NarrowMaxWidthPx(760),
    UiConfig.NarrowMaxHeightPx(500),
    UiConfig.TabletMaxWidthPx(1366),
    EventsUiConfig(
      EventsUiConfig.LocalFadeSeconds(1.5),
      EventsUiConfig.RegionalFadeSeconds(3),
      EventsUiConfig.MapLimit(5000),
      EventsUiConfig.OutlineToleranceDeg(0.05)
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def status(
      available: Boolean,
      readableMissing: List[Basemap.StyleState.Missing] = Nil,
      focus: Boolean = false,
      onDemand: Boolean = false,
      sea: Boolean = true
  ) =
    Basemap.Status(
      Basemap.Status.Available(available),
      Basemap.Status.Dir("/x"),
      Basemap.Status.Bbox("-12,20,90,62"),
      List(
        Basemap.LayerInfo(
          Basemap.LayerInfo.Layer("vector"),
          Basemap.LayerInfo.Present(available),
          Basemap.LayerInfo.File("/x/vector.pmtiles"),
          maxZoom = Some(Basemap.LayerInfo.MaxZoom(10)),
          version = Some(Basemap.LayerInfo.Version("v1"))
        ),
        Basemap.LayerInfo(
          Basemap.LayerInfo.Layer("terrain"),
          Basemap.LayerInfo.Present(available),
          Basemap.LayerInfo.File("/x/terrain.pmtiles"),
          maxZoom = Some(Basemap.LayerInfo.MaxZoom(8)),
          version = Some(Basemap.LayerInfo.Version("t1")),
          onDemand = Some(
            Basemap.OnDemand(
              Basemap.OnDemand.Enabled(onDemand),
              Basemap.OnDemand.MaxZoom(12),
              Basemap.OnDemand.Dir("/x/terrain-cache"),
              Basemap.OnDemand.Tiles(0),
              ByteCount(0),
              Basemap.OnDemand.MaxBytes(1000)
            )
          ),
          focus = if focus then
            List(
              Basemap.Focus(
                Basemap.Focus.Name("argolid"),
                Basemap.Focus.File("/x/f.pmtiles"),
                ByteCount(1),
                Basemap.Focus.Bbox("22,37,23,38"),
                Basemap.Focus.MinZoom(9),
                Basemap.Focus.MaxZoom(12),
                Basemap.Focus.Tiles(10)
              )
            )
          else Nil
        ),
        Basemap.LayerInfo(
          Basemap.LayerInfo.Layer("bathymetry"),
          Basemap.LayerInfo.Present(available && sea),
          Basemap.LayerInfo.File("/x/bathymetry.pmtiles"),
          maxZoom = Some(Basemap.LayerInfo.MaxZoom(7)),
          version = Some(Basemap.LayerInfo.Version("b1"))
        )
      ),
      Basemap.Status.FetchCommand(Basemap.FetchCommand),
      styles = List(
        Basemap.StyleState(Basemap.StyleState.Id("readable"), Basemap.StyleState.Name("Readable"), readableMissing),
        Basemap.StyleState(Basemap.StyleState.Id("physical"), Basemap.StyleState.Name("Physical"))
      ),
      mapMaxZoom = Basemap.Status.MapMaxZoom(16)
    )

  test("the choices: two styles, the online maps unless the page is local; an old local choice is Readable"):
    assertEquals(BaseMaps.choices(local = true), List(BaseMap.Readable, BaseMap.Physical))
    assertEquals(
      BaseMaps.choices(local = false).map(_.wire),
      List(
        BaseMap.Wire("readable"),
        BaseMap.Wire("physical"),
        BaseMap.Wire("standard"),
        BaseMap.Wire("muted"),
        BaseMap.Wire("dark")
      )
    )
    assertEquals(BaseMap.fromWire(BaseMap.Wire("terrain")), Some(BaseMap.Readable))
    assertEquals(summon[FieldCodec[BaseMap]].decode(List(FieldCodec.Token("physical"))), Some(BaseMap.Physical))
    assertEquals(BaseMap.tone(BaseMap.Physical, MapTone.Dark), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Dark, MapTone.Light), MapTone.Dark)

  test("a local page draws a kept online choice as its best local style: Readable when whole, else Physical"):
    assertEquals(BaseMaps.effective(BaseMap.Dark, local = true, Some(status(true))), BaseMap.Readable)
    assertEquals(
      BaseMaps.effective(BaseMap.Dark, local = true, Some(status(true, List(Basemap.StyleState.Missing("roads"))))),
      BaseMap.Physical
    )
    assertEquals(BaseMaps.effective(BaseMap.Dark, local = false, Some(status(true))), BaseMap.Dark)
    assertEquals(BaseMaps.effective(BaseMap.Physical, local = true, Some(status(true))), BaseMap.Physical)

  test("the labels say when the data is missing or the download left layers out"):
    assertEquals(BaseMaps.optionLabel(BaseMap.Readable, Some(status(true))), "Readable")
    assertEquals(
      BaseMaps.optionLabel(BaseMap.Readable, Some(status(true, List(Basemap.StyleState.Missing("roads"))))),
      "Readable — needs the full download"
    )
    assertEquals(BaseMaps.optionLabel(BaseMap.Physical, Some(status(false))), "Physical — not downloaded")
    assertEquals(BaseMaps.optionLabel(BaseMap.Muted, None), "Muted (online)")
    assert(
      BaseMaps.lackingNotice(List("places", "roads")).contains("`histmersion basemap fetch --layer vector --keep-all`")
    )
    assert(BaseMaps.missingNotice(Some(status(false))).contains("`histmersion basemap fetch`"))
    assertEquals(
      UiConfig.DocsUrl("https://d/guide").page(BaseMaps.BaseMapPage),
      "https://d/guide/exploring/web-ui.md#the-base-map"
    )
    assertEquals(UiConfig.DocsUrl("https://d/guide/").page("a.md"), "https://d/guide/a.md")
    assertEquals(UiConfig.DocsUrl("").page("a.md"), "a.md")

  test("what is drawn: a vector style over the extract with the viewer's options, its notice and depth"):
    val (spec, notice, z) = BaseMaps.plan(
      BaseMap.Physical,
      Some(status(true)),
      MapPrefs(language = Some(MapPrefs.Language("el")), contours = Some(MapPrefs.Contours(false))),
      ui,
      local = false,
      soft = false
    )
    spec match
      case BaseMapSpec.Vector(VectorStyle.Physical, _, lang, names, contours, seaDepth, reduced)
          if lang == BaseMapSpec.Vector.Lang("el") && names == BaseMapSpec.Vector.Names(true) &&
            contours == BaseMapSpec.Vector.Contours(false) && seaDepth == BaseMapSpec.Vector.SeaDepth(true) &&
            reduced == BaseMapSpec.Vector.Reduced(false) =>
        ()
      case other => fail(other.toString)
    assertEquals((notice.map(_._1), z), (Some(BaseMaps.PastNotice), Basemap.Zoom(16)))
    // a lacking download says so; without a GPU the cheapest drawing
    val (s2, n2, _) =
      BaseMaps.plan(
        BaseMap.Readable,
        Some(status(true, List(Basemap.StyleState.Missing("roads")))),
        MapPrefs(),
        ui,
        local = false,
        soft = true
      )
    assert(s2.asInstanceOf[BaseMapSpec.Vector].reduced.value)
    assert(n2.exists((t, warn) => warn && t.contains("roads")))
    // the configured language (`basemap.labels.language`) unless the viewer chose one; offered in the menu
    BaseMaps
      .plan(
        BaseMap.Readable,
        Some(status(true)),
        MapPrefs(),
        ui.modify(_.mapLanguage).setTo(UiConfig.MapLanguage("de")),
        local = false,
        soft = false
      )
      ._1 match
      case BaseMapSpec.Vector(_, _, lang, _, _, seaDepth, _)
          if lang == BaseMapSpec.Vector.Lang("de") && seaDepth == BaseMapSpec.Vector.SeaDepth(false) =>
        ()
      case other => fail(other.toString)
    assertEquals(BaseMaps.languageChoices("de"), BaseMaps.Languages)
    assertEquals(BaseMaps.languageChoices("pl").last, "pl" -> "pl")
    // not downloaded: the online Standard stands in — none on a local page
    assert(
      BaseMaps
        .plan(BaseMap.Readable, Some(status(false)), MapPrefs(), ui, local = false, soft = false)
        ._1
        .isInstanceOf[BaseMapSpec.Raster]
    )
    assertEquals(
      BaseMaps.plan(BaseMap.Readable, Some(status(false)), MapPrefs(), ui, local = true, soft = false)._1,
      BaseMapSpec.Blank
    )

  test("the online maps: the configured tiles, the HOT style's own, Dark the same tiles inverted; their credits"):
    val std = BaseMaps.raster(BaseMap.Standard, ui)
    assertEquals((std.template.value, std.maxZoom.value, std.dark.value), (ui.tiles.value, 12, false))
    assert(std.attribution.value.contains("OpenStreetMap") && !std.attribution.value.contains("Humanitarian"))
    assert(BaseMaps.raster(BaseMap.Muted, ui).attribution.value.contains("Humanitarian"))
    assert(BaseMaps.raster(BaseMap.Dark, ui).dark.value)
    assert(
      !BaseMaps.raster(BaseMap.Dark, ui.modify(_.tilesTone).setTo(UiConfig.TilesTone("dark"))).dark.value,
      "dark tiles already"
    )
    assertEquals(
      BaseMaps.plan(BaseMap.Muted, None, MapPrefs(), ui, local = false, soft = false)._2,
      None,
      "no notice online"
    )

  test("contours: Physical only, with a GPU, from a focus region or the on-demand relief"):
    assertEquals(BaseMaps.contoursWhy(BaseMap.Physical, Some(status(true, focus = true)), soft = false), None)
    assertEquals(
      BaseMaps.contoursWhy(BaseMap.Physical, Some(status(true, onDemand = true)), soft = false),
      None,
      "on demand alone"
    )
    assert(BaseMaps.contoursWhy(BaseMap.Readable, Some(status(true, focus = true)), soft = false).isDefined)
    assert(
      BaseMaps
        .contoursWhy(BaseMap.Physical, Some(status(true)), soft = false)
        .exists(w => w.contains("--focus") && w.contains("on-demand relief is off"))
    )
    assert(BaseMaps.contoursWhy(BaseMap.Physical, Some(status(true, focus = true)), soft = true).isDefined)
    assert(
      BaseMaps.contoursWhy(BaseMap.Physical, Some(status(false)), soft = false).exists(_.contains("--layer terrain`")),
      "no relief"
    )
    assert(BaseMaps.contoursWhy(BaseMap.Physical, None, soft = false).isDefined, "not asked yet")

  test("contours in this view: zoom in below zoom 11; outside every focus region only when nothing is read on demand"):
    val argolid = GeoBounds(GeoBounds.South(37.6), GeoBounds.West(22.6), GeoBounds.North(37.8), GeoBounds.East(22.9))
    val naxos = GeoBounds(GeoBounds.South(36.9), GeoBounds.West(25.3), GeoBounds.North(37.2), GeoBounds.East(25.6))
    assertEquals(BaseMaps.contoursNote(Some(status(true, focus = true)), 12, argolid), None)
    assert(BaseMaps.contoursNote(Some(status(true, focus = true)), 10.5, argolid).exists(_.startsWith("zoom in")))
    assert(
      BaseMaps
        .contoursNote(Some(status(true, focus = true)), 12, naxos)
        .exists(n => n.contains("outside the detailed relief (argolid)") && n.contains("on-demand relief is off"))
    )
    assertEquals(
      BaseMaps.contoursNote(Some(status(true, focus = true, onDemand = true)), 12, naxos),
      None,
      "read on demand there"
    )
    assertEquals(BaseMaps.contoursNote(Some(status(true, onDemand = true)), 11, naxos), None)
    assert(
      BaseMaps.meets(
        GeoBounds(GeoBounds.South(37.0), GeoBounds.West(21.0), GeoBounds.North(37.5), GeoBounds.East(22.1)),
        BBox(Longitude(22), Latitude(37), Longitude(23), Latitude(38))
      ),
      "touching at the corner"
    )
    assert(
      !BaseMaps.meets(
        GeoBounds(GeoBounds.South(38.1), GeoBounds.West(22.0), GeoBounds.North(39.0), GeoBounds.East(23.0)),
        BBox(Longitude(22), Latitude(37), Longitude(23), Latitude(38))
      )
    )

  test(
    "the sea's depth: on in Physical and off in Readable by default, the viewer's choice per style; needs the sea floor and a GPU"
  ):
    assert(MapPrefs().seaDepth(VectorStyle.Physical))
    assert(!MapPrefs().seaDepth(VectorStyle.Readable))
    val r = MapPrefs().withSeaDepth(VectorStyle.Readable, on = MapPrefs.SeaDepth(true))
    assertEquals(r, MapPrefs(depthReadable = Some(MapPrefs.DepthReadable(true))))
    assert(r.seaDepth(VectorStyle.Readable) && r.seaDepth(VectorStyle.Physical))
    assertEquals(
      r.withSeaDepth(VectorStyle.Readable, on = MapPrefs.SeaDepth(false)),
      MapPrefs(),
      "the default again is no choice"
    )
    assertEquals(
      MapPrefs().withSeaDepth(VectorStyle.Physical, on = MapPrefs.SeaDepth(false)),
      MapPrefs(depthPhysical = Some(MapPrefs.DepthPhysical(false)))
    )
    BaseMaps.plan(BaseMap.Readable, Some(status(true)), r, ui, local = false, soft = false)._1 match
      case v: BaseMapSpec.Vector => assert(v.seaDepth.value)
      case other                 => fail(other.toString)
    assertEquals(BaseMaps.seaDepthWhy(BaseMap.Readable, Some(status(true)), soft = false), None)
    assert(
      BaseMaps
        .seaDepthWhy(BaseMap.Physical, Some(status(true, sea = false)), soft = false)
        .exists(_.contains("--layer bathymetry"))
    )
    assert(BaseMaps.seaDepthWhy(BaseMap.Physical, Some(status(true)), soft = true).isDefined)
    assert(BaseMaps.seaDepthWhy(BaseMap.Standard, Some(status(true)), soft = false).isDefined)

  test("a map of the fake engine is told the base map, and the notice rises only where it meets the credits"):
    val s = FakeSurface()
    s.setBaseMap(BaseMaps.plan(BaseMap.Physical, Some(status(true)), MapPrefs(), ui, local = true, soft = false)._1)
    assert(s.baseMaps.head.isInstanceOf[BaseMapSpec.Vector])
    assertEquals(BaseMaps.noticeLift(10, 300, 900, 1200, 40), 0.0)
    assertEquals(BaseMaps.noticeLift(10, 300, 200, 400, 40), 44.0)
    assertEquals(BaseMaps.placement(900, 1000, 40, 300, 1100), (700.0, 44.0))

  test("the map options open under their button, or over it when a tablet's drawer leaves no room under it"):
    import BaseMaps.PanelSide
    assertEquals(BaseMaps.panelSide(10, 40, 900, 280), PanelSide.Below(44.0))
    assertEquals(BaseMaps.panelSide(818, 862, 1024, 280), PanelSide.Above(210.0))
    assertEquals(
      BaseMaps.panelSide(818, 862, 1024, 0),
      PanelSide.Below(866.0),
      "0: always under (the phone, the desktop)"
    )
    assertEquals(BaseMaps.panelSide(100, 140, 300, 280), PanelSide.Below(144.0), "no more room above: under")

  test("the notice rises over an opened legend only while the map has room for it above; else the legend lies over it"):
    assertEquals(BaseMaps.noticeLift(16, 300, 16, 374, 200, room = 500), 204.0)
    assertEquals(BaseMaps.noticeLift(16, 300, 16, 374, 600, room = 500), 0.0)
    assertEquals(BaseMaps.noticeLift(16, 300, 320, 374, 600, room = 10), 0.0)

  test("a folding overlay's button names what it opens and says whether it is open"):
    val closed =
      MapFold.toggleHtml(
        MapFold.ClassName("hd-legend-toggle"),
        MapFold.ControlsId("hd-legend-body"),
        MapFold.Label("map legend"),
        html"<span>legend</span>",
        open = MapFold.Open(false)
      )
    assert(closed.value.startsWith("""<button type="button" class="btn btn--sm hd-legend-toggle" """), closed)
    assert(closed.value.contains("""aria-controls="hd-legend-body""""), closed)
    assert(closed.value.contains("""aria-expanded="false""""), closed)
    assert(closed.value.contains("""aria-label="map legend""""), closed)
    assert(closed.value.endsWith("<span>legend</span></button>"), closed)
    assert(
      MapFold
        .toggleHtml(
          MapFold.ClassName("x"),
          MapFold.ControlsId("y"),
          MapFold.Label("a \"b\" & c"),
          Html.empty,
          open = MapFold.Open(true)
        )
        .value
        .contains("""aria-expanded="true" aria-label="a &quot;b&quot; &amp; c"""")
    )

  test("map options: kept as one JSON value; anything unreadable is no options"):
    val c = summon[FieldCodec[MapPrefs]]
    assertEquals(c.encode(MapPrefs()), Nil)
    assertEquals(
      c.decode(c.encode(MapPrefs(names = Some(MapPrefs.Names(false)), language = Some(MapPrefs.Language("local"))))),
      Some(MapPrefs(Some(MapPrefs.Names(false)), Some(MapPrefs.Language("local"))))
    )
    assertEquals(c.decode(List(FieldCodec.Token("{oops"))), Some(MapPrefs()))
    assertEquals(
      c.decode(c.encode(MapPrefs(depthReadable = Some(MapPrefs.DepthReadable(true))))),
      Some(MapPrefs(depthReadable = Some(MapPrefs.DepthReadable(true))))
    )
    assertEquals(
      c.decode(List(FieldCodec.Token("""{"names":false}"""))),
      Some(MapPrefs(names = Some(MapPrefs.Names(false)))),
      "kept before the depth existed"
    )
