package com.histmersion
package web

class PageWidthSuite extends munit.FunSuite:
  test("a stored width reads back; anything else is the reading width"):
    assertEquals(PageWidth.fromWire(PageWidth.Wire("wide")), PageWidth.Wide)
    assertEquals(PageWidth.fromWire(PageWidth.Wire(" Wide ")), PageWidth.Wide)
    assertEquals(PageWidth.fromWire(PageWidth.Wire(null)), PageWidth.Reading)
    assertEquals(PageWidth.fromWire(PageWidth.Wire("huge")), PageWidth.Reading)
