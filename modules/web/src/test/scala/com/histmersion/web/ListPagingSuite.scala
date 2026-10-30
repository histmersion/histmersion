package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.web.ListPaging.Listing

/** The paged list windows (jobs, runs, approvals): the heading's wording, when "Show older" is there, what a poll asks
  * for and how its answer, or an older page, lands.
  */
class ListPagingSuite extends munit.FunSuite:

  private def job(id: String, state: JobState = JobState.Done) = JobInfo(id, Ingest.KindIngest, state.wire)
  private def page(total: Int, next: Option[String], counts: (String, Int)*) = Paging.Page(total, next, counts.toList)

  test("the heading: every row shown, or the latest N of the total, with the filter's word"):
    val all = ListPaging.first(List(job("a"), job("b")), page(2, None, "done" -> 2))
    assertEquals(ListPaging.heading(all, "job", None, _.status), "2 jobs")
    val part = ListPaging.first((1 to 100).map(i => job(s"j$i")).toList, page(2431, Some("j100"), "done" -> 2431))
    assertEquals(ListPaging.heading(part, "job", None, _.status), "Latest 100 of 2,431 jobs")
    val failed = ListPaging.first(
      (1 to 100).map(i => job(s"j$i", JobState.Failed)).toList,
      page(412, Some("j100"), "failed" -> 412, "done" -> 2019)
    )
    assertEquals(
      ListPaging.heading(failed, "job", Some("failed"), _.status, List("Failed")),
      "Latest 100 of 412 jobs · Failed"
    )
    // the filter just changed: the rows loaded under "all" that it shows, of that state's count
    val switched =
      ListPaging.first(List(job("x", JobState.Failed)), page(2431, Some("j100"), "failed" -> 412, "done" -> 2019))
    assertEquals(
      ListPaging.heading(switched, "job", Some("failed"), _.status, List("Failed", "Fetch pictures")),
      "Latest 1 of 412 jobs · Failed, Fetch pictures"
    )
    // no counts from the server: the rows count themselves
    assertEquals(
      ListPaging.heading(ListPaging.first(List(job("a")), Paging.Page.empty), "approval", Some("done"), _.status),
      "1 approval"
    )

  test("Show older is there only while the server names an older page"):
    assert(ListPaging.first(List(job("a")), page(2, Some("a"))).hasOlder)
    assert(!ListPaging.first(List(job("a")), page(1, None)).hasOlder)
    assert(!Listing.empty[JobInfo].hasOlder)

  test("an older page is appended, a row already shown left out, and the listing is extended"):
    val cur = ListPaging.first(List(job("c"), job("b")), page(5, Some("b")))
    val more = ListPaging.appended(cur, List(job("b"), job("a")), page(5, None), _.id)
    assertEquals(more.rows.map(_.id), List("c", "b", "a"))
    assert(more.extended && !more.hasOlder)
    assertEquals(ListPaging.heading(more, "job", None, _.status), "3 jobs") // no counts: the rows count

  test("a poll asks for the newest page, or down to the last row shown once older pages are loaded"):
    val cur = ListPaging.first(List(job("c"), job("b")), page(5, Some("b")))
    assertEquals(ListPaging.pollUpTo(cur, _.id), None)
    val ext = ListPaging.appended(cur, List(job("a")), page(5, Some("a")), _.id)
    assertEquals(ListPaging.pollUpTo(ext, _.id), Some("a"))
    // its answer replaces the rows: a new one on top, every loaded one kept, still extended
    val polled = ListPaging.polled(ext, List(job("d"), job("c"), job("b"), job("a")), page(6, Some("a")))
    assertEquals(polled.rows.map(_.id), List("d", "c", "b", "a"))
    assert(polled.extended)
    assertEquals(ListPaging.pollUpTo(polled, _.id), Some("a"))

  test("a filter button counts the whole list from the server, the rows only when it sent nothing"):
    val l =
      ListPaging.first(List(job("a"), job("b", JobState.Failed)), page(2431, Some("b"), "done" -> 2400, "failed" -> 31))
    assertEquals(ListPaging.count(l, None, _.status), 2431)
    assertEquals(ListPaging.count(l, Some("failed"), _.status), 31)
    assertEquals(ListPaging.count(l, Some("running"), _.status), 0)
    val old = l.copy(page = Paging.Page.empty)
    assertEquals(ListPaging.count(old, None, _.status), 2)
    assertEquals(ListPaging.count(old, Some("failed"), _.status), 1)

  test("the jobs and runs windows poll while anything in the table is live, not just in the rows shown"):
    val done = List(job("a"))
    assert(JobsView.live(done, page(3, None, "done" -> 2, "running" -> 1)))
    assert(!JobsView.live(done, page(2, None, "done" -> 2)))
    assert(JobsView.live(List(job("q", JobState.Queued)), Paging.Page.empty)) // no counts: the rows say
    val run = Agents.RunInfo("r1", "enrich-source", "a2020_b", "done")
    assert(RunsView.anyLive(List(run), page(2, None, "done" -> 1, "queued" -> 1)))
    assert(!RunsView.anyLive(List(run), page(1, None, "done" -> 1)))
    assertEquals(ApprovalsView.qualifier(com.histmersion.api.ListFilters.ApprovalFilter()), Some("open"))
    assertEquals(ApprovalsView.qualifier(com.histmersion.api.ListFilters.ApprovalFilter(state = Some("all"))), None)
