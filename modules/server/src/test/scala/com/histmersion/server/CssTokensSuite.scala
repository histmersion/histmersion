package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.Confidence

/** The colour tokens the code names ([[Palette]]) are defined in the stylesheet it ships. A token
  * the stylesheet lacks renders as nothing at all — a transparent dot, an invisible edge — and no
  * other test would notice. Reads the served `web/style.css` off the classpath, as [[WebAssetsSuite]]
  * does. */
class CssTokensSuite extends munit.FunSuite:

  private val css = Static.read("web/style.css")

  /** `--name: value` declarations in the `:root` blocks. */
  private val rootTokens: Map[String, String] =
    """(?s):root\s*\{(.*?)\}""".r.findAllMatchIn(css).map(_.group(1))
      .flatMap(b => """(--[\w-]+)\s*:\s*([^;]+);""".r.findAllMatchIn(b).map(m => m.group(1) -> m.group(2).trim))
      .toMap

  test("every token the code names is defined on :root"):
    val missing = Palette.allTokens.filterNot(rootTokens.contains)
    assertEquals(missing, Nil, "define these in modules/server/src/main/resources/web/style.css")

  test("the confidence colours are the ramp's midpoint desaturated exactly as the map's dots are"):
    val mid = rootTokens("--ramp-2")
    Confidence.values.foreach { c =>
      val level = c.toString.toLowerCase
      assertEquals(rootTokens(Palette.confidence(level)).replace(" ", ""),
        ConfidenceStyle.desaturate(mid.replace(" ", ""), c), level)
    }

  test("every article review state has a badge colour"):
    val missing = ReviewState.values.toList.map(_.wire).filterNot(w => css.contains(s".job-state.review-state.state-$w {"))
    assertEquals(missing, Nil)

  test("every var(--…) the stylesheet uses for a colour category is one it defines"):
    val families = List("--edge-", "--status-", "--media-", "--conf-", "--ramp-", "--place-", "--graph-")
    val used = """var\((--[\w-]+)""".r.findAllMatchIn(css).map(_.group(1)).toSet
      .filter(t => families.exists(t.startsWith))
    assertEquals(used.filterNot(rootTokens.contains).toList.sorted, Nil)

  // #1442: the map's names were white text with a soft shadow, unreadable on a bright tile (Alashiya,
  // on Cyprus's pale coast). Halo text must hold WCAG AA against its own halo AND against the tiles
  // themselves (where the 3 px halo thins out at a glyph's edge), on light tiles and on dark ones.
  /** Colours the OSM tiles draw large areas in: land, water, forest, farmland, sand, built-up, and the
    * HOT style's land; the dark set is the same tiles as `.map-tiles-dark` draws them (inverted, 90%). */
  private val lightTiles = List("#f2efe9", "#aad3df", "#add19e", "#eef0d5", "#fff1ba", "#e0dfdf", "#f8f4f0", "#c8facc")
  private def darkened(c: (Int, Int, Int)): (Int, Int, Int) =
    def d(v: Int) = ((255 - v) * 0.9).round.toInt
    (d(c._1), d(c._2), d(c._3))

  test("#1442: map names are AA on their halo and on every light and dark tile colour"):
    def colour(t: String) = Wcag.hex(rootTokens(t)).getOrElse(fail(s"$t is not a hex colour: ${rootTokens(t)}"))
    val light = lightTiles.map(c => Wcag.hex(c).get)
    for (tone, tiles) <- List("light" -> light, "dark" -> light.map(darkened)) do
      val ink  = colour(s"--map-label-ink-$tone")
      val halo = colour(s"--map-label-halo-$tone")
      val note = colour(s"--map-label-note-$tone")
      assert(Wcag.contrast(ink, halo) >= 7.0, s"$tone: ink on halo ${Wcag.contrast(ink, halo)}")
      assert(Wcag.contrast(note, halo) >= Wcag.AaText, s"$tone: note on halo ${Wcag.contrast(note, halo)}")
      for t <- tiles do
        assert(Wcag.contrast(ink, t) >= Wcag.AaText, s"$tone: ink on tile $t: ${Wcag.contrast(ink, t)}")
        assert(Wcag.contrast(note, t) >= Wcag.AaText, s"$tone: note on tile $t: ${Wcag.contrast(note, t)}")

  test("#1442: the dark tone swaps the names' colours, and names use the tone's tokens"):
    val dark = """(?s)\.map-tone-dark\s*\{(.*?)\}""".r.findFirstMatchIn(css).map(_.group(1)).getOrElse(fail("no .map-tone-dark"))
    for part <- List("ink", "halo", "note") do
      assert(dark.contains(s"--map-label-$part:var(--map-label-$part-dark)"), dark)
      assertEquals(rootTokens(s"--map-label-$part"), s"var(--map-label-$part-light)")
    val label = """(?s)\.hd-map-label, \.hd-area-point\s*\{(.*?)\}""".r.findFirstMatchIn(css).map(_.group(1)).getOrElse("")
    assert(label.contains("paint-order:stroke") && label.contains("var(--map-label-halo)"), label)
    assert(!label.contains("text-shadow"), label)

  // The map's panes (core MapStack): every point pane above every area pane, in the order the code
  // declares, the canvas (the areas) under them all, and a pane itself never a pointer target.

  /** The declarations of the rules for exactly `.<cls>` (the last one wins, as in the browser). */
  private def rules(cls: String): List[String] =
    s"""(?s)\\.${java.util.regex.Pattern.quote(cls)}\\s*\\{([^}]*)\\}""".r.findAllMatchIn(css).toList.map(_.group(1))

  /** A rule's z-index: a number, or a `var(--token)` of the scale on `:root`. */
  private def zIndex(cls: String): Option[Int] =
    rules(cls).flatMap(r => """z-index\s*:\s*(\d+|var\((--[\w-]+)\))""".r.findFirstMatchIn(r).map(m =>
      Option(m.group(2)).fold(m.group(1).toInt)(t => rootTokens(t).trim.toInt))).lastOption

  test("the map's panes stack in MapStack's order, every point above every area"):
    assert(MapStack.pointsAboveAreas(MapStack.order), MapStack.order.toString)
    val z = MapStack.domPanes.map(p => p -> zIndex(p.cssClass).getOrElse(fail(s"no z-index for .${p.cssClass}")))
    z.sliding(2).foreach {
      case List((a, za), (b, zb)) => assert(za < zb, s"${a.name} ($za) must be under ${b.name} ($zb)")
      case _ => ()
    }
    // then the controls in the map's corners (#3844: the legend and the notice drew under the markers),
    // then the popups, which open over everything on the map
    val controls = rootTokens("--z-map-controls").trim.toInt
    val popups = zIndex("hd-popup").getOrElse(fail("no z-index for .hd-popup"))
    assert(z.last._2 < controls, s"the top pane (${z.last._2}) must be under the corners ($controls)")
    assert(controls < popups, s"the corners ($controls) must be under the popups ($popups)")
    assert(popups < 1000, "the map's popups stay under the page's windows and modals")
    assert("""\.map-container \.hd-map-corner\s*\{[^}]*z-index:var\(--z-map-controls\)""".r.findFirstIn(css).isDefined,
      "the corners take the controls' z-index")

  test("a pane lets the pointer through everywhere but its markers"):
    val pane = """(?s)\.hd-pane\s*\{([^}]*)\}""".r.findFirstMatchIn(css).map(_.group(1)).getOrElse(fail("no .hd-pane rule"))
    assert(pane.contains("pointer-events:none"), pane)

  // ── the theme: one token set, light and dark (the web UI's visual system) ──────────────────────────

  private def declarations(block: String): Map[String, String] =
    """(--[\w-]+)\s*:\s*([^;]+);""".r.findAllMatchIn(block).map(m => m.group(1) -> m.group(2).trim).toMap

  /** The dark set chosen with the theme button, and the one the system's setting picks under "system". */
  private val darkChosen: Map[String, String] =
    declarations("""(?s):root\[data-theme="dark"\]\s*\{(.*?)\}""".r.findFirstMatchIn(css).map(_.group(1))
      .getOrElse(fail("no :root[data-theme=\"dark\"] block")))
  private val darkSystem: Map[String, String] =
    declarations("""(?s)@media \(prefers-color-scheme: dark\)\s*\{\s*:root:not\(\[data-theme="light"\]\)\s*\{(.*?)\}""".r
      .findFirstMatchIn(css).map(_.group(1)).getOrElse(fail("no prefers-color-scheme: dark block")))

  /** A theme's resolved colour: its own value, `var(--x)` followed, as a hex triple. */
  private def colour(theme: Map[String, String], token: String): (Int, Int, Int) =
    def resolve(t: String, depth: Int): String =
      val v = theme.getOrElse(t, rootTokens.getOrElse(t, fail(s"$t is not defined")))
      """^var\((--[\w-]+)\)$""".r.findFirstMatchIn(v).fold(v)(m => if depth > 5 then v else resolve(m.group(1), depth + 1))
    val v = resolve(token, 0)
    Wcag.hex(v).getOrElse(fail(s"$token is not a hex colour: $v"))

  private val light: Map[String, String] = rootTokens
  private val dark: Map[String, String]  = rootTokens ++ darkChosen

  test("the system's dark theme is the button's dark theme, token for token"):
    assertEquals(darkSystem, darkChosen)

  test("the dark theme redefines every colour the light theme's surfaces, text and states use"):
    val themed = List("--bg", "--panel", "--sunk", "--ink", "--ink2", "--ink3", "--line", "--line2", "--control-line",
      "--accent", "--on-accent", "--accent-soft", "--time", "--time-soft", "--time-fill", "--attn", "--attn-soft",
      "--ok", "--ok-soft", "--danger", "--danger-soft", "--mark-bg", "--sh-pop", "--sh-win", "--scrim") ++
      Palette.statuses.map(Palette.status) ++ Palette.edgeKinds.map(Palette.edge) ++
      Palette.goalStages.flatMap(s => List(Palette.goalStage(s), Palette.goalStageSoft(s)))
    assertEquals(themed.filterNot(darkChosen.contains), Nil)
    assertEquals(themed.filterNot(rootTokens.contains), Nil)

  /** Text on a surface: WCAG 2.2 AA, 4.5:1 (every text size the UI uses is under 18.66 px bold). */
  private val textPairs: List[(String, String)] =
    (for fg <- List("--ink", "--ink2", "--ink3", "--muted", "--accent"); bg <- List("--bg", "--panel", "--sunk", "--accent-soft")
      yield fg -> bg) ++
      List("--on-accent" -> "--accent", "--time" -> "--panel", "--time" -> "--bg", "--time" -> "--time-soft",
        "--attn" -> "--panel", "--attn" -> "--attn-soft", "--ok" -> "--panel", "--ok" -> "--ok-soft",
        "--danger" -> "--panel", "--danger" -> "--danger-soft", "--ink" -> "--time-soft", "--ink" -> "--attn-soft",
        "--ink" -> "--ok-soft", "--ink" -> "--danger-soft", "--mark-ink" -> "--mark-bg", "--ink2" -> "--accent-soft") ++
      // a goal stage's word on its badge (planned's word is the secondary ink, done's the green of done)
      List("--ink2" -> "--stage-planned-soft", "--stage-seeding" -> "--stage-seeding-soft",
        "--stage-compiled" -> "--stage-compiled-soft", "--stage-reviewed" -> "--stage-reviewed-soft", "--ok" -> "--ok-soft")

  /** A control's edge, the focus ring and the source-status marks against what they sit on: 3:1. */
  private val componentPairs: List[(String, String)] =
    for
      fg <- List("--control-line", "--accent") ++ Palette.statuses.map(Palette.status) ++ Palette.goalStages.map(Palette.goalStage)
      bg <- List("--bg", "--panel", "--sunk")
    yield fg -> bg

  for (name, theme) <- List("light" -> light, "dark" -> dark) do
    test(s"$name theme: every text pair is WCAG 2.2 AA (4.5:1)"):
      val low = textPairs.map((f, b) => (f, b, Wcag.contrast(colour(theme, f), colour(theme, b)))).filter(_._3 < Wcag.AaText)
      assertEquals(low.map((f, b, c) => f"$f on $b: $c%.2f"), Nil)

    test(s"$name theme: every control edge and status mark is WCAG 2.2 AA for components (3:1)"):
      val low = componentPairs.map((f, b) => (f, b, Wcag.contrast(colour(theme, f), colour(theme, b)))).filter(_._3 < 3.0)
      assertEquals(low.map((f, b, c) => f"$f on $b: $c%.2f"), Nil)

  /** The stylesheet's rules, without the token blocks and the font faces: what must NAME tokens. */
  private lazy val rules: String =
    css.replaceAll("""(?s)/\*.*?\*/""", "")
      .replaceAll("""(?s):root(\[data-theme="dark"\]|:not\(\[data-theme="light"\]\))?\s*\{[^}]*\}""", "")
      .replaceAll("""(?s)\[data-layout="narrow"\](\[data-tablet\])?\s*\{[^}]*\}""", "")
      .replaceAll("""(?s)@font-face\s*\{[^}]*\}""", "")

  test("rules name colour tokens, never colours (but a mask's opaque stop)"):
    val literals = """#[0-9a-fA-F]{3,8}\b|rgba?\(\s*\d""".r.findAllMatchIn(rules).map(m =>
      rules.substring(math.max(0, m.start - 40), math.min(rules.length, m.end + 20)).replace('\n', ' ')).toList
    assertEquals(literals.filterNot(_.contains("linear-gradient(to bottom, #000")), Nil)

  test("type: six sizes, two weights, three radii, two shadows — tokens, not numbers"):
    val sizes   = """font-size:\s*([^;}]+)""".r.findAllMatchIn(rules).map(_.group(1).trim).toSet
    // the narrow layout's own scale (`--fs`, the 16 px floor that keeps a phone from zooming into a field) and a
    // map glyph's size, which its marker sets
    assertEquals(sizes.filterNot(v => v.startsWith("var(--fs") || v.endsWith("em") || v == "inherit" ||
      v.startsWith("var(--glyph-size") || v == "calc(var(--fs) * 1.1)" || v == "max(16px, var(--fs))"), Set.empty[String])
    val weights = """font-weight:\s*([^;}]+)""".r.findAllMatchIn(rules).map(_.group(1).trim).toSet
    assertEquals(weights.filterNot(_.startsWith("var(--fw-")), Set.empty[String])
    assertEquals(Set(rootTokens("--fs-xs"), rootTokens("--fs-sm"), rootTokens("--fs-md"), rootTokens("--fs-lg"),
      rootTokens("--fs-xl"), rootTokens("--fs-2xl")), Set("12px", "13px", "14px", "16px", "20px", "26px"))
    val radii = """border-radius:\s*([^;}]+)""".r.findAllMatchIn(rules).map(_.group(1).trim).toSet
    // 50 %: a round mark is a shape, not a corner; 1 px: the status code's square and diamond; 0: none
    assertEquals(radii.filterNot(v => v.startsWith("var(--radius") || v.startsWith("var(--badge-corner") ||
      Set("50%", "1px", "0").contains(v) || v.matches("""var\(--radius-lg\) var\(--radius-lg\) 0 0""")), Set.empty[String])
    val elevations = """box-shadow:\s*([^;}]+)""".r.findAllMatchIn(rules).map(_.group(1).trim)
      .filter(v => """\d+px \d+px""".r.findFirstIn(v).exists(_ => !v.startsWith("0 0 ") && !v.startsWith("inset")))
      .toSet
    // a drop shadow with an offset is one of the two elevations (rings and outlines are 0 0 …)
    assertEquals(elevations.filterNot(v => v.contains("var(--marker-shadow)") || v.contains("var(--glyph-edge)")), Set.empty[String])

  test("no badge or label is upper-cased by CSS (only the map's large region names, a cartographic style)"):
    val upper = """([^{}]+)\{[^}]*text-transform:\s*uppercase""".r.findAllMatchIn(rules).map(_.group(1).trim).toList
    // a first letter raised is sentence case, not capitals
    assertEquals(upper.filterNot(_.endsWith("::first-letter")), List(".hd-map-label--l"))

  test("the interface and the reading text are the bundled faces, with a fallback, and one monospace stack"):
    assert(rootTokens("--font-ui").startsWith("\"Source Sans 3\""), rootTokens("--font-ui"))
    assert(rootTokens("--font-read").startsWith("\"Source Serif 4\""), rootTokens("--font-read"))
    assert(rootTokens("--font-ui").endsWith("sans-serif") && rootTokens("--font-read").endsWith("serif"))
    for (family, file) <- List("Source Sans 3" -> "SourceSans3", "Source Serif 4" -> "SourceSerif4"); (w, f) <- List(400 -> "Regular", 600 -> "Semibold") do
      assert(css.contains(s"""font-family:"$family"; font-style:normal; font-weight:$w; font-display:swap;\n  src:url("vendor/source-fonts/$file-$f.woff2")"""),
        s"$family $w")
      assert(Option(getClass.getClassLoader.getResource(s"web/vendor/source-fonts/$file-$f.woff2")).isDefined, s"$file-$f.woff2 is not bundled")
    val monos = """font(?:-family)?:[^;}]*(monospace)""".r.findAllMatchIn(rules).toList
    assertEquals(monos, Nil, "a monospace face is var(--font-mono)")
    val reading = """(?s)\.reading \{([^}]*)\}""".r.findFirstMatchIn(css).map(_.group(1)).getOrElse(fail("no .reading rule"))
    assert(reading.contains("font-family:var(--font-read)") && reading.contains("font-size:var(--fs-lg)") &&
      reading.contains("line-height:var(--read-lh)") && reading.contains("max-width:var(--read-measure)"), reading)
    assertEquals((rootTokens("--read-lh"), rootTokens("--read-measure")), ("1.6", "62ch"))

  test("every source status has a mark (colour + shape) and its word is not upper-cased"):
    Palette.statuses.foreach(s => assert(css.contains(s".st-$s::before"), s"no mark for $s"))
    assert("""\.status-badge\.st-retracted[^{]*\{[^}]*line-through""".r.findFirstIn(css).isDefined, "retracted is struck through")
    assert("""\.st-contested::before[^{]*\{[^}]*rotate\(45deg\)""".r.findFirstIn(css).isDefined, "contested is a diamond")
    assert("""\.st-unassessed::before[^{]*\{[^}]*dashed""".r.findFirstIn(css).isDefined, "not assessed is a dashed ring")
    assert("""\.st-superseded::before[^{]*\{[^}]*border:2px solid""".r.findFirstIn(css).isDefined, "superseded is a ring")
    assert(!css.contains(".cite-tip-status."), "the cards' own status palette is gone")

  test("a state's colour means one thing: done is the same green everywhere, seeding is not an error"):
    def bg(sel: String) = """(?s)""" + java.util.regex.Pattern.quote(sel) + """ \{([^}]*)\}"""
    def decl(sel: String) = bg(sel).r.findFirstMatchIn(css).map(_.group(1)).getOrElse(fail(s"no rule $sel"))
    val done = List(".job-state.state-done", ".job-state.goal-status.state-done", ".job-state.review-state.state-passed",
      ".job-state.state-approved").map(decl)
    done.foreach(d => assert(d.contains("background:var(--ok-soft); color:var(--ok)"), d))
    val seeding = decl(".job-state.goal-status.state-seeding")
    assert(!seeding.contains("danger") && !seeding.contains("health-down"), seeding)

  /** The colours a goal stage's badge is drawn with: its ground, its word and its mark, from its rules. */
  private def stageLook(stage: String): (String, String, String) =
    def rule(sel: String) = ("""(?s)""" + java.util.regex.Pattern.quote(sel) + """ \{([^}]*)\}""").r.findFirstMatchIn(css)
      .map(_.group(1)).getOrElse(fail(s"no rule $sel"))
    val badge = rule(s".job-state.goal-status.state-$stage")
    val mark  = rule(s".job-state.goal-status.state-$stage::before")
    def value(decl: String, prop: String) = s"""$prop:([^;]+);?""".r.findFirstMatchIn(decl).map(_.group(1).trim).getOrElse("")
    (value(badge, "background"), value(badge, "color"), mark)

  test("#7381: every goal stage has its own ground, word colour and mark shape — seeding and compiled no longer share one"):
    val looks = Palette.goalStages.map(s => s -> stageLook(s)).toMap
    assertEquals(looks.values.map(_._1).toSet.size, Palette.goalStages.size, s"grounds: $looks")
    assertEquals(looks.values.map(_._2).toSet.size, Palette.goalStages.size, s"words: $looks")
    assertNotEquals(looks("seeding"), looks("compiled"))
    // the shape: a dashed ring, a half-filled circle, a square, a diamond, a dot
    assert(looks("planned")._3.contains("dashed"), looks("planned")._3)
    assert(looks("seeding")._3.contains("linear-gradient"), looks("seeding")._3)
    assert(looks("compiled")._3.contains("border-radius:1px") && !looks("compiled")._3.contains("rotate"), looks("compiled")._3)
    assert(looks("reviewed")._3.contains("rotate(45deg)"), looks("reviewed")._3)
    assert(looks("done")._3.contains("var(--stage-done)"), looks("done")._3)
    for (name, theme) <- List("light" -> light, "dark" -> dark); s <- Palette.goalStages do
      val (bg, fg, _) = looks(s)
      def tokenOf(v: String) = v.stripPrefix("var(").stripSuffix(")")
      val c = Wcag.contrast(colour(theme, tokenOf(fg)), colour(theme, tokenOf(bg)))
      assert(c >= Wcag.AaText, f"$name: $s's word on its ground is $c%.2f")

  test("#7381: a link is the accent in both themes, visited or not — never the browser's purple or pink"):
    assert(css.contains(":where(a) { color:var(--accent); }"), "every link takes the accent")
    assert(""":visited\s*\{""".r.findFirstIn(rules).isEmpty, "no rule gives visited links a colour of their own")
    for (name, theme) <- List("light" -> light, "dark" -> dark); bg <- List("--bg", "--panel", "--sunk") do
      assert(Wcag.contrast(colour(theme, "--accent"), colour(theme, bg)) >= Wcag.AaText, s"$name: the link on $bg")
