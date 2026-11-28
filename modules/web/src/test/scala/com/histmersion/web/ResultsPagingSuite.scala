package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.TimeHistogram
import com.histmersion.api.research.Article
import com.histmersion.api.Paging
import com.histmersion.api.Plural
import com.histmersion.api.UiLabels
import com.histmersion.api.RowCount
import com.histmersion.api.catalogue.Year

/** The sidebar tabs' one pagination form: the count line and the "show more" label every tab uses, and what each tab
  * adds to the line.
  */
class ResultsPagingSuite extends munit.FunSuite:

  test("the count line: showing a part, all, or none; the extras after it"):
    assertEquals(
      ResultsPaging.countLine(
        Paging.Shown(20),
        Paging.Page.Total(285),
        Plural.Noun.Singular("source"),
        extras = List(UiLabels.Text("491 findings"), UiLabels.Text("1475 BCE → 1465 BCE"))
      ),
      "showing 1–20 of 285 sources · 491 findings · 1475 BCE → 1465 BCE"
    )
    assertEquals(
      ResultsPaging.countLine(Paging.Shown(3), Paging.Page.Total(3), Plural.Noun.Singular("source")),
      "3 sources"
    )
    assertEquals(
      ResultsPaging.countLine(Paging.Shown(1), Paging.Page.Total(1), Plural.Noun.Singular("source")),
      "1 source"
    )
    assertEquals(
      ResultsPaging.countLine(
        Paging.Shown(0),
        Paging.Page.Total(0),
        Plural.Noun.Singular("event"),
        extras = List(UiLabels.Text(""), UiLabels.Text("x"))
      ),
      "no events · x"
    )
    assertEquals(
      ResultsPaging
        .countLine(Paging.Shown(2), Paging.Page.Total(9), Plural.Noun.Singular("index"), Plural.Noun.Plural("indexes")),
      "showing 1–2 of 9 indexes"
    )

  test("the more label: the next page and what is left, always both; nothing when nothing is left"):
    assertEquals(ResultsPaging.moreLabel(RowCount(265), ResultsPaging.PageNumber(20)), Some("show 20 more (265 left)"))
    assertEquals(ResultsPaging.moreLabel(RowCount(5), ResultsPaging.PageNumber(20)), Some("show 5 more (5 left)"))
    assertEquals(ResultsPaging.moreLabel(RowCount(0), ResultsPaging.PageNumber(20)), None)

  test("articles: the kind counts, the largest first"):
    assertEquals(
      ArticlesTab.kindCounts(
        List(
          Article.KindCount(Article.Kind.Wire("myth"), Article.KindCount.Count(3)),
          Article.KindCount(Article.Kind.Wire("concept"), Article.KindCount.Count(200)),
          Article.KindCount(Article.Kind.Wire("index"), Article.KindCount.Count(2)),
          Article.KindCount(Article.Kind.Wire("report"), Article.KindCount.Count(0))
        )
      ),
      List("200 concepts", "3 myths", "2 indexes")
    )

  test("by time / by region: every source, the groups, the window"):
    val time = TimeHistogram(
      from = TimeHistogram.From(-1475),
      to = TimeHistogram.To(-1465),
      bucket = TimeHistogram.Bucket(10),
      buckets = Nil,
      by = TimeHistogram.By("time"),
      total = TimeHistogram.Total(285)
    )
    assertEquals(
      TreeTab.countLine(time, RowCount(12), Year.Astronomical(-1475), Year.Astronomical(-1465)),
      "285 sources · 12 periods · 1475 BCE → 1465 BCE"
    )
    assertEquals(
      TreeTab.countLine(
        time.modify(_.by).setTo(TimeHistogram.By("region")).modify(_.asOf).setTo(Some(Year(-1470))),
        RowCount(1),
        Year.Astronomical(-1475),
        Year.Astronomical(-1465)
      ),
      "285 sources · 1 region · 1475 BCE → 1465 BCE · as of 1470 BCE"
    )
