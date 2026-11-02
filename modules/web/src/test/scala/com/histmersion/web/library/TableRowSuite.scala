package com.histmersion.web.library

import com.histmersion.api.research.ArticleSummary
import com.histmersion.api.library.{Hit, SearchHit}
import com.histmersion.domain.library.Source

class TableRowSuite extends munit.FunSuite:

  private def hit(authors: List[String]) =
    Hit(
      SearchHit(
        "cline2014_year",
        "1177 B.C.",
        Some(2014),
        Source.Status.Current,
        Nil,
        List("levant"),
        Some(-1250),
        Some(-1100),
        None,
        authors = authors
      ),
      Nil,
      Nil
    )

  test("a source's row: title, authors, year, status, period, its page"):
    val r = TableRow.of(hit(List("Eric H. Cline")))
    assertEquals(
      r,
      TableRow(
        "cline2014_year",
        "1177 B.C.",
        "Eric H. Cline",
        Some(2014),
        Some(Source.Status.Current),
        "",
        "1250 BCE–1100 BCE",
        "/source/cline2014_year"
      )
    )

  test("more than three authors: the first three, et al."):
    assertEquals(TableRow.of(hit(List("A", "B", "C"))).byline, "A, B, C")
    assertEquals(TableRow.of(hit(List("A", "B", "C", "D"))).byline, "A, B, C et al.")
    assertEquals(TableRow.of(hit(Nil)).byline, "")

  test("an article's row: its kind in words, its regions under the title, no status"):
    val r = TableRow.of(
      ArticleSummary("sea-peoples", "The Sea Peoples", "concept", None, Nil, List("eastern_mediterranean"), None, None)
    )
    assertEquals(
      r,
      TableRow(
        "sea-peoples",
        "The Sea Peoples",
        "eastern mediterranean",
        None,
        None,
        "Concept",
        "",
        "/article/sea-peoples"
      )
    )
