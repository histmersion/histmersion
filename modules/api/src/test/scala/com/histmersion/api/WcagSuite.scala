package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

class WcagSuite extends munit.FunSuite:

  test("black on white is 21:1, a colour on itself 1:1, and the ratio is symmetric"):
    assertEqualsDouble(
      Wcag
        .contrast(
          (Wcag.Channel(0), Wcag.Channel(0), Wcag.Channel(0)),
          (Wcag.Channel(255), Wcag.Channel(255), Wcag.Channel(255))
        )
        .value,
      21.0,
      1e-9
    )
    assertEqualsDouble(
      Wcag
        .contrast(
          (Wcag.Channel(120), Wcag.Channel(30), Wcag.Channel(200)),
          (Wcag.Channel(120), Wcag.Channel(30), Wcag.Channel(200))
        )
        .value,
      1.0,
      1e-9
    )
    assertEqualsDouble(
      Wcag
        .contrast(
          (Wcag.Channel(10), Wcag.Channel(20), Wcag.Channel(30)),
          (Wcag.Channel(200), Wcag.Channel(210), Wcag.Channel(220))
        )
        .value,
      Wcag
        .contrast(
          (Wcag.Channel(200), Wcag.Channel(210), Wcag.Channel(220)),
          (Wcag.Channel(10), Wcag.Channel(20), Wcag.Channel(30))
        )
        .value,
      1e-12
    )

  test("the reference value: #777 on white is just under AA (4.48)"):
    assertEqualsDouble(
      Wcag.contrast(Wcag.hex(Wcag.Hex("#777")).get, Wcag.hex(Wcag.Hex("#ffffff")).get).value,
      4.48,
      0.01
    )

  test("hex parses #rgb and #rrggbb and refuses anything else"):
    assertEquals(Wcag.hex(Wcag.Hex("#fff")), Some((255, 255, 255)))
    assertEquals(Wcag.hex(Wcag.Hex(" #1b1f27 ")), Some((0x1b, 0x1f, 0x27)))
    assertEquals(Wcag.hex(Wcag.Hex("var(--x)")), None)
    assertEquals(Wcag.hex(Wcag.Hex("#12345")), None)

  test("the map's label tokens are named for both tones"):
    assertEquals(Palette.mapLabelTokens.length, 6)
    assert(Palette.allTokens.contains("--map-label-halo-dark"))
