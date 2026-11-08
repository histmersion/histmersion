package com.histmersion
package web.library

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.Offset
import com.histmersion.api.RowCount

class ListPageSuite extends munit.FunSuite:

  test("numbers group their thousands with commas, whatever the locale"):
    assertEquals(
      List(0, 7, 999, 1000, 1026, 12345, 1234567, -4500).map(ListPage.grouped),
      List("0", "7", "999", "1,000", "1,026", "12,345", "1,234,567", "-4,500")
    )

  test("the range line: loading, nothing, or which rows of how many"):
    assertEquals(ListPage.rangeLine(ListPage[Int](), "sources"), "Loading…")
    assertEquals(ListPage.rangeLine(ListPage[Int](loaded = true), "sources"), "No sources")
    assertEquals(
      ListPage.rangeLine(ListPage(List.fill(50)(0), Offset(0), RowCount(1026), loaded = true), "sources"),
      "1–50 of 1,026"
    )
    assertEquals(
      ListPage.rangeLine(ListPage(List.fill(26)(0), Offset(1000), RowCount(1026), loaded = true), "sources"),
      "1,001–1,026 of 1,026"
    )

  test("pages: previous and next exist only where there are rows, and turn by a page"):
    val first = ListPage(List.fill(50)(0), Offset(0), RowCount(120), loaded = true)
    assert(!first.hasPrevious && first.hasNext)
    assertEquals(first.nextOffset(50), 50)
    val last = ListPage(List.fill(20)(0), Offset(100), RowCount(120), loaded = true)
    assert(last.hasPrevious && !last.hasNext)
    assertEquals(last.previousOffset(50), 50)
    assertEquals(ListPage(List(1), Offset(10), RowCount(11)).previousOffset(50), 0)
