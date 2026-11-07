package com.histmersion.web

class PageWidthSuite extends munit.FunSuite:
  test("a stored width reads back; anything else is the reading width"):
    assertEquals(PageWidth.fromWire(com.histmersion.web.PageWidth.Wire("wide")), PageWidth.Wide)
    assertEquals(PageWidth.fromWire(com.histmersion.web.PageWidth.Wire(" Wide ")), PageWidth.Wide)
    assertEquals(PageWidth.fromWire(null), PageWidth.Reading)
    assertEquals(PageWidth.fromWire(com.histmersion.web.PageWidth.Wire("huge")), PageWidth.Reading)
