package com.histmersion.web.ui

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Palette
import com.histmersion.web.{Fmt, TestTokens}

/** The palettes are the stylesheet's; the code holds their names and reads their values. */
class ColorsSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  test("a category's token is named after it; anything unlisted falls back to the family default"):
    assertEquals(Palette.edge(com.histmersion.api.library.Edge.Kind.Wire("references")), "--edge-references")
    assertEquals(Palette.edge(com.histmersion.api.library.Edge.Kind.Wire("superseded_by")), "--edge-superseded_by")
    assertEquals(Palette.edge(com.histmersion.api.library.Edge.Kind.Wire("nonexistent")), "--edge-default")
    assertEquals(Palette.status(com.histmersion.api.library.Source.Status.Wire("current")), "--status-current")
    assertEquals(Palette.status(com.histmersion.api.library.Source.Status.Wire("weird")), "--status-default")
    assertEquals(Palette.media(com.histmersion.api.media.MediaItem.Kind("photo")), "--media-photo")
    assertEquals(Palette.media(com.histmersion.api.media.MediaItem.Kind("x")), "--media-default")
    assertEquals(Palette.confidence(com.histmersion.api.library.Confidence.Wire("low")), "--conf-low")

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
    ThemeTokens.lookup = n => { reads += 1; TestTokens.values.get(n) }
    ThemeTokens.refresh()
    assertEquals(ThemeTokens(com.histmersion.web.ui.ThemeTokens.Name("--edge-references")), "#6ea8fe")
    assertEquals(ThemeTokens(com.histmersion.web.ui.ThemeTokens.Name("--edge-references")), "#6ea8fe")
    assertEquals(reads, 1)
    assertEquals(ThemeTokens(com.histmersion.web.ui.ThemeTokens.Name("--not-defined")), "")
    TestTokens.install()

  test("colour values parse as rgb(), #rrggbb and #rgb; anything else does not"):
    assertEquals(ThemeTokens.rgb(com.histmersion.api.Wcag.Hex("rgb(68,1,84)")), Some((68, 1, 84)))
    assertEquals(ThemeTokens.rgb(com.histmersion.api.Wcag.Hex(" rgb(68, 1, 84) ")), Some((68, 1, 84)))
    assertEquals(ThemeTokens.rgb(com.histmersion.api.Wcag.Hex("#6ea8fe")), Some((110, 168, 254)))
    assertEquals(ThemeTokens.rgb(com.histmersion.api.Wcag.Hex("#fff")), Some((255, 255, 255)))
    assertEquals(ThemeTokens.rgb(com.histmersion.api.Wcag.Hex("var(--x)")), None)
    assertEquals(ThemeTokens.rgb(com.histmersion.api.Wcag.Hex("")), None)

  test("the ramp is the five stops, in order"):
    assertEquals(ThemeTokens.ramp.length, Palette.rampStops)
    assertEquals(ThemeTokens.ramp.head, (68, 1, 84))
    assertEquals(ThemeTokens.ramp.last, (253, 231, 37))

  test("a ramp with no stops (no stylesheet) gives no colour rather than a wrong one"):
    assertEquals(Fmt.colorOn(Vector.empty, 0.5), "")
    assertEquals(Fmt.colorOn(Vector((com.histmersion.api.Wcag.Channel(0), com.histmersion.api.Wcag.Channel(0), com.histmersion.api.Wcag.Channel(0)), (com.histmersion.api.Wcag.Channel(100), com.histmersion.api.Wcag.Channel(200), com.histmersion.api.Wcag.Channel(50))), 0.5), "rgb(50,100,25)")
