package com.histmersion.web

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise}

/** [[Latest]] and [[TabState]] (#1798 item 5): an older answer never overwrites a newer query's, a
  * failed request ends "Loading…" with an error, and "show more" reaches the rows past the first page
  * by asking the server for the next one. No browser, no server: promises complete by hand. */
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
    def note(tag: String)(r: scala.util.Try[Int]): Unit = got += r.fold(e => s"$tag failed ${e.getMessage}", n => s"$tag ok $n")
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
    val s = TabState.first(rows, 412, 25)
    val l = TabState.loading(s)
    assert(l.loading && l.rows == rows && l.shown == 25)
    val f = TabState.failed(l, "HTTP 500")
    assert(!f.loading && f.error.contains("HTTP 500") && f.rows == rows)
    assertEquals(TabState.loading(f).error, None, "a new request clears the old error")

  test("TabState: show more reveals the loaded rows first, then asks for the next page past them"):
    val s = TabState.first(rows, 412, 25)
    assertEquals(s.moreLabel(25), Some("show 25 more (387 left)"))
    assertEquals(TabState.more(s, 25).map(_.shown), Right(50))
    val atEnd = s.copy(shown = 200)
    assertEquals(TabState.more(atEnd, 25), Left(200), "rows past the first fetch are fetched, no longer unreachable")
    val next = TabState.appended(TabState.loading(atEnd), (201 to 400).toList, 412, 25)
    assertEquals((next.rows.size, next.shown, next.loading), (400, 225, false))

  test("TabState: the last rows; nothing more once every row is shown"):
    val s = TabState.first((1 to 10).toList, 10, 25)
    assertEquals(s.shown, 10)
    assertEquals(s.moreLabel(25), None)
    val few = TabState.first((1 to 30).toList, 30, 25)
    assertEquals(few.moreLabel(25), Some("show 5 more"))
    assertEquals(TabState.more(few, 25).map(_.shown), Right(30))
