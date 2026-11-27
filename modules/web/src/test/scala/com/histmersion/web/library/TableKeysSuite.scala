package com.histmersion
package web.library

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.personal.ItemKey
import com.histmersion.lint.{tolerated, LintRule}

class TableKeysSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val rows = List("a", "b", "c").map(ItemKey(_))

  test("↓ and ↑ move the choice by one row, held at the ends"):
    assertEquals(TableKeys.step(rows, Some(ItemKey("a")), 1), Some("b"))
    assertEquals(TableKeys.step(rows, Some(ItemKey("b")), -1), Some("a"))
    assertEquals(TableKeys.step(rows, Some(ItemKey("c")), 1), Some("c"))
    assertEquals(TableKeys.step(rows, Some(ItemKey("a")), -1), Some("a"))

  test("with nothing on this page chosen, ↓ takes the first row and ↑ the last"):
    assertEquals(TableKeys.step(rows, None, 1), Some("a"))
    assertEquals(TableKeys.step(rows, Some(ItemKey("elsewhere")), -1), Some("c"))
    assertEquals(TableKeys.step(Nil, None, 1), None)

  test("only the arrow keys move the choice"):
    assertEquals(TableKeys.delta("ArrowDown"), Some(1))
    assertEquals(TableKeys.delta("ArrowUp"), Some(-1))
    assertEquals(TableKeys.delta("Enter"), None)
    assertEquals(TableKeys.delta("j"), None)
