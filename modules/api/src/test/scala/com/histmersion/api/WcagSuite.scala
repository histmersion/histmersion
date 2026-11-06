package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

class WcagSuite extends munit.FunSuite:

  test("black on white is 21:1, a colour on itself 1:1, and the ratio is symmetric"):
    assertEqualsDouble(Wcag.contrast((com.histmersion.api.Wcag.Channel(0), com.histmersion.api.Wcag.Channel(0), com.histmersion.api.Wcag.Channel(0)), (com.histmersion.api.Wcag.Channel(255), com.histmersion.api.Wcag.Channel(255), com.histmersion.api.Wcag.Channel(255))), 21.0, 1e-9)
    assertEqualsDouble(Wcag.contrast((com.histmersion.api.Wcag.Channel(120), com.histmersion.api.Wcag.Channel(30), com.histmersion.api.Wcag.Channel(200)), (com.histmersion.api.Wcag.Channel(120), com.histmersion.api.Wcag.Channel(30), com.histmersion.api.Wcag.Channel(200))), 1.0, 1e-9)
    assertEqualsDouble(
      Wcag.contrast((com.histmersion.api.Wcag.Channel(10), com.histmersion.api.Wcag.Channel(20), com.histmersion.api.Wcag.Channel(30)), (com.histmersion.api.Wcag.Channel(200), com.histmersion.api.Wcag.Channel(210), com.histmersion.api.Wcag.Channel(220))),
      Wcag.contrast((com.histmersion.api.Wcag.Channel(200), com.histmersion.api.Wcag.Channel(210), com.histmersion.api.Wcag.Channel(220)), (com.histmersion.api.Wcag.Channel(10), com.histmersion.api.Wcag.Channel(20), com.histmersion.api.Wcag.Channel(30))),
      1e-12
    )

  test("the reference value: #777 on white is just under AA (4.48)"):
    assertEqualsDouble(Wcag.contrast(Wcag.hex(com.histmersion.api.Wcag.Hex("#777")).get, Wcag.hex(com.histmersion.api.Wcag.Hex("#ffffff")).get), 4.48, 0.01)

  test("hex parses #rgb and #rrggbb and refuses anything else"):
    assertEquals(Wcag.hex(com.histmersion.api.Wcag.Hex("#fff")), Some((255, 255, 255)))
    assertEquals(Wcag.hex(com.histmersion.api.Wcag.Hex(" #1b1f27 ")), Some((0x1b, 0x1f, 0x27)))
    assertEquals(Wcag.hex(com.histmersion.api.Wcag.Hex("var(--x)")), None)
    assertEquals(Wcag.hex(com.histmersion.api.Wcag.Hex("#12345")), None)

  test("the map's label tokens are named for both tones"):
    assertEquals(Palette.mapLabelTokens.length, 6)
    assert(Palette.allTokens.contains("--map-label-halo-dark"))
