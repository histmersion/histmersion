package com.histmersion.web.library

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.web.LibrarySection

class LibraryAddressSuite extends munit.FunSuite:

  test("the chosen item reads back from its query, encoded or not"):
    List("cline2014_year", "a b/c?d&e=f", "ζeus").foreach { k =>
      assertEquals(LibraryAddress.item(LibraryAddress.search(Some(k))), Some(k), k)
    }

  test("no item: an empty query, and an empty or missing ?item= reads as none"):
    assertEquals(LibraryAddress.search(None), "")
    assertEquals(LibraryAddress.search(Some(com.histmersion.api.personal.ItemKey("  "))), "")
    assertEquals(LibraryAddress.item(com.histmersion.web.Pages.UrlQuery("")), None)
    assertEquals(LibraryAddress.item(com.histmersion.web.Pages.UrlQuery("?item=")), None)
    assertEquals(LibraryAddress.item(com.histmersion.web.Pages.UrlQuery("?other=x")), None)

  test("the item is found among other parameters, with or without the ?"):
    assertEquals(LibraryAddress.item(com.histmersion.web.Pages.UrlQuery("?a=1&item=drews1993_end&b=2")), Some("drews1993_end"))
    assertEquals(LibraryAddress.item(com.histmersion.web.Pages.UrlQuery("item=x")), Some("x"))

  test("a list's address with its chosen item"):
    assertEquals(LibraryAddress.href(LibrarySection.Sources, Some(com.histmersion.api.personal.ItemKey("cline2014_year"))), "/library?item=cline2014_year")
    assertEquals(
      LibraryAddress.href(LibrarySection.Articles, Some(com.histmersion.api.personal.ItemKey("sea-peoples"))),
      "/library/articles?item=sea-peoples"
    )
    assertEquals(LibraryAddress.href(LibrarySection.Sources, None), "/library")
