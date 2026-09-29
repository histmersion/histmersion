package com.histmersion.server

import com.histmersion.core.*

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
