package com.histmersion
package web.library

import com.histmersion.api.library.{Hit, SearchHit}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName

class TableRowSuite extends munit.FunSuite:

  private def hit(authors: List[String]) =
    Hit(
      SearchHit(
        Citekey("cline2014_year"),
        Title("1177 B.C."),
        (Some(2014)).map(Year.apply),
        Source.Status.Current,
        Nil,
        List(Source.Regions("levant")),
        Some(Year(-1250)),
        Some(Year(-1100)),
        None,
        authors = (authors).map(AuthorName.apply)
      ),
      Nil,
      Nil
    )

  test("a source's row: title, authors, year, status, period, its page"):
    val r = TableRow.of(hit(List("Eric H. Cline")))
    assertEquals(
      r,
      TableRow(
        TableRow.Key("cline2014_year"),
        "1177 B.C.",
        "Eric H. Cline",
        Some(Year(2014)),
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
      Article.Summary(
        Article.Slug("sea-peoples"),
        Article.Title("The Sea Peoples"),
        Article.Kind.Wire("concept"),
        None,
        Nil,
        List(Article.Regions("eastern_mediterranean")),
        None,
        None
      )
    )
    assertEquals(
      r,
      TableRow(
        TableRow.Key("sea-peoples"),
        "The Sea Peoples",
        "eastern mediterranean",
        None,
        None,
        "Concept",
        "",
        "/article/sea-peoples"
      )
    )
