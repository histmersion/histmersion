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
