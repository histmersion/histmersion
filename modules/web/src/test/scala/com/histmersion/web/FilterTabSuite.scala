package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise}

/** [[Latest]] and [[TabState]] (#1798 item 5): an older answer never overwrites a newer query's, a failed request ends
  * "Loading…" with an error, and "show more" reaches the rows past the first page by asking the server for the next
  * one. No browser, no server: promises complete by hand.
  */
class FilterTabSuite extends munit.FunSuite:

  // answers are applied the moment a promise completes
  private given ExecutionContext = ExecutionContext.parasitic

  test("Latest: only the newest request's answer is applied, whatever order they arrive in"):
    val latest = Latest()
    val (a, b) = (Promise[Int](), Promise[Int]())
    val got = mutable.ListBuffer.empty[String]
    latest(a.future)(n => got += s"ok $n", m => got += s"failed $m")
    latest(b.future)(n => got += s"ok $n", m => got += s"failed $m")
    b.success(2)
    a.success(1) // the older answer arrives last: dropped
    assertEquals(got.toList, List("ok 2"))

  test("Latest: a failure is answered with its message, never left hanging"):
    val latest = Latest()
    val p = Promise[Int]()
    val got = mutable.ListBuffer.empty[String]
    latest(p.future)(n => got += s"ok $n", m => got += s"failed $m")
    p.failure(RuntimeException("HTTP 503"))
    assertEquals(got.toList, List("failed HTTP 503"))

  test("Latest: tickets — a newer start supersedes every older one"):
    val latest = Latest()
    val one = latest.start(); val two = latest.start()
    assert(!latest.isCurrent(one) && latest.isCurrent(two))

  test("Latest.onCurrent: the raw outcome of the newest request only; cancel drops every one out (#1946)"):
    val latest = Latest()
    val (a, b, c) = (Promise[Int](), Promise[Int](), Promise[Int]())
    val got = mutable.ListBuffer.empty[String]
    def note(tag: String)(r: scala.util.Try[Int]): Unit =
      got += r.fold(e => s"$tag failed ${e.getMessage}", n => s"$tag ok $n")
    latest.onCurrent(a.future)(note("a"))
    latest.onCurrent(b.future)(note("b"))
    a.success(1) // superseded by b: dropped
    b.failure(RuntimeException("HTTP 500"))
    assertEquals(got.toList, List("b failed HTTP 500"))
    latest.onCurrent(c.future)(note("c"))
    latest.cancel() // the layer went off / the input unmounted
    c.success(3)
    assertEquals(got.size, 1, "an answer to a cancelled request is dropped")

  private val rows = (1 to 200).toList

  test("TabState: loading keeps the rows shown, a failure ends loading with the error"):
    val s = TabState.first(TabPage(rows, com.histmersion.api.RowCount(412)), com.histmersion.web.ResultsPaging.PageNumber(25))
    val l = TabState.loading(s)
    assert(l.loading.value && l.rows == rows && l.shown.value == 25)
    val f = TabState.failed(l, com.histmersion.web.TabState.Problem("HTTP 500"))
    assert(!f.loading.value && f.error.map(_.value).contains("HTTP 500") && f.rows == rows)
    assertEquals(TabState.loading(f).error, None, "a new request clears the old error")

  test("TabState: show more reveals the loaded rows first, then asks for the next page past them"):
    val s = TabState.first(TabPage(rows, com.histmersion.api.RowCount(412)), com.histmersion.web.ResultsPaging.PageNumber(25))
    assertEquals(s.moreLabel(com.histmersion.web.ResultsPaging.PageNumber(25)).map(_.value), Some("show 25 more (387 left)"))
    assertEquals(TabState.more(s, com.histmersion.web.ResultsPaging.PageNumber(25)).map((_.shown.value: Int)), Right(50))
    val atEnd = s.copy(shown = com.histmersion.api.RowCount(200))
    assertEquals(TabState.more(atEnd, com.histmersion.web.ResultsPaging.PageNumber(25)).left.map(_.value), Left(200), "rows past the first fetch are fetched, no longer unreachable")
    val next = TabState.appended(TabState.loading(atEnd), TabPage((201 to 400).toList, com.histmersion.api.RowCount(412)), com.histmersion.web.ResultsPaging.PageNumber(25))
    assertEquals((next.rows.size, next.shown.value: Int, next.loading.value), (400, 225, false))

  test("TabState: the last rows; nothing more once every row is shown"):
    val s = TabState.first(TabPage((1 to 10).toList, com.histmersion.api.RowCount(10)), com.histmersion.web.ResultsPaging.PageNumber(25))
    assertEquals(s.shown.value, 10)
    assertEquals(s.moreLabel(com.histmersion.web.ResultsPaging.PageNumber(25)), None)
    val few = TabState.first(TabPage((1 to 30).toList, com.histmersion.api.RowCount(30)), com.histmersion.web.ResultsPaging.PageNumber(25))
    assertEquals(few.moreLabel(com.histmersion.web.ResultsPaging.PageNumber(25)).map(_.value), Some("show 5 more (5 left)"))
    assertEquals(TabState.more(few, com.histmersion.web.ResultsPaging.PageNumber(25)).map((_.shown.value: Int)), Right(30))

  test("TabState: the count line — loading before the first answer, then the sidebar's one form with the extras"):
    assertEquals(TabState[Int]().countLine(com.histmersion.api.Plural.Noun.Singular("article"), com.histmersion.api.Plural.Noun.Plural("articles")).value, "loading…")
    val s = TabState.first(TabPage(rows, com.histmersion.api.RowCount(412), List(com.histmersion.web.TabPage.Extras("200 concepts"), com.histmersion.web.TabPage.Extras("? → ?"))), com.histmersion.web.ResultsPaging.PageNumber(25))
    assertEquals(s.countLine(com.histmersion.api.Plural.Noun.Singular("article"), com.histmersion.api.Plural.Noun.Plural("articles")).value, "showing 1–25 of 412 articles · 200 concepts · ? → ?")
    assertEquals(TabState.first(TabPage(List(1), com.histmersion.api.RowCount(1)), com.histmersion.web.ResultsPaging.PageNumber(25)).countLine(com.histmersion.api.Plural.Noun.Singular("article"), com.histmersion.api.Plural.Noun.Plural("articles")).value, "1 article")
    assertEquals(TabState.first(TabPage(Nil, com.histmersion.api.RowCount(0)), com.histmersion.web.ResultsPaging.PageNumber(25)).countLine(com.histmersion.api.Plural.Noun.Singular("event"), com.histmersion.api.Plural.Noun.Plural("events")).value, "no events")
