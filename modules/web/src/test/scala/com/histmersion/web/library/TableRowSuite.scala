package com.histmersion.web.library

import com.histmersion.api.library.{Hit, SearchHit}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

class TableRowSuite extends munit.FunSuite:

  private def hit(authors: List[String]) =
    Hit(
      SearchHit(
        com.histmersion.api.library.Citekey("cline2014_year"),
        com.histmersion.api.library.Title("1177 B.C."),
        (Some(2014)).map(com.histmersion.api.catalogue.Year.apply),
        Source.Status.Current,
        Nil,
        List("levant"),
        Some(-1250),
        Some(-1100),
        None,
        authors = (authors).map(com.histmersion.api.library.AuthorName.apply)
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
      Article.Summary("sea-peoples", "The Sea Peoples", "concept", None, Nil, List("eastern_mediterranean"), None, None)
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
