package com.histmersion.web

import com.histmersion.api.library.TimeHistogram
import com.histmersion.api.research.Article

/** The sidebar tabs' one pagination form: the count line and the "show more" label every tab uses, and what each tab
  * adds to the line.
  */
class ResultsPagingSuite extends munit.FunSuite:

  test("the count line: showing a part, all, or none; the extras after it"):
    assertEquals(
      ResultsPaging.countLine(20, 285, "source", extras = List("491 findings", "1475 BCE → 1465 BCE")),
      "showing 1–20 of 285 sources · 491 findings · 1475 BCE → 1465 BCE"
    )
    assertEquals(ResultsPaging.countLine(3, 3, "source"), "3 sources")
    assertEquals(ResultsPaging.countLine(1, 1, "source"), "1 source")
    assertEquals(ResultsPaging.countLine(0, 0, "event", extras = List("", "x")), "no events · x")
    assertEquals(ResultsPaging.countLine(2, 9, "index", "indexes"), "showing 1–2 of 9 indexes")

  test("the more label: the next page and what is left, always both; nothing when nothing is left"):
    assertEquals(ResultsPaging.moreLabel(265, 20), Some("show 20 more (265 left)"))
    assertEquals(ResultsPaging.moreLabel(5, 20), Some("show 5 more (5 left)"))
    assertEquals(ResultsPaging.moreLabel(0, 20), None)

  test("articles: the kind counts, the largest first"):
    assertEquals(
      ArticlesTab.kindCounts(
        List(
          Article.KindCount("myth", 3),
          Article.KindCount("concept", 200),
          Article.KindCount("index", 2),
          Article.KindCount("report", 0)
        )
      ),
      List("200 concepts", "3 myths", "2 indexes")
    )

  test("by time / by region: every source, the groups, the window"):
    val time = TimeHistogram(from = -1475, to = -1465, bucket = 10, buckets = Nil, by = "time", total = 285)
    assertEquals(TreeTab.countLine(time, 12, -1475, -1465), "285 sources · 12 periods · 1475 BCE → 1465 BCE")
    assertEquals(
      TreeTab.countLine(time.copy(by = "region", asOf = Some(-1470)), 1, -1475, -1465),
      "285 sources · 1 region · 1475 BCE → 1465 BCE · as of 1470 BCE"
    )
