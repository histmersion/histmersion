package com.histmersion.core

/** The terrain style documents: the built-in one, a person's file over it (good, partial, bad, with
  * unknown keys), the merge rules, zoom-dependent values and the label languages. Pure. */
class TerrainStyleSuite extends munit.FunSuite:

  private lazy val builtIn = TerrainStyleFile.builtInText()

  private def combine(user: String): TerrainStyleFile.Loaded =
    TerrainStyleFile.combine(builtIn, Some(("/home/me/.config/histmersion/basemap-style.conf", user)))
      .fold(e => fail(e.mkString("\n")), identity)

  private def style(l: TerrainStyleFile.Loaded, id: String): TerrainStyle = l.styles.find(_.id == id).getOrElse(fail(s"no $id in ${l.styles.map(_.id)}"))

  test("the built-in document is valid: three street maps and four terrain styles, Standard the default, every layer complete"):
    val l = TerrainStyleFile.combine(builtIn, None).fold(e => fail(e.mkString("\n")), identity)
    assertEquals(l.styles.map(_.id), List("standard", "muted", "dark", "terrain", "terrain-vegetation", "relief", "parchment"))
    assertEquals(l.styles.map(_.name), List("Standard", "Muted", "Dark", "Terrain", "Terrain + vegetation", "Relief only", "Parchment"))
    assertEquals((l.default, l.source, l.warnings), ("standard", TerrainStyles.BuiltIn, Nil))
    assertEquals(l.styles.filter(_.modern).map(_.id), List("standard", "muted", "dark"))
    val t = style(l, "terrain")
    assertEquals(t.layers.head.id, "land")
    assertEquals(t.layers.find(_.id == "forest").map(_.dataLayers), Some(List("landcover")), "land use is not drawn by default")
    assert(t.layers.forall(x => x.dataLayers.nonEmpty && TerrainStyles.Draws.contains(x.draw)), t.layers.toString)
    assert(t.hasLabels && t.labels)
    assertEquals(t.relief.exaggeration.at(0), 7.6)
    assertEqualsDouble(t.relief.exaggeration.at(11), 1.0, 1e-9)
    // the variants: inherited and patched by id
    val veg = style(l, "terrain-vegetation")
    assertEquals(veg.layers.map(_.id).filterNot(_.startsWith("osm-")), t.layers.map(_.id))
    assertEquals(veg.layers.filter(_.id.startsWith("osm-")).map(_.dataLayers), List.fill(3)(List("landuse")))
    assertEquals(veg.layers.indexWhere(_.id == "osm-wetland") + 1, veg.layers.indexWhere(_.id == "water"), "under the water")
    assertNotEquals(veg.layers.find(_.id == "forest").flatMap(_.fill), t.layers.find(_.id == "forest").flatMap(_.fill))
    assertEquals(veg.background, t.background)
    val relief = style(l, "relief")
    assert(!relief.labels)
    assert(!relief.layers.exists(_.id == "forest"), "no land cover")
    assertEquals(relief.relief.shadowOpacity, 0.85)
    assertEquals(relief.relief.azimuth, 315.0, "a relief key not given is the parent's")
    val parchment = style(l, "parchment")
    assertEquals(parchment.tone, "light")
    assertEquals(parchment.layers.find(_.id == "land").flatMap(_.fill), Some("#efe4c6"))
    assertEquals(parchment.layers.find(_.id == "land").map(_.geometry), Some(Some("polygon")), "a patch keeps the parent's other keys")

  test("the street maps: roads by class (casings under fills), borders, land use, place names; the layers they need"):
    val l = TerrainStyleFile.combine(builtIn, None).fold(e => fail(e.mkString("\n")), identity)
    val std = style(l, "standard")
    assertEquals(std.needs, List("boundaries", "earth", "landcover", "landuse", "places", "roads", "water"))
    assertEquals(List("terrain", "terrain-vegetation", "relief", "parchment").map(style(l, _).needs).flatten.distinct.sorted,
      List("earth", "landcover", "landuse", "pois", "water"), "the terrain styles need nothing the default download leaves out")
    val ids = std.layers.map(_.id)
    assert(ids.indexOf("case-highway") < ids.indexOf("minor") && ids.indexOf("major") < ids.indexOf("highway"), ids.toString)
    val roads = std.layers.filter(_.dataLayers == List("roads")).flatMap(_.kinds).distinct.sorted
    assertEquals(roads, List("aeroway", "highway", "major_road", "minor_road", "rail"))
    val places = std.layers.filter(l => l.draw == "label" && l.dataLayers == List("places"))
    assertEquals(places.map(_.id), List("countries", "cities", "regions", "towns", "villages", "hamlets"))
    assert(places.forall(_.ownMinZoom), "each place waits for its own min_zoom: the density")
    assert(std.layers.filter(_.draw != "label").forall(l => l.fill.orElse(l.color).exists(c => !c.startsWith("var("))),
      "its own colours, not the terrain tokens")
    assertEquals((std.tone, style(l, "muted").tone, style(l, "dark").tone), ("light", "light", "dark"))
    assert(!style(l, "muted").relief.enabled)
    assertEquals(style(l, "dark").layers.map(_.id), ids, "Dark patches Standard's layers, adds none")
    assertNotEquals(style(l, "dark").layers.find(_.id == "land").flatMap(_.fill), std.layers.find(_.id == "land").flatMap(_.fill))
    assert(style(l, "muted").modern && style(l, "dark").modern, "modern is inherited")

  test("zoom-dependent values: linear between stops, constant beyond, one stop a constant"):
    val v = ZoomValue(List(ZoomStop(4, 1), ZoomStop(8, 3), ZoomStop(10, 3)))
    assertEquals(v.at(0), 1.0)
    assertEquals(v.at(6), 2.0)
    assertEquals(v.at(9), 3.0)
    assertEquals(v.at(20), 3.0)
    assert(ZoomValue.const(2).constant && !v.constant)
    assertEquals(ZoomValue(Nil).at(5), 0.0)
    assertEquals(ZoomValue(List(ZoomStop(3, 1), ZoomStop(3, 5))).at(3), 1.0)

  test("label languages: English first by default, local alone, another language before English"):
    assertEquals(TerrainStyles.labelProps("en"), List("name:en", "name"))
    assertEquals(TerrainStyles.labelProps("local"), List("name"))
    assertEquals(TerrainStyles.labelProps("DE"), List("name:de", "name:en", "name"))
    assert(TerrainStyles.validLanguage("zh-Hans") && TerrainStyles.validLanguage("local"))
    assert(!TerrainStyles.validLanguage("English") && !TerrainStyles.validLanguage(""))

  test("colours: hex, functions, names and stylesheet tokens; nothing else"):
    List("#abc", "#abcd", "#a1b2c3", "#a1b2c380", "rgb(1, 2, 3)", "rgba(1,2,3,.5)", "hsl(30 50% 40%)", "Wheat",
      "transparent", "var(--terrain-land)").foreach(c => assert(TerrainStyleDoc.validColour(c), c))
    List("#ab", "#ggg", "blu", "url(x)", "var(terrain)", "expression(alert(1))", "").foreach(c =>
      assert(!TerrainStyleDoc.validColour(c), c))

  test("a good file: a new style extending a built-in one, a patched one, the default and the language"):
    val l = combine(
      """# mine
        |default = "night"
        |language = "local"
        |styles = [
        |  { id = "night", extends = "terrain", name = "Night", background = "#05070a",
        |    relief { shadow-opacity = 0.9 },
        |    layers = [
        |      { id = "land", fill = "#101418" },
        |      { id = "coast", type = "line", layer = "earth", geometry = "polygon", color = "#2a3440", width = 0.5, before = "forest" },
        |      { id = "streams", remove = true },
        |      { id = "glow", type = "line", layer = "water", kinds = "river", color = "white", width = [[5, 0.2], [10, 1]] }
        |    ] }
        |  { id = "terrain", extends = "terrain", labels = false }
        |  { id = "parchment", hidden = true }
        |]""".stripMargin)
    assertEquals(l.warnings, Nil)
    assertEquals(l.styles.map(_.id), List("standard", "muted", "dark", "terrain", "terrain-vegetation", "relief", "night"))
    assertEquals((l.default, l.language), ("night", Some("local")))
    assert(l.source.contains("basemap-style.conf over the built-in styles"), l.source)
    val n = style(l, "night")
    assertEquals(n.layers.map(_.id).take(3), List("land", "coast", "forest"))
    assert(!n.layers.exists(_.id == "streams"))
    assertEquals(n.layers.last.id, "glow")
    assertEquals(n.layers.last.kinds, List("river"), "one kind may be given as text")
    assertEqualsDouble(n.layers.last.width.map(_.at(7.5)).getOrElse(0.0), 0.6, 1e-9)
    assertEquals(n.relief.shadowOpacity, 0.9)
    assertEquals(n.relief.highlightOpacity, 0.2)
    assert(!style(l, "terrain").labels, "a built-in style extended by itself")
    assert(style(l, "terrain").layers.nonEmpty)

  test("a partial file: only the default or the language; JSON works too"):
    val l = combine("""{ "default": "relief" }""")
    assertEquals((l.default, l.styles.size, l.warnings), ("relief", 7, Nil))
    assertEquals(combine("").styles.size, 7)
    assertEquals(combine("""{"styles": [{"id": "relief", "extends": "relief", "relief": {"azimuth": 270}}]}""")
      .styles.find(_.id == "relief").map(_.relief.azimuth), Some(270.0))

  test("unknown keys are warnings with their place; the file is still used"):
    val l = combine("""styles = [ { id = "terrain", extends = "terrain", colour = "red", layers = [ { id = "land", fil = "#000" } ] } ]
                      |shade = 3""".stripMargin)
    assert(l.source.contains("over the built-in"), l.source)
    assert(l.warnings.exists(_.contains("styles[0].colour: unknown key, ignored")), l.warnings.toString)
    assert(l.warnings.exists(_.contains("styles[0].layers[0].fil: unknown key")), l.warnings.toString)
    assert(l.warnings.exists(_.contains("shade: unknown key")), l.warnings.toString)
    assertEquals(l.warnings.size, 3, l.warnings.toString)

  test("a bad file is refused whole, each error at its path, and the built-in styles stay"):
    val l = combine(
      """styles = [
        |  { id = "a", extends = "terrain", name = "A", background = "bluish",
        |    layers = [ { id = "land", width = "wide" }, { id = "x", type = "area", layer = "earth" },
        |               { id = "y", type = "line", layer = "water", color = "red", width = [[9, 1], [5, 2]] } ] }
        |  { id = "b", extends = "nothing", name = "B" }
        |  { id = "c bad" }
        |]""".stripMargin)
    assertEquals(l.source, TerrainStyles.BuiltIn)
    assertEquals(l.styles.map(_.id), List("standard", "muted", "dark", "terrain", "terrain-vegetation", "relief", "parchment"))
    val w = l.warnings.mkString("\n")
    assert(l.warnings.head.contains("is not used — the built-in terrain styles are shown instead"), w)
    assert(w.contains("styles[0].background: \"bluish\" is not a colour"), w)
    assert(w.contains("styles[0].layers[0].width: expected a number or [[zoom, value], …], got \"wide\""), w)
    assert(w.contains("styles[0].layers[1].type: \"area\" is not one of fill, line, label"), w)
    assert(w.contains("styles[0].layers[2].width: the stops' zooms must ascend"), w)
    assert(w.contains("styles[1].extends: no style 'nothing' to extend"), w)
    assert(w.contains("styles[2].id: \"c bad\": letters, digits, - and _ only"), w)

  test("what a style needs is checked after merging: a new style's name, background and a layer's colour"):
    val l = combine(
      """styles = [ { id = "new", layers = [ { id = "l", type = "fill", layer = "earth" } ] } ]""")
    val w = l.warnings.mkString("\n")
    assertEquals(l.source, TerrainStyles.BuiltIn, w)
    assert(w.contains("style 'new' needs a name"), w)
    assert(w.contains("style 'new' needs a background colour"), w)
    assert(w.contains("layer 'l' of style 'new' is a fill and needs a fill colour"), w)

  test("a syntax error names the line; a default that names no style and hiding every style are errors"):
    val syntax = combine("styles = [ { id = \"x\" ")
    assertEquals(syntax.source, TerrainStyles.BuiltIn)
    assert(syntax.warnings.exists(_.contains("basemap-style.conf: 1")), syntax.warnings.toString)
    assert(combine("""default = "nope"""").warnings.exists(_.contains("default: no style 'nope'")))
    val none = combine("""styles = [ {id=standard, hidden=true}, {id=muted, hidden=true}, {id=dark, hidden=true}, {id=terrain, hidden=true}, {id=terrain-vegetation, hidden=true}, {id=relief, hidden=true}, {id=parchment, hidden=true} ]""")
    assert(none.warnings.exists(_.contains("every style is hidden")), none.warnings.toString)
    assertEquals(none.styles.size, 7)

  test("merging layers by id: patch, remove, insert before, append; relief key by key"):
    import io.circe.Json
    def l(id: String, kv: (String, Json)*) = Json.obj(("id" -> Json.fromString(id)) +: kv*)
    val base = List(l("a", "fill" -> Json.fromString("#000")), l("b"), l("c"))
    val out = TerrainStyleDoc.mergeLayers(base, List(
      l("a", "fill" -> Json.fromString("#fff")), l("b", "remove" -> Json.True), l("n", "before" -> Json.fromString("c")), l("z"),
      l("gone", "remove" -> Json.True)))
    assertEquals(out.flatMap(_.hcursor.get[String]("id").toOption), List("a", "n", "c", "z"))
    assertEquals(out.head.hcursor.get[String]("fill").toOption, Some("#fff"))
    assert(out.forall(j => j.hcursor.downField("before").failed && j.hcursor.downField("remove").failed))
