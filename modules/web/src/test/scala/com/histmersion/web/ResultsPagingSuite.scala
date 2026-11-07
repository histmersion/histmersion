package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.TimeHistogram
import com.histmersion.api.research.Article

/** The sidebar tabs' one pagination form: the count line and the "show more" label every tab uses, and what each tab
  * adds to the line.
  */
class ResultsPagingSuite extends munit.FunSuite:

  test("the count line: showing a part, all, or none; the extras after it"):
    assertEquals(
      ResultsPaging.countLine(com.histmersion.api.Paging.Shown(20), com.histmersion.api.Paging.Page.Total(285), com.histmersion.api.Plural.Noun.Singular("source"), extras = List(com.histmersion.api.UiLabels.Text("491 findings"), com.histmersion.api.UiLabels.Text("1475 BCE → 1465 BCE"))),
      "showing 1–20 of 285 sources · 491 findings · 1475 BCE → 1465 BCE"
    )
    assertEquals(ResultsPaging.countLine(com.histmersion.api.Paging.Shown(3), com.histmersion.api.Paging.Page.Total(3), com.histmersion.api.Plural.Noun.Singular("source")), "3 sources")
    assertEquals(ResultsPaging.countLine(com.histmersion.api.Paging.Shown(1), com.histmersion.api.Paging.Page.Total(1), com.histmersion.api.Plural.Noun.Singular("source")), "1 source")
    assertEquals(ResultsPaging.countLine(com.histmersion.api.Paging.Shown(0), com.histmersion.api.Paging.Page.Total(0), com.histmersion.api.Plural.Noun.Singular("event"), extras = List(com.histmersion.api.UiLabels.Text(""), com.histmersion.api.UiLabels.Text("x"))), "no events · x")
    assertEquals(ResultsPaging.countLine(com.histmersion.api.Paging.Shown(2), com.histmersion.api.Paging.Page.Total(9), com.histmersion.api.Plural.Noun.Singular("index"), com.histmersion.api.Plural.Noun.Plural("indexes")), "showing 1–2 of 9 indexes")

  test("the more label: the next page and what is left, always both; nothing when nothing is left"):
    assertEquals(ResultsPaging.moreLabel(com.histmersion.api.RowCount(265), com.histmersion.web.ResultsPaging.PageNumber(20)), Some("show 20 more (265 left)"))
    assertEquals(ResultsPaging.moreLabel(com.histmersion.api.RowCount(5), com.histmersion.web.ResultsPaging.PageNumber(20)), Some("show 5 more (5 left)"))
    assertEquals(ResultsPaging.moreLabel(com.histmersion.api.RowCount(0), com.histmersion.web.ResultsPaging.PageNumber(20)), None)

  test("articles: the kind counts, the largest first"):
    assertEquals(
      ArticlesTab.kindCounts(
        List(
          Article.KindCount(com.histmersion.api.research.Article.Kind.Wire("myth"), com.histmersion.api.research.Article.KindCount.Count(3)),
          Article.KindCount(com.histmersion.api.research.Article.Kind.Wire("concept"), com.histmersion.api.research.Article.KindCount.Count(200)),
          Article.KindCount(com.histmersion.api.research.Article.Kind.Wire("index"), com.histmersion.api.research.Article.KindCount.Count(2)),
          Article.KindCount(com.histmersion.api.research.Article.Kind.Wire("report"), com.histmersion.api.research.Article.KindCount.Count(0))
        )
      ),
      List("200 concepts", "3 myths", "2 indexes")
    )

  test("by time / by region: every source, the groups, the window"):
    val time = TimeHistogram(from = com.histmersion.api.library.TimeHistogram.From(-1475), to = com.histmersion.api.library.TimeHistogram.To(-1465), bucket = com.histmersion.api.library.TimeHistogram.Bucket(10), buckets = Nil, by = com.histmersion.api.library.TimeHistogram.By("time"), total = com.histmersion.api.library.TimeHistogram.Total(285))
    assertEquals(TreeTab.countLine(time, com.histmersion.api.RowCount(12), com.histmersion.api.Years.Astronomical(-1475), com.histmersion.api.Years.Astronomical(-1465)), "285 sources · 12 periods · 1475 BCE → 1465 BCE")
    assertEquals(
      TreeTab.countLine(time.modify(_.by).setTo("region").modify(_.asOf).setTo((Some(-1470): _root_.scala.Option[_root_.scala.Long])), com.histmersion.api.RowCount(1), com.histmersion.api.Years.Astronomical(-1475), com.histmersion.api.Years.Astronomical(-1465)),
      "285 sources · 1 region · 1475 BCE → 1465 BCE · as of 1470 BCE"
    )
