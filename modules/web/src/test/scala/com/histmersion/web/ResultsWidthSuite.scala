package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.web.ui.Layout

class ResultsWidthSuite extends munit.FunSuite:

  test("the column's width: clamped to the narrowest and to 60 % of the window"):
    assertEquals(ResultsWidth.clamp(ResultsWidth.Width(100), ResultsWidth.ClampViewport(1440)), ResultsWidth.min)
    assertEquals(ResultsWidth.max(ResultsWidth.ViewportWidth(1440)), 800)
    assertEquals(ResultsWidth.max(ResultsWidth.ViewportWidth(820)), 492)
    assertEquals(ResultsWidth.clamp(ResultsWidth.Width(9999), ResultsWidth.ClampViewport(820)), 492)
    assert(
      Layout.sidebarDefault.value >= 462,
      "the default fits the five tabs with five-digit counts (measured: 462 px)"
    )

  test("the keys: ← wider, → narrower, Shift four times as far, Home and End the ends"):
    assertEquals(ResultsWidth.step(ResultsWidth.Width(480), ResultsWidth.KeyName("ArrowLeft"), shift = ResultsWidth.ShiftHeld(false), ResultsWidth.StepViewport(1440)), Some(496))
    assertEquals(ResultsWidth.step(ResultsWidth.Width(480), ResultsWidth.KeyName("ArrowRight"), shift = ResultsWidth.ShiftHeld(true), ResultsWidth.StepViewport(1440)), Some(416))
    assertEquals(ResultsWidth.step(ResultsWidth.Width(480), ResultsWidth.KeyName("Home"), shift = ResultsWidth.ShiftHeld(false), ResultsWidth.StepViewport(1440)), Some(ResultsWidth.min))
    assertEquals(ResultsWidth.step(ResultsWidth.Width(480), ResultsWidth.KeyName("End"), shift = ResultsWidth.ShiftHeld(false), ResultsWidth.StepViewport(1440)), Some(800))
    assertEquals(ResultsWidth.step(ResultsWidth.Width(480), ResultsWidth.KeyName("a"), shift = ResultsWidth.ShiftHeld(false), ResultsWidth.StepViewport(1440)), None)

  test("a kept width is read back; nonsense is none"):
    assertEquals(ResultsWidth.decode(Some(" 512 ")), Some(512))
    assertEquals(ResultsWidth.decode(Some("wide")), None)
    assertEquals(ResultsWidth.decode(Some("-3")), None)
