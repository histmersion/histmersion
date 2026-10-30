package com.histmersion.web.library

import com.histmersion.api.Sort

class LibrarySortSuite extends munit.FunSuite:

  test("each order the table offers asks the server for its own sort"):
    assertEquals(LibrarySort.values.toList.map(_.sort),
      List(Sort.Relevance, Sort.Year, Sort.Oldest, Sort.Title, Sort.PeriodStart))
    assertEquals(LibrarySort.values.toList.map(_.label),
      List("Relevance", "Newest publication", "Oldest publication", "Title", "Period start"))

  test("an address names an order by its wire word; none or an unknown one is relevance"):
    LibrarySort.values.foreach(s => assertEquals(LibrarySort.of(Some(s.wire)), s))
    assertEquals(LibrarySort.of(Some(" OLDEST ")), LibrarySort.Oldest)
    assertEquals(LibrarySort.of(None), LibrarySort.Relevance)
    assertEquals(LibrarySort.of(Some("cited")), LibrarySort.Relevance)
