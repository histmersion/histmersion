package com.histmersion.web

import com.histmersion.core.*

import scala.scalajs.js.typedarray.Uint8ClampedArray

/** The terrain base map's pure parts: the option, its notices, the tile templates, the land-cover
  * paint groups, and the relief's arithmetic. */
class TerrainMapSuite extends munit.FunSuite:

  private def layer(l: BasemapLayer, present: Boolean, version: Option[String] = Some("abc")) =
    BasemapLayerInfo(l.wire, present, s"/x/${l.file}", maxZoom = Some(8), bbox = Some("22.0,35.0,29.0,41.0"),
      version = version, attribution = s"credit ${l.wire}")

  private def status(vector: Boolean, terrain: Boolean) =
    BasemapStatus(vector, "/x", "-12,20,90,62", List(layer(BasemapLayer.Vector, vector), layer(BasemapLayer.Terrain, terrain)),
      Basemap.FetchCommand)

  test("the option is chosen and kept like the other base maps; its labels name it and say when it is missing"):
    assertEquals(BaseMap.fromWire("terrain"), Some(BaseMap.Local))
    assertEquals(summon[FieldCodec[BaseMap]].decode(List("terrain")), Some(BaseMap.Local))
    assertEquals(BaseMap.tone(BaseMap.Local, MapTone.Light), MapTone.Dark)
    assert(!BaseMap.darkened(BaseMap.Local, MapTone.Light), "its own colours are dark already")
    assertEquals(BaseMap.Local.url, None)
    assertEquals(TerrainMap.optionLabel(None), "Local map")
    assertEquals(TerrainMap.optionLabel(Some(status(true, false))), "Local map")
    assertEquals(TerrainMap.optionLabel(Some(status(false, false))), "Local map — not downloaded")

  test("the notices: the past was different; when missing, what to run"):
    assert(TerrainMap.PastNotice.contains("Coastlines, rivers, lakes and vegetation were different in the past"))
    assert(TerrainMap.missingNotice(Some(status(false, false))).contains("`histmersion basemap fetch`"))
    assertEquals(TerrainMap.docsLink("https://d/guide/"), "https://d/guide/exploring/web-ui.md#the-terrain-map")
    assertEquals(TerrainMap.docsLink("https://d/guide"), "https://d/guide/exploring/web-ui.md#the-terrain-map")

  test("tile templates carry the layer's version; a missing layer has none; credits are joined"):
    val s = status(true, true)
    assertEquals(TerrainMap.template(s, BasemapLayer.Vector), Some("/api/basemap/vector/{z}/{x}/{y}?v=abc"))
    assertEquals(TerrainMap.template(status(true, false), BasemapLayer.Terrain), None)
    assertEquals(TerrainMap.attribution(s), "credit vector · credit terrain")
    assertEquals(TerrainMap.bounds(s.layers.head).map(_.toList.map(_.toList)), Some(List(List(35.0, 22.0), List(41.0, 29.0))))

  private lazy val styles = TerrainStyles(List(
    TerrainStyle("terrain", "Terrain", background = "var(--terrain-water)",
      layers = List(StyleLayer("land", "fill", dataLayers = List("earth"), fill = Some("var(--terrain-land)")),
        StyleLayer("seas", "label", dataLayers = List("water"), geometry = Some("point"), kinds = List("sea"), color = Some("#fff")))),
    TerrainStyle("parchment", "Parchment", tone = "light", background = "#c6d3d0", labels = false, language = Some("de"))),
    default = "terrain", language = "en", source = "built-in")

  test("the menu: one entry per named style, its value names it; the map and style it stands for; the tone"):
    assertEquals(TerrainMap.optionValue("parchment"), "terrain:parchment")
    assertEquals(TerrainMap.parseOption("terrain:parchment"), Some((BaseMap.Local, Some("parchment"))))
    assertEquals(TerrainMap.parseOption("terrain"), Some((BaseMap.Local, None)))
    assertEquals(TerrainMap.parseOption("dark"), Some((BaseMap.Dark, None)))
    assertEquals(TerrainMap.parseOption("dark:x"), None)
    assertEquals(TerrainMap.selectValue(BaseMap.Local, None, Some(styles)), "terrain:terrain", "the default")
    assertEquals(TerrainMap.selectValue(BaseMap.Local, Some("parchment"), Some(styles)), "terrain:parchment")
    assertEquals(TerrainMap.selectValue(BaseMap.Local, Some("gone"), Some(styles)), "terrain:terrain", "a style no longer served")
    assertEquals(TerrainMap.selectValue(BaseMap.Local, None, None), "terrain")
    assertEquals(TerrainMap.selectValue(BaseMap.Muted, Some("parchment"), Some(styles)), "muted")
    assertEquals(TerrainMap.optionLabel(Some(status(true, false)), styles.find("parchment")), "Parchment (no modern features)")
    assertEquals(TerrainMap.optionLabel(Some(status(false, false)), styles.find("parchment")), "Parchment (no modern features) — not downloaded")
    assertEquals(BaseMap.tone(BaseMap.Local, MapTone.Dark, MapTone.Light), MapTone.Light)
    assertEquals(TerrainMap.sources(status(true, true)), Set("vector"), "no names archive in this status")

  test("the two families: online maps offered unless the page is local; a kept online choice drawn locally there"):
    assertEquals(TerrainMap.onlineMaps(local = false), List(BaseMap.Standard, BaseMap.Muted, BaseMap.Dark))
    assertEquals(TerrainMap.onlineMaps(local = true), Nil, "a local page offers nothing from another host")
    assertEquals(BaseMap.Standard.label, "Standard (online)")
    assertEquals(TerrainMap.effective(BaseMap.Standard, None, local = false), (BaseMap.Standard, None))
    assertEquals(TerrainMap.effective(BaseMap.Dark, Some("parchment"), local = true), (BaseMap.Local, Some("dark")))
    assertEquals(TerrainMap.effective(BaseMap.Local, Some("parchment"), local = true), (BaseMap.Local, Some("parchment")))
    val street = styles.copy(styles = TerrainStyle("standard", "Standard", tone = "light", background = "#aad", modern = true) :: styles.styles)
    assertEquals(TerrainMap.selectValue(BaseMap.Standard, None, Some(street), local = true), "terrain:standard")
    assertEquals(TerrainMap.selectValue(BaseMap.Muted, None, Some(street), local = true), "terrain:terrain", "no local Muted: the default")
    assertEquals(TerrainMap.selectValue(BaseMap.Standard, None, Some(street), local = false), "standard")

  test("a street map's label has no 'no modern features'; one whose layers the download left out says so, and its notice what to run"):
    val std = TerrainStyle("standard", "Standard", background = "#aad", modern = true)
    val lacking = status(true, false).copy(styles = List(BasemapStyleState("standard", "Standard", List("places", "roads"))))
    assertEquals(TerrainMap.optionLabel(Some(status(true, false)), Some(std)), "Standard")
    assertEquals(TerrainMap.optionLabel(Some(lacking), Some(std)), "Standard — needs the full download")
    assertEquals(TerrainMap.optionLabel(Some(status(false, false)), Some(std)), "Standard — not downloaded")
    assert(TerrainMap.lackingNotice(List("places", "roads")).contains("places, roads"))
    assert(TerrainMap.lackingNotice(List("roads")).contains("`histmersion basemap fetch --layer vector --keep-all`"))
    assert(TerrainMap.missingNotice(Some(status(false, false)), modern = true).contains("`histmersion basemap fetch --keep-all`"))

  test("compiled selectors: geometry, kinds, not-kinds, details, and a label's own min_zoom"):
    val sea = TerrainRules.selector(styles.styles.head.layers(1))
    assert(sea.matches(1, "sea", "", 7, 7) && !sea.matches(1, "sea", "", 7, 6.5), "waits for its own min_zoom")
    assert(sea.matches(1, "sea", "", Double.NaN, 3), "no min_zoom: always")
    assert(!sea.matches(2, "sea", "", 0, 9) && !sea.matches(1, "bay", "", 0, 9))
    val water = TerrainRules.selector(StyleLayer("w", "fill", dataLayers = List("water"), geometry = Some("polygon"),
      notKinds = List("swimming_pool"), details = List("lake")))
    assert(water.matches(3, "water", "lake", Double.NaN, 9))
    assert(!water.matches(3, "swimming_pool", "lake", Double.NaN, 9) && !water.matches(3, "water", "reservoir", Double.NaN, 9))
    assert(!water.ownMinZoom, "a fill ignores min_zoom")

  test("colours resolve stylesheet tokens once; zoom values are numbers or functions of the data zoom; only drawable sources"):
    val tokens = Map("--terrain-land" -> " #31343a ")
    assertEquals(TerrainRules.colour("var(--terrain-land)", tokens.getOrElse(_, "")), "#31343a")
    assertEquals(TerrainRules.colour("var(--nope)", tokens.getOrElse(_, "")), "transparent")
    assertEquals(TerrainRules.colour("#abc", _ => "x"), "#abc")
    assertEquals(TerrainRules.zoomed(ZoomValue.const(2), 1), 2.0: Any)
    val f = TerrainRules.zoomed(ZoomValue(List(ZoomStop(4, 0), ZoomStop(8, 4))), 1).asInstanceOf[scala.scalajs.js.Function1[Double, Double]]
    assertEquals(f(5), 2.0, "data zoom 5 is map zoom 6")
    val ls = List(StyleLayer("a", "fill", dataLayers = List("earth"), fill = Some("#000")),
      StyleLayer("b", "label", source = "labels", dataLayers = List("physical"), color = Some("#fff")))
    assertEquals(TerrainRules.usable(ls, Set("vector")).map(_.id), List("a"), "an extract without the names draws the rest")
    assertEquals(TerrainRules.language(styles.styles(1), styles), "de")
    // what the tiles are asked for: the layers and geometries the rules read, per source
    val reads = TerrainRules.subsets(List(
      StyleLayer("land", "fill", dataLayers = List("earth"), geometry = Some("polygon"), fill = Some("#000")),
      StyleLayer("seas", "label", dataLayers = List("water", "earth"), geometry = Some("point"), color = Some("#fff")),
      StyleLayer("rivers", "line", dataLayers = List("water"), geometry = Some("line"), color = Some("#00f")),
      StyleLayer("ranges", "label", source = "labels", dataLayers = List("physical"), color = Some("#fff"))))
    import Basemap.Read
    assertEquals(reads, Map("vector" -> Map("earth" -> Read(Set(3, 1)), "water" -> Read(Set(1, 2))), "labels" -> Map("physical" -> Read())))
    assertEquals(Basemap.subsetParam(reads("vector")), "earth:pa,water:pl")
    assertEquals(TerrainMap.withSubset("/api/basemap/vector/{z}/{x}/{y}?v=1", Map("water" -> Read(Set(1)))),
      "/api/basemap/vector/{z}/{x}/{y}?v=1&layers=water%3Ap")
    assertEquals(TerrainMap.withSubset("/api/basemap/vector/{z}/{x}/{y}?v=1", Map("water" -> Read(Set(1))), List("kind", "name:en", "kind")),
      "/api/basemap/vector/{z}/{x}/{y}?v=1&layers=water%3Ap&keys=kind%2Cname%3Aen")
    // kinds: read only those a layer's rules name — unless one draws all of it, or all but some
    val roads = TerrainRules.subsets(List(
      StyleLayer("motorway", "line", dataLayers = List("roads"), geometry = Some("line"), kinds = List("highway"), color = Some("#f00")),
      StyleLayer("major", "line", dataLayers = List("roads"), geometry = Some("line"), kinds = List("major_road"), color = Some("#fa0")),
      StyleLayer("towns", "label", dataLayers = List("places"), kinds = List("locality"), color = Some("#000")),
      StyleLayer("cities", "label", dataLayers = List("places"), color = Some("#000")),
      StyleLayer("water", "fill", dataLayers = List("water"), notKinds = List("fountain"), fill = Some("#00f"))))
    assertEquals(roads("vector"), Map("roads" -> Read(Set(2), Set("highway", "major_road")), "places" -> Read(), "water" -> Read()))
    assertEquals(TerrainRules.labelKeys("de"), List("kind", "kind_detail", "min_zoom", "population_rank", "name:de", "name:en", "name"))
    assert(TerrainRules.order(3, 1, 5, 9) < 0, "the lower min_zoom first")
    assert(TerrainRules.order(5, 9, 5, 2) < 0 && TerrainRules.order(5, 2, 5, 9) > 0, "then the larger place")
    assert(TerrainRules.order(Double.NaN, 9, 5, 1) > 0, "no min_zoom: after one")
    assertEquals(TerrainRules.language(styles.styles.head, styles), "en")

  test("terrarium elevations and ground resolution"):
    assertEquals(Hillshade.elevation(128, 0, 0), 0.0)
    assertEquals(Hillshade.elevation(128, 100, 128), 100.5)
    assertEqualsDouble(Hillshade.metresPerPixel(0, 0, 256), 156543.03, 1.0)
    assert(Hillshade.metresPerPixel(8, 90, 512) < Hillshade.metresPerPixel(8, 128, 512), "narrower towards the poles")

  test("flat ground is transparent; a slope away from the north-west light is shadowed, one towards it lit"):
    val light = Hillshade.Light()
    val flat  = Hillshade.illumination(5, 5, 5, 5, 5, 5, 5, 5, 30, 1, light)
    assertEqualsDouble(flat, math.cos(math.toRadians(45)), 1e-9)
    // rising towards the south-east, a slope faces the north-west light; rising towards it, away
    val towards = Hillshade.illumination(0, 10, 20, 10, 30, 20, 30, 40, 30, 1, light)
    val away    = Hillshade.illumination(40, 30, 20, 30, 10, 20, 10, 0, 30, 1, light)
    assert(away < flat && towards > flat, s"$away $flat $towards")

    def tile(f: (Int, Int) => Double): Uint8ClampedArray =
      val a = new Uint8ClampedArray(4 * 4 * 4)
      for y <- 0 until 4; x <- 0 until 4 do
        val e = f(x, y) + 32768
        val o = (y * 4 + x) * 4
        a(o) = (e / 256).toInt; a(o + 1) = (e % 256).toInt; a(o + 2) = 0; a(o + 3) = 255
      a
    val shadow = Hillshade.Tint(0, 0, 0, 150)
    val lit    = Hillshade.Tint(255, 255, 255, 40)
    val out    = new Uint8ClampedArray(4 * 4 * 4)
    Hillshade.shade(tile((_, _) => 100), 4, 4, 30, 1, light, shadow, lit, out)
    assert((0 until 16).forall(p => out(p * 4 + 3) == 0), "flat: transparent")
    Hillshade.shade(tile((x, y) => (6 - x - y) * 50.0), 4, 4, 30, 1, light, shadow, lit, out)
    assertEquals(out(5 * 4), 0)
    assert(out(5 * 4 + 3) > 0 && out(5 * 4 + 3) <= 150, out(5 * 4 + 3).toString)
    Hillshade.shade(tile((x, y) => (x + y) * 50.0), 4, 4, 30, 1, light, shadow, lit, out)
    assertEquals(out(5 * 4), 255)
    assert(out(5 * 4 + 3) > 0 && out(5 * 4 + 3) <= 40, out(5 * 4 + 3).toString)
