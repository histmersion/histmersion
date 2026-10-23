package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.map.*

/** The base map's choices and what each draws, on the fake engine's terms (no browser). */
class BaseMapsSuite extends munit.FunSuite:

  private val ui = UiConfig("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", 12, 5, 250, 20, 12, 2, 3, 40,
    DebounceConfig(150, 250, 400, 120), TimeConfig(-5000, 2000, -3500, -500), 760, 500, EventsUiConfig(1.5, 3, 5000, 0.05))

  private def status(available: Boolean, readableMissing: List[String] = Nil, focus: Boolean = false) =
    BasemapStatus(available, "/x", "-12,20,90,62", List(
      BasemapLayerInfo("vector", available, "/x/vector.pmtiles", maxZoom = Some(10), version = Some("v1")),
      BasemapLayerInfo("terrain", available, "/x/terrain.pmtiles", maxZoom = Some(8), version = Some("t1"),
        focus = if focus then List(BasemapFocus("argolid", "/x/f.pmtiles", 1, "22,37,23,38", 9, 12, 10)) else Nil)),
      Basemap.FetchCommand, styles = List(BasemapStyleState("readable", "Readable", readableMissing), BasemapStyleState("physical", "Physical")),
      mapMaxZoom = 16)

  test("the choices: two styles, the online maps unless the page is local; an old local choice is Readable"):
    assertEquals(BaseMaps.choices(local = true), List(BaseMap.Readable, BaseMap.Physical))
    assertEquals(BaseMaps.choices(local = false).map(_.wire), List("readable", "physical", "standard", "muted", "dark"))
    assertEquals(BaseMap.fromWire("terrain"), Some(BaseMap.Readable))
    assertEquals(summon[FieldCodec[BaseMap]].decode(List("physical")), Some(BaseMap.Physical))
    assertEquals(BaseMap.tone(BaseMap.Physical, MapTone.Dark), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Dark, MapTone.Light), MapTone.Dark)

  test("a local page draws a kept online choice as its best local style: Readable when whole, else Physical"):
    assertEquals(BaseMaps.effective(BaseMap.Dark, local = true, Some(status(true))), BaseMap.Readable)
    assertEquals(BaseMaps.effective(BaseMap.Dark, local = true, Some(status(true, List("roads")))), BaseMap.Physical)
    assertEquals(BaseMaps.effective(BaseMap.Dark, local = false, Some(status(true))), BaseMap.Dark)
    assertEquals(BaseMaps.effective(BaseMap.Physical, local = true, Some(status(true))), BaseMap.Physical)

  test("the labels say when the data is missing or the download left layers out"):
    assertEquals(BaseMaps.optionLabel(BaseMap.Readable, Some(status(true))), "Readable")
    assertEquals(BaseMaps.optionLabel(BaseMap.Readable, Some(status(true, List("roads")))), "Readable — needs the full download")
    assertEquals(BaseMaps.optionLabel(BaseMap.Physical, Some(status(false))), "Physical — not downloaded")
    assertEquals(BaseMaps.optionLabel(BaseMap.Muted, None), "Muted (online)")
    assert(BaseMaps.lackingNotice(List("places", "roads")).contains("`histmersion basemap fetch --layer vector --keep-all`"))
    assert(BaseMaps.missingNotice(Some(status(false))).contains("`histmersion basemap fetch`"))
    assertEquals(BaseMaps.docsLink("https://d/guide"), "https://d/guide/exploring/web-ui.md#the-base-map")

  test("what is drawn: a vector style over the extract with the viewer's options, its notice and depth"):
    val (spec, notice, z) = BaseMaps.plan(BaseMap.Physical, Some(status(true)), MapPrefs(language = Some("el"), contours = Some(false)), ui,
      local = false, soft = false)
    spec match
      case BaseMapSpec.Vector(VectorStyle.Physical, _, "el", true, false, false) => ()
      case other => fail(other.toString)
    assertEquals((notice.map(_._1), z), (Some(BaseMaps.PastNotice), 16))
    // a lacking download says so; without a GPU the cheapest drawing
    val (s2, n2, _) = BaseMaps.plan(BaseMap.Readable, Some(status(true, List("roads"))), MapPrefs(), ui, local = false, soft = true)
    assert(s2.asInstanceOf[BaseMapSpec.Vector].reduced)
    assert(n2.exists((t, warn) => warn && t.contains("roads")))
    // the configured language (`basemap.labels.language`) unless the viewer chose one; offered in the menu
    BaseMaps.plan(BaseMap.Readable, Some(status(true)), MapPrefs(), ui.copy(mapLanguage = "de"), local = false, soft = false)._1 match
      case BaseMapSpec.Vector(_, _, "de", _, _, _) => ()
      case other => fail(other.toString)
    assertEquals(BaseMaps.languageChoices("de"), BaseMaps.Languages)
    assertEquals(BaseMaps.languageChoices("pl").last, "pl" -> "pl")
    // not downloaded: the online Standard stands in — none on a local page
    assert(BaseMaps.plan(BaseMap.Readable, Some(status(false)), MapPrefs(), ui, local = false, soft = false)._1.isInstanceOf[BaseMapSpec.Raster])
    assertEquals(BaseMaps.plan(BaseMap.Readable, Some(status(false)), MapPrefs(), ui, local = true, soft = false)._1, BaseMapSpec.Blank)

  test("the online maps: the configured tiles, the HOT style's own, Dark the same tiles inverted; their credits"):
    val std = BaseMaps.raster(BaseMap.Standard, ui)
    assertEquals((std.template, std.maxZoom, std.dark), (ui.tiles, 12, false))
    assert(std.attribution.contains("OpenStreetMap") && !std.attribution.contains("Humanitarian"))
    assert(BaseMaps.raster(BaseMap.Muted, ui).attribution.contains("Humanitarian"))
    assert(BaseMaps.raster(BaseMap.Dark, ui).dark)
    assert(!BaseMaps.raster(BaseMap.Dark, ui.copy(tilesTone = "dark")).dark, "dark tiles already")
    assertEquals(BaseMaps.plan(BaseMap.Muted, None, MapPrefs(), ui, local = false, soft = false)._2, None, "no notice online")

  test("contours: Physical only, with a GPU, where a focus region holds the relief"):
    assertEquals(BaseMaps.contoursWhy(BaseMap.Physical, Some(status(true, focus = true)), soft = false), None)
    assert(BaseMaps.contoursWhy(BaseMap.Readable, Some(status(true, focus = true)), soft = false).isDefined)
    assert(BaseMaps.contoursWhy(BaseMap.Physical, Some(status(true)), soft = false).exists(_.contains("--focus")))
    assert(BaseMaps.contoursWhy(BaseMap.Physical, Some(status(true, focus = true)), soft = true).isDefined)

  test("a map of the fake engine is told the base map, and the notice rises only where it meets the credits"):
    val s = FakeSurface()
    s.setBaseMap(BaseMaps.plan(BaseMap.Physical, Some(status(true)), MapPrefs(), ui, local = true, soft = false)._1)
    assert(s.baseMaps.head.isInstanceOf[BaseMapSpec.Vector])
    assertEquals(BaseMaps.noticeLift(10, 300, 900, 1200, 40), 0.0)
    assertEquals(BaseMaps.noticeLift(10, 300, 200, 400, 40), 44.0)
    assertEquals(BaseMaps.placement(900, 1000, 40, 300, 1100), (700.0, 44.0))

  test("map options: kept as one JSON value; anything unreadable is no options"):
    val c = summon[FieldCodec[MapPrefs]]
    assertEquals(c.encode(MapPrefs()), Nil)
    assertEquals(c.decode(c.encode(MapPrefs(names = Some(false), language = Some("local")))), Some(MapPrefs(Some(false), Some("local"))))
    assertEquals(c.decode(List("{oops")), Some(MapPrefs()))
