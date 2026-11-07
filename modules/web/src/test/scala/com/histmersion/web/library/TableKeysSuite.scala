package com.histmersion.web.library

class TableKeysSuite extends munit.FunSuite:

  private val rows = List("a", "b", "c")

  test("↓ and ↑ move the choice by one row, held at the ends"):
    assertEquals(TableKeys.step(rows, Some(com.histmersion.api.personal.ItemKey("a")), 1), Some("b"))
    assertEquals(TableKeys.step(rows, Some(com.histmersion.api.personal.ItemKey("b")), -1), Some("a"))
    assertEquals(TableKeys.step(rows, Some(com.histmersion.api.personal.ItemKey("c")), 1), Some("c"))
    assertEquals(TableKeys.step(rows, Some(com.histmersion.api.personal.ItemKey("a")), -1), Some("a"))

  test("with nothing on this page chosen, ↓ takes the first row and ↑ the last"):
    assertEquals(TableKeys.step(rows, None, 1), Some("a"))
    assertEquals(TableKeys.step(rows, Some(com.histmersion.api.personal.ItemKey("elsewhere")), -1), Some("c"))
    assertEquals(TableKeys.step(Nil, None, 1), None)

  test("only the arrow keys move the choice"):
    assertEquals(TableKeys.delta("ArrowDown"), Some(1))
    assertEquals(TableKeys.delta("ArrowUp"), Some(-1))
    assertEquals(TableKeys.delta("Enter"), None)
    assertEquals(TableKeys.delta("j"), None)
