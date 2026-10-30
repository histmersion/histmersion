package com.histmersion.web.library

import com.histmersion.web.LibrarySection

class LibraryAddressSuite extends munit.FunSuite:

  test("the chosen item reads back from its query, encoded or not"):
    List("cline2014_year", "a b/c?d&e=f", "ζeus").foreach { k =>
      assertEquals(LibraryAddress.item(LibraryAddress.search(Some(k))), Some(k), k)
    }

  test("no item: an empty query, and an empty or missing ?item= reads as none"):
    assertEquals(LibraryAddress.search(None), "")
    assertEquals(LibraryAddress.search(Some("  ")), "")
    assertEquals(LibraryAddress.item(""), None)
    assertEquals(LibraryAddress.item("?item="), None)
    assertEquals(LibraryAddress.item("?other=x"), None)

  test("the item is found among other parameters, with or without the ?"):
    assertEquals(LibraryAddress.item("?a=1&item=drews1993_end&b=2"), Some("drews1993_end"))
    assertEquals(LibraryAddress.item("item=x"), Some("x"))

  test("a list's address with its chosen item"):
    assertEquals(LibraryAddress.href(LibrarySection.Sources, Some("cline2014_year")), "/library?item=cline2014_year")
    assertEquals(LibraryAddress.href(LibrarySection.Articles, Some("sea-peoples")), "/library/articles?item=sea-peoples")
    assertEquals(LibraryAddress.href(LibrarySection.Sources, None), "/library")
