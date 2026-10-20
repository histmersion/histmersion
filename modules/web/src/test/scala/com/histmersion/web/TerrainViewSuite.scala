package com.histmersion.web

import com.histmersion.core.*

/** A viewer's map options over a preset ([[TerrainView]]): what each option changes in the style that
  * is drawn, what a preset offers given the data, normalising, the stored form, and the family. Pure. */
class TerrainViewSuite extends munit.FunSuite:

  private val tint = List(TintStop(0, "#cfe2b4"), TintStop(1000, "#ecdcb0"))
  private val seaTint = List(TintStop(-3000, "#5b92c0"), TintStop(-1, "#cfe6f3"))
  private val natural = TerrainStyle("natural", "Natural", tone = "light", background = "#a9cfe4",
    relief = ReliefStyle(tint = tint, seaTint = seaTint, contours = ContourStyle(enabled = true)),
    layers = List(
      StyleLayer("land", "fill", dataLayers = List("earth"), geometry = Some("polygon"), fill = Some("#e9e9d2")),
      StyleLayer("forest", "fill", dataLayers = List("landuse"), kinds = List("forest"), fill = Some("green")),
      StyleLayer("water", "fill", dataLayers = List("water"), notKinds = List("basin"), fill = Some("#a9cfe4"),
        glow = Some(StyleGlow("#fff"))),
      StyleLayer("oceans", "label", dataLayers = List("water"), color = Some("#123"))))
  private val all = Set("vector", "terrain", "labels", "bathymetry")

  test("the preset's own values; a viewer's change; an option back at the preset's value is no change"):
    assertEquals(TerrainView.defaults(natural), ViewOptions(Some(true), Some(1.0), Some(true), Some(true), Some(true), Some(true), None))
    val v = ViewOptions(names = Some(false), contours = Some(true))
    assertEquals(TerrainView.normalise(natural, v), ViewOptions(names = Some(false)))
    assertEquals(TerrainView.shown(natural, v).names, Some(false))
    assert(TerrainView.normalise(natural, ViewOptions(relief = Some(1.0))).isEmpty)

  test("the preset as is: the sea floor splits the water — the open sea under the depth colours, lakes and shore over them"):
    val s = TerrainView(natural, ViewOptions(), None, all)
    assert(s.relief.heights && s.relief.seaFloor && s.labels && s.relief.contours.enabled)
    assertEquals(s.layers.map(_.id), List("land", "forest", "water-sea", "water", "water-shore", "oceans"))
    assertEquals(s.layers.find(_.id == "water").map(_.notKinds), Some(List("basin", "ocean")), "the lakes")
    val sea = s.layers.find(_.id == "water-sea").get
    assert(TerrainRules.isGround(sea) && sea.glow.isEmpty && sea.kinds == List("ocean"), "the open sea: the ground")
    assert(TerrainRules.isGround(s.layers.head), "the land's fill: the ground")
    assert(!TerrainRules.isGround(s.layers.find(_.id == "water-shore").get), "the shore's glow lies over the depths")
    assert(!TerrainRules.isGround(s.layers.find(_.id == "forest").get))

  test("each option changes the drawn style; the language; data that is missing switches its option off"):
    val s = TerrainView(natural, ViewOptions(names = Some(false), relief = Some(2.0), heights = Some(false), landCover = Some(false),
      seaFloor = Some(false), contours = Some(false), interval = Some(20)), Some("local"), all)
    assert(!s.labels && !s.relief.heights && !s.relief.seaFloor && !s.relief.contours.enabled)
    assertEquals(s.language, Some("local"))
    assertEquals(s.relief.shadowOpacity, math.min(1.0, ReliefStyle().shadowOpacity * 2))
    assert(!s.layers.exists(_.id == "forest"), "no land cover")
    assertEquals(s.layers.map(_.id), List("land", "water", "oceans"), "the water as the preset paints it")
    assertEquals(s.relief.contours.interval.step(15), 20.0)
    val noData = TerrainView(natural, ViewOptions(), None, Set("vector", "labels"))
    assert(!noData.relief.heights && !noData.relief.seaFloor && !noData.relief.contours.enabled, "no relief: none of it")
    val why = TerrainView.unavailable(natural, Set("vector", "terrain"))
    assert(why("seaFloor").contains("histmersion basemap fetch --layer bathymetry"), why.toString)
    assert(!why.contains("heights") && !why.contains("names"))
    assert(TerrainView.unavailable(natural.copy(relief = natural.relief.copy(tint = Nil)), all)("heights").contains("no colours by height"))

  test("only the names changed: their layer alone is drawn again"):
    val a = TerrainView(natural, ViewOptions(), None, all)
    assert(TerrainView.onlyNames(a, TerrainView(natural, ViewOptions(), Some("el"), all)))
    assert(TerrainView.onlyNames(a, TerrainView(natural, ViewOptions(names = Some(false)), None, all)))
    assert(!TerrainView.onlyNames(a, TerrainView(natural, ViewOptions(contours = Some(false)), None, all)))

  test("kept as one JSON value; anything unreadable is no options; the family of a base map"):
    val codec = summon[FieldCodec[ViewPrefs]]
    val p = ViewPrefs(Some("el"), Map("natural" -> ViewOptions(names = Some(false), interval = Some(50))))
    assertEquals(codec.decode(codec.encode(p)), Some(p))
    assertEquals(codec.encode(ViewPrefs()), Nil)
    assertEquals(codec.decode(List("{oops")), Some(ViewPrefs()))
    assertEquals(p.set("natural", ViewOptions()).styles, Map.empty[String, ViewOptions], "an empty change is dropped")
    assertEquals(MapOptions.family(BaseMap.Local), "histmersion")
    assertEquals(MapOptions.family(BaseMap.Dark), "raster")
    assertEquals(TerrainMap.familyLabel(None), "Histmersion map")

  test("the sea-floor tiles under an elevation window: their zoom, scale and place"):
    // a whole elevation tile of zoom 6 (512 px) with 2 px around: the 256-px sea-floor tiles of zoom 7 under it
    val src = Relief.source(7, 36, 24, 7)
    val (bz, f, tiles) = ReliefLayer.seaTiles(src, 7)
    assertEquals((bz, f), (7, 1.0))
    assertEquals(tiles.toSet, (for y <- 48L to 49L; x <- 72L to 73L yield (x, y)).toSet, "the 2 × 2 under it, no more")
    // deeper than the sea floor goes: one coarser tile, a smaller scale
    val deep = Relief.source(12, 2306, 1583, 12)
    val (bz2, f2, t2) = ReliefLayer.seaTiles(deep, 7)
    assertEquals((bz2, f2), (7, math.pow(2, -5)))
    assertEquals(t2, List((144L, 98L)))

  test("the options panel stays inside the window, under its button"):
    assertEquals(MapOptions.placement(900, 932, 360, 300, 1440), (632.0, 364.0), "its right edge at the button's")
    assertEquals(MapOptions.placement(10, 42, 400, 300, 1280), (8.0, 404.0), "a button at the far left: from the left edge")
    assertEquals(MapOptions.placement(300, 332, 400, 300, 320), (12.0, 404.0))

  test("the tile zoom each data layer is read from: when every layer reading it starts closer in"):
    val ls = List(StyleLayer("a", "fill", dataLayers = List("landuse"), minZoom = Some(9), fill = Some("red")),
      StyleLayer("b", "fill", dataLayers = List("landuse"), minZoom = Some(10), fill = Some("red")),
      StyleLayer("c", "fill", dataLayers = List("earth"), fill = Some("red")),
      StyleLayer("d", "fill", dataLayers = List("water"), minZoom = Some(6), fill = Some("red")),
      StyleLayer("e", "fill", dataLayers = List("water"), fill = Some("red")))
    assertEquals(TerrainRules.subsetZooms(ls), Map("vector" -> Map("landuse" -> 8)))

  test("the notice rises above the credits only where the two would meet"):
    assertEquals(TerrainMap.noticeLift(10, 230, 156, 390, 18), 22.0, "a narrow map: side by side they overlap")
    assertEquals(TerrainMap.noticeLift(10, 310, 560, 1080, 18), 0.0, "a wide one: apart")
