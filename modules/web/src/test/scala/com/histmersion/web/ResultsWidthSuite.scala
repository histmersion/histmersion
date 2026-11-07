package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

class ResultsWidthSuite extends munit.FunSuite:

  test("the column's width: clamped to the narrowest and to 60 % of the window"):
    assertEquals(ResultsWidth.clamp(com.histmersion.web.ResultsWidth.Width(100), com.histmersion.web.ResultsWidth.ClampViewport(1440)), ResultsWidth.min)
    assertEquals(ResultsWidth.max(com.histmersion.web.ResultsWidth.ViewportWidth(1440)), 800)
    assertEquals(ResultsWidth.max(com.histmersion.web.ResultsWidth.ViewportWidth(820)), 492)
    assertEquals(ResultsWidth.clamp(com.histmersion.web.ResultsWidth.Width(9999), com.histmersion.web.ResultsWidth.ClampViewport(820)), 492)
    assert(
      ui.Layout.sidebarDefault.value >= 462,
      "the default fits the five tabs with five-digit counts (measured: 462 px)"
    )

  test("the keys: ← wider, → narrower, Shift four times as far, Home and End the ends"):
    assertEquals(ResultsWidth.step(com.histmersion.web.ResultsWidth.Width(480), com.histmersion.web.ResultsWidth.KeyName("ArrowLeft"), shift = com.histmersion.web.ResultsWidth.ShiftHeld(false), com.histmersion.web.ResultsWidth.StepViewport(1440)), Some(496))
    assertEquals(ResultsWidth.step(com.histmersion.web.ResultsWidth.Width(480), com.histmersion.web.ResultsWidth.KeyName("ArrowRight"), shift = com.histmersion.web.ResultsWidth.ShiftHeld(true), com.histmersion.web.ResultsWidth.StepViewport(1440)), Some(416))
    assertEquals(ResultsWidth.step(com.histmersion.web.ResultsWidth.Width(480), com.histmersion.web.ResultsWidth.KeyName("Home"), shift = com.histmersion.web.ResultsWidth.ShiftHeld(false), com.histmersion.web.ResultsWidth.StepViewport(1440)), Some(ResultsWidth.min))
    assertEquals(ResultsWidth.step(com.histmersion.web.ResultsWidth.Width(480), com.histmersion.web.ResultsWidth.KeyName("End"), shift = com.histmersion.web.ResultsWidth.ShiftHeld(false), com.histmersion.web.ResultsWidth.StepViewport(1440)), Some(800))
    assertEquals(ResultsWidth.step(com.histmersion.web.ResultsWidth.Width(480), com.histmersion.web.ResultsWidth.KeyName("a"), shift = com.histmersion.web.ResultsWidth.ShiftHeld(false), com.histmersion.web.ResultsWidth.StepViewport(1440)), None)

  test("a kept width is read back; nonsense is none"):
    assertEquals(ResultsWidth.decode(Some(" 512 ")), Some(512))
    assertEquals(ResultsWidth.decode(Some("wide")), None)
    assertEquals(ResultsWidth.decode(Some("-3")), None)
