package com.histmersion.web

class ResultsWidthSuite extends munit.FunSuite:

  test("the column's width: clamped to the narrowest and to 60 % of the window"):
    assertEquals(ResultsWidth.clamp(100, 1440), ResultsWidth.min)
    assertEquals(ResultsWidth.max(1440), 800)
    assertEquals(ResultsWidth.max(820), 492)
    assertEquals(ResultsWidth.clamp(9999, 820), 492)
    assert(
      ui.Layout.sidebarDefault.value >= 462,
      "the default fits the five tabs with five-digit counts (measured: 462 px)"
    )

  test("the keys: ← wider, → narrower, Shift four times as far, Home and End the ends"):
    assertEquals(ResultsWidth.step(480, "ArrowLeft", shift = false, 1440), Some(496))
    assertEquals(ResultsWidth.step(480, "ArrowRight", shift = true, 1440), Some(416))
    assertEquals(ResultsWidth.step(480, "Home", shift = false, 1440), Some(ResultsWidth.min))
    assertEquals(ResultsWidth.step(480, "End", shift = false, 1440), Some(800))
    assertEquals(ResultsWidth.step(480, "a", shift = false, 1440), None)

  test("a kept width is read back; nonsense is none"):
    assertEquals(ResultsWidth.decode(Some(" 512 ")), Some(512))
    assertEquals(ResultsWidth.decode(Some("wide")), None)
    assertEquals(ResultsWidth.decode(Some("-3")), None)
