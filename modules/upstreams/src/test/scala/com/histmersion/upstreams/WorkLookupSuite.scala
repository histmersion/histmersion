package com.histmersion.upstreams

import com.histmersion.plugin.{Doi, WorkKey, Year}
import com.histmersion.domain.library.acquisition.{WorkLookup, WorkRef}

/** What the acquisition chain is asked for one work. */
class WorkLookupSuite extends munit.FunSuite:

  test("the chain is asked for a work by its identifiers and its metadata record"):
    val ref = WorkRef(
      doi = Some("10.1/x"),
      arxivId = Some("2101.00001"),
      title = Some("A title"),
      year = Some(1999),
      authors = List("A. Author")
    )
    assertEquals(
      WorkLookup.key(Some("10.1/x"), None, Some(ref)),
      WorkKey(
        doi = Some(Doi("10.1/x")),
        arxiv = Some("2101.00001"),
        title = Some("A title"),
        authors = List("A. Author"),
        year = Some(Year(1999))
      )
    )
    assertEquals(WorkLookup.key(None, Some("hep-th/9901001"), None), WorkKey(arxiv = Some("hep-th/9901001")))
    assertEquals(WorkLookup.key(Some("not a doi"), Some("2101.00001"), None), WorkKey(arxiv = Some("2101.00001")))
