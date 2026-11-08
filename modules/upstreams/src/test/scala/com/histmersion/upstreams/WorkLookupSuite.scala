package com.histmersion
package upstreams

import com.histmersion.plugin.{Doi, WorkKey, Year}
import com.histmersion.domain.library.acquisition.{WorkLookup, WorkRef}

/** What the acquisition chain is asked for one work. */
class WorkLookupSuite extends munit.FunSuite:

  test("the chain is asked for a work by its identifiers and its metadata record"):
    val ref = WorkRef(
      doi = Some(Doi("10.1/x")),
      arxivId = Some(ArxivId("2101.00001")),
      title = Some(WorkKey.Title("A title")),
      year = Some(Year(1999)),
      authors = List(WorkKey.Authors("A. Author"))
    )
    assertEquals(
      WorkLookup.key(Some(Doi("10.1/x")), None, Some(ref)),
      WorkKey(
        doi = Some(Doi("10.1/x")),
        arxiv = Some(WorkKey.Arxiv("2101.00001")),
        title = Some(WorkKey.Title("A title")),
        authors = List(WorkKey.Authors("A. Author")),
        year = Some(Year(1999))
      )
    )
    assertEquals(WorkLookup.key(None, Some(ArxivId("hep-th/9901001")), None), WorkKey(arxiv = Some(WorkKey.Arxiv("hep-th/9901001"))))
    assertEquals(WorkLookup.key(Some(Doi("not a doi")), Some(ArxivId("2101.00001")), None), WorkKey(arxiv = Some(WorkKey.Arxiv("2101.00001"))))
