package com.histmersion.web

import com.histmersion.core.*

class PreviewSuite extends munit.FunSuite:

  private val detail = SourceDetail("cline2014_year", "1177 BC", List("Eric H. Cline"), Some(2014),
    Some("Princeton UP"), "current", List("bronze_age_collapse"), List("Eastern_Mediterranean"),
    List("sea_peoples"), Some(-1250), Some(-1100), Some("x" * 400), Some("10.1515/9781400849987"),
    None, None, None, None)

  test("a data-ck element previews a source, a data-slug element an article; a source wins"):
    assertEquals(Preview.targetOf(Some("a2020_b"), None), Some(Preview.Target.Source("a2020_b")))
    assertEquals(Preview.targetOf(None, Some("sea_peoples")), Some(Preview.Target.Article("sea_peoples")))
    assertEquals(Preview.targetOf(Some("a2020_b"), Some("x")), Some(Preview.Target.Source("a2020_b")))
    assertEquals(Preview.targetOf(Some(" "), None), None)

  test("the source card shows the DOI as identifier, readable places and a bounded summary"):
    val c = Preview.card(detail)
    assertEquals(c.identifier, "10.1515/9781400849987")
    assertEquals(c.byline, "Eric H. Cline · 2014 · Princeton UP")
    assertEquals(c.where, "Eastern Mediterranean · 1250 BCE–1100 BCE")
    assertEquals(c.status, Some(SourceStatus.Current))
    assert(c.summary.exists(s => s.length == 300 && s.endsWith("…")), c.summary)
    assertEquals(Preview.card(detail.copy(doi = None)).identifier, "cline2014_year")

  test("InPage reads only same-page fragments, and match stepping wraps"):
    assertEquals(InPage.fragmentOf("#cite-3"), Some("cite-3"))
    assertEquals(InPage.fragmentOf("/source/x#y"), None)
    assertEquals(InPage.fragmentOf("#"), None)
    assertEquals(InPage.nextIndex(-1, 3), 0)
    assertEquals(InPage.nextIndex(2, 3), 0)
    assertEquals(InPage.nextIndex(0, 0), -1)
