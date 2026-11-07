package com.histmersion.web

class ChipInputSuite extends munit.FunSuite:

  test("a topic hint shows spaces, not underscores") {
    assertEquals(ChipInput.hintLabel(com.histmersion.web.Chip.Choice("bronze_age_collapse")).value, "bronze age collapse")
  }

  test("a hint is not HTML-escaped (it is a text node; Laminar escapes it)") {
    assertEquals(ChipInput.hintLabel(com.histmersion.web.Chip.Choice("Bell & Beaker")).value, "Bell & Beaker")
  }
