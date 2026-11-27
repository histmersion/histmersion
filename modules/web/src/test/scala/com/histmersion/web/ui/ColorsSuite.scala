package com.histmersion
package web.ui

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Palette
import com.histmersion.web.{Fmt, TestTokens}
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.media.MediaItem
import com.histmersion.api.library.Confidence
import com.histmersion.api.Wcag
import com.histmersion.lint.{tolerated, LintRule}

/** The palettes are the stylesheet's; the code holds their names and reads their values. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class ColorsSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  test("a category's token is named after it; anything unlisted falls back to the family default"):
    assertEquals(Palette.edge(Edge.Kind.Wire("references")), "--edge-references")
    assertEquals(Palette.edge(Edge.Kind.Wire("superseded_by")), "--edge-superseded_by")
    assertEquals(Palette.edge(Edge.Kind.Wire("nonexistent")), "--edge-default")
    assertEquals(Palette.status(Source.Status.Wire("current")), "--status-current")
    assertEquals(Palette.status(Source.Status.Wire("weird")), "--status-default")
    assertEquals(Palette.media(MediaItem.Kind("photo")), "--media-photo")
    assertEquals(Palette.media(MediaItem.Kind("x")), "--media-default")
    assertEquals(Palette.confidence(Confidence.Wire("low")), "--conf-low")

  test("the token list names every family, its default and the ramp"):
    val all = Palette.allTokens
    assertEquals(all.distinct, all)
    List(
      "--edge-default",
      "--status-default",
      "--media-default",
      "--ramp-0",
      "--ramp-4",
      "--conf-contested",
      "--graph-hover",
      "--place-pick"
    ).foreach(t => assert(all.contains(t), t))

  test("a token's value is read once and cached until a refresh"):
    var reads = 0
    ThemeTokens.lookup = n => { reads += 1; TestTokens.values.get(n.value).map(ThemeTokens.CssText(_)) }
    ThemeTokens.refresh()
    assertEquals(ThemeTokens(ThemeTokens.Name("--edge-references")), "#6ea8fe")
    assertEquals(ThemeTokens(ThemeTokens.Name("--edge-references")), "#6ea8fe")
    assertEquals(reads, 1)
    assertEquals(ThemeTokens(ThemeTokens.Name("--not-defined")), "")
    TestTokens.install()

  test("colour values parse as rgb(), #rrggbb and #rgb; anything else does not"):
    assertEquals(ThemeTokens.rgb(Wcag.Hex("rgb(68,1,84)")), Some((68, 1, 84)))
    assertEquals(ThemeTokens.rgb(Wcag.Hex(" rgb(68, 1, 84) ")), Some((68, 1, 84)))
    assertEquals(ThemeTokens.rgb(Wcag.Hex("#6ea8fe")), Some((110, 168, 254)))
    assertEquals(ThemeTokens.rgb(Wcag.Hex("#fff")), Some((255, 255, 255)))
    assertEquals(ThemeTokens.rgb(Wcag.Hex("var(--x)")), None)
    assertEquals(ThemeTokens.rgb(Wcag.Hex("")), None)

  test("the ramp is the five stops, in order"):
    assertEquals(ThemeTokens.ramp.length, Palette.rampStops)
    assertEquals(ThemeTokens.ramp.head, (68, 1, 84))
    assertEquals(ThemeTokens.ramp.last, (253, 231, 37))

  test("a ramp with no stops (no stylesheet) gives no colour rather than a wrong one"):
    assertEquals(Fmt.colorOn(Vector.empty, Fmt.RampPosition(0.5)), "")
    assertEquals(
      Fmt.colorOn(
        Vector(
          (Wcag.Channel(0), Wcag.Channel(0), Wcag.Channel(0)),
          (Wcag.Channel(100), Wcag.Channel(200), Wcag.Channel(50))
        ),
        Fmt.RampPosition(0.5)
      ),
      "rgb(50,100,25)"
    )
