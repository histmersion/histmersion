package com.histmersion.web

class PageWidthSuite extends munit.FunSuite:
  test("a stored width reads back; anything else is the reading width"):
    assertEquals(PageWidth.fromWire("wide"), PageWidth.Wide)
    assertEquals(PageWidth.fromWire(" Wide "), PageWidth.Wide)
    assertEquals(PageWidth.fromWire(null), PageWidth.Reading)
    assertEquals(PageWidth.fromWire("huge"), PageWidth.Reading)
