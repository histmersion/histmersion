package com.histmersion
package web.library

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.Sort

class LibrarySortSuite extends munit.FunSuite:

  test("each order the table offers asks the server for its own sort"):
    assertEquals(
      LibrarySort.values.toList.map(_.sort),
      List(Sort.Relevance, Sort.Year, Sort.Oldest, Sort.Title, Sort.PeriodStart)
    )
    assertEquals(
      LibrarySort.values.toList.map(_.label),
      List("Relevance", "Newest publication", "Oldest publication", "Title", "Period start")
    )

  test("an address names an order by its wire word; none or an unknown one is relevance"):
    LibrarySort.values.foreach(s => assertEquals(LibrarySort.of(Some(s.wire)), s))
    assertEquals(LibrarySort.of(Some(LibrarySort.Wire(" OLDEST "))), LibrarySort.Oldest)
    assertEquals(LibrarySort.of(None), LibrarySort.Relevance)
    assertEquals(LibrarySort.of(Some(LibrarySort.Wire("cited"))), LibrarySort.Relevance)
