package com.histmersion
package web

class ChipInputSuite extends munit.FunSuite:

  test("a topic hint shows spaces, not underscores") {
    assertEquals(ChipInput.hintLabel(Chip.Choice("bronze_age_collapse")).value, "bronze age collapse")
  }

  test("a hint is not HTML-escaped (it is a text node; Laminar escapes it)") {
    assertEquals(ChipInput.hintLabel(Chip.Choice("Bell & Beaker")).value, "Bell & Beaker")
  }
