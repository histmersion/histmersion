package com.histmersion.core

class WcagSuite extends munit.FunSuite:

  test("black on white is 21:1, a colour on itself 1:1, and the ratio is symmetric"):
    assertEqualsDouble(Wcag.contrast((0, 0, 0), (255, 255, 255)), 21.0, 1e-9)
    assertEqualsDouble(Wcag.contrast((120, 30, 200), (120, 30, 200)), 1.0, 1e-9)
    assertEqualsDouble(Wcag.contrast((10, 20, 30), (200, 210, 220)), Wcag.contrast((200, 210, 220), (10, 20, 30)), 1e-12)

  test("the reference value: #777 on white is just under AA (4.48)"):
    assertEqualsDouble(Wcag.contrast(Wcag.hex("#777").get, Wcag.hex("#ffffff").get), 4.48, 0.01)

  test("hex parses #rgb and #rrggbb and refuses anything else"):
    assertEquals(Wcag.hex("#fff"), Some((255, 255, 255)))
    assertEquals(Wcag.hex(" #1b1f27 "), Some((0x1b, 0x1f, 0x27)))
    assertEquals(Wcag.hex("var(--x)"), None)
    assertEquals(Wcag.hex("#12345"), None)

  test("the map's label tokens are named for both tones"):
    assertEquals(Palette.mapLabelTokens.length, 6)
    assert(Palette.allTokens.contains("--map-label-halo-dark"))
