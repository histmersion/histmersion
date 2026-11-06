package com.histmersion.upstreams

import com.histmersion.plugin.{Doi, WorkKey, Year}
import com.histmersion.domain.library.acquisition.{WorkLookup, WorkRef}

/** What the acquisition chain is asked for one work. */
class WorkLookupSuite extends munit.FunSuite:

  test("the chain is asked for a work by its identifiers and its metadata record"):
    val ref = WorkRef(
      doi = Some(com.histmersion.plugin.Doi("10.1/x")),
      arxivId = Some(com.histmersion.upstreams.ArxivId("2101.00001")),
      title = Some(com.histmersion.plugin.WorkKey.Title("A title")),
      year = Some(com.histmersion.plugin.Year(1999)),
      authors = List(com.histmersion.plugin.WorkKey.Authors("A. Author"))
    )
    assertEquals(
      WorkLookup.key(Some(com.histmersion.plugin.Doi("10.1/x")), None, Some(ref)),
      WorkKey(
        doi = Some(Doi("10.1/x")),
        arxiv = Some(com.histmersion.plugin.WorkKey.Arxiv("2101.00001")),
        title = Some(com.histmersion.plugin.WorkKey.Title("A title")),
        authors = List(com.histmersion.plugin.WorkKey.Authors("A. Author")),
        year = Some(Year(1999))
      )
    )
    assertEquals(WorkLookup.key(None, Some(com.histmersion.upstreams.ArxivId("hep-th/9901001")), None), WorkKey(arxiv = Some(com.histmersion.plugin.WorkKey.Arxiv("hep-th/9901001"))))
    assertEquals(WorkLookup.key(Some(com.histmersion.plugin.Doi("not a doi")), Some(com.histmersion.upstreams.ArxivId("2101.00001")), None), WorkKey(arxiv = Some(com.histmersion.plugin.WorkKey.Arxiv("2101.00001"))))
