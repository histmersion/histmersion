package com.histmersion
package web

import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ChipInputSuite extends munit.FunSuite:

  test("a topic hint shows spaces, not underscores") {
    assertEquals(ChipInput.hintLabel(Chip.Choice("bronze_age_collapse")).value, "bronze age collapse")
  }

  test("a hint is not HTML-escaped (it is a text node; Laminar escapes it)") {
    assertEquals(ChipInput.hintLabel(Chip.Choice("Bell & Beaker")).value, "Bell & Beaker")
  }
