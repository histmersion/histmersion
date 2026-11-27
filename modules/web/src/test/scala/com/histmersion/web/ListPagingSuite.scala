package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.Paging
import com.histmersion.web.ListPaging.Listing
import com.histmersion.api.automation.Agents
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.library.Ingest
import com.histmersion.api.SpatialHierarchyResult.Job
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.Plural
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.automation.Approval
import com.histmersion.lint.{tolerated, LintRule}

/** The paged list windows (jobs, runs, approvals): the heading's wording, when "Show older" is there, what a poll asks
  * for and how its answer, or an older page, lands.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ListPagingSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def job(id: Job.Id, state: JobState = JobState.Done) =
    JobInfo(id, JobKinds.Wire(Ingest.KindIngest), state.wire)
  private def page(
      total: Paging.Page.Total,
      next: Option[Paging.Page.Next],
      counts: (Paging.Page.CountState, Paging.Count)*
  ) = Paging.Page(total, next, counts.toList)

  test("the heading: every row shown, or the latest N of the total, with the filter's word"):
    val all = ListPaging.first(
      List(job(Job.Id("a")), job(Job.Id("b"))),
      page(Paging.Page.Total(2), None, Paging.Page.CountState("done") -> Paging.Count(2))
    )
    assertEquals(ListPaging.heading(all, Plural.Noun.Singular("job"), None, _.status.value), "2 jobs")
    val part = ListPaging.first(
      (1 to 100).map(i => job(Job.Id(s"j$i"))).toList,
      page(
        Paging.Page.Total(2431),
        Some(Paging.Page.Next("j100")),
        Paging.Page.CountState("done") -> Paging.Count(2431)
      )
    )
    assertEquals(
      ListPaging.heading(part, Plural.Noun.Singular("job"), None, _.status.value),
      "Latest 100 of 2,431 jobs"
    )
    val failed = ListPaging.first(
      (1 to 100).map(i => job(Job.Id(s"j$i"), JobState.Failed)).toList,
      page(
        Paging.Page.Total(412),
        Some(Paging.Page.Next("j100")),
        Paging.Page.CountState("failed") -> Paging.Count(412),
        Paging.Page.CountState("done") -> Paging.Count(2019)
      )
    )
    assertEquals(
      ListPaging.heading(failed, Plural.Noun.Singular("job"), Some("failed"), _.status.value, List("Failed")),
      "Latest 100 of 412 jobs · Failed"
    )
    // the filter just changed: the rows loaded under "all" that it shows, of that state's count
    val switched =
      ListPaging.first(
        List(job(Job.Id("x"), JobState.Failed)),
        page(
          Paging.Page.Total(2431),
          Some(Paging.Page.Next("j100")),
          Paging.Page.CountState("failed") -> Paging.Count(412),
          Paging.Page.CountState("done") -> Paging.Count(2019)
        )
      )
    assertEquals(
      ListPaging.heading(
        switched,
        Plural.Noun.Singular("job"),
        Some("failed"),
        _.status.value,
        List("Failed", "Fetch pictures")
      ),
      "Latest 1 of 412 jobs · Failed, Fetch pictures"
    )
    // no counts from the server: the rows count themselves
    assertEquals(
      ListPaging.heading(
        ListPaging.first(List(job(Job.Id("a"))), Paging.Page.empty),
        Plural.Noun.Singular("approval"),
        Some("done"),
        _.status.value
      ),
      "1 approval"
    )

  test("Show older is there only while the server names an older page"):
    assert(ListPaging.first(List(job(Job.Id("a"))), page(Paging.Page.Total(2), Some(Paging.Page.Next("a")))).hasOlder)
    assert(!ListPaging.first(List(job(Job.Id("a"))), page(Paging.Page.Total(1), None)).hasOlder)
    assert(!Listing.empty[JobInfo].hasOlder)

  test("an older page is appended, a row already shown left out, and the listing is extended"):
    val cur = ListPaging.first(
      List(job(Job.Id("c")), job(Job.Id("b"))),
      page(Paging.Page.Total(5), Some(Paging.Page.Next("b")))
    )
    val more =
      ListPaging.appended(cur, List(job(Job.Id("b")), job(Job.Id("a"))), page(Paging.Page.Total(5), None), _.id.value)
    assertEquals(more.rows.map(_.id), List("c", "b", "a").map(Job.Id.apply))
    assert(more.extended && !more.hasOlder)
    assertEquals(
      ListPaging.heading(more, Plural.Noun.Singular("job"), None, _.status.value),
      "3 jobs"
    ) // no counts: the rows count

  test("a poll asks for the newest page, or down to the last row shown once older pages are loaded"):
    val cur = ListPaging.first(
      List(job(Job.Id("c")), job(Job.Id("b"))),
      page(Paging.Page.Total(5), Some(Paging.Page.Next("b")))
    )
    assertEquals(ListPaging.pollUpTo(cur, _.id.value), None)
    val ext = ListPaging.appended(
      cur,
      List(job(Job.Id("a"))),
      page(Paging.Page.Total(5), Some(Paging.Page.Next("a"))),
      _.id.value
    )
    assertEquals(ListPaging.pollUpTo(ext, _.id.value), Some("a"))
    // its answer replaces the rows: a new one on top, every loaded one kept, still extended
    val polled = ListPaging.polled(
      ext,
      List(job(Job.Id("d")), job(Job.Id("c")), job(Job.Id("b")), job(Job.Id("a"))),
      page(Paging.Page.Total(6), Some(Paging.Page.Next("a")))
    )
    assertEquals(polled.rows.map(_.id), List("d", "c", "b", "a").map(Job.Id.apply))
    assert(polled.extended)
    assertEquals(ListPaging.pollUpTo(polled, _.id.value), Some("a"))

  test("a filter button counts the whole list from the server, the rows only when it sent nothing"):
    val l =
      ListPaging.first(
        List(job(Job.Id("a")), job(Job.Id("b"), JobState.Failed)),
        page(
          Paging.Page.Total(2431),
          Some(Paging.Page.Next("b")),
          Paging.Page.CountState("done") -> Paging.Count(2400),
          Paging.Page.CountState("failed") -> Paging.Count(31)
        )
      )
    assertEquals(ListPaging.count(l, None, _.status.value), 2431)
    assertEquals(ListPaging.count(l, Some("failed"), _.status.value), 31)
    assertEquals(ListPaging.count(l, Some("running"), _.status.value), 0)
    val old = l.copy(page = Paging.Page.empty)
    assertEquals(ListPaging.count(old, None, _.status.value), 2)
    assertEquals(ListPaging.count(old, Some("failed"), _.status.value), 1)

  test("the jobs and runs windows poll while anything in the table is live, not just in the rows shown"):
    val done = List(job(Job.Id("a")))
    assert(
      JobsView.live(
        done,
        page(
          Paging.Page.Total(3),
          None,
          Paging.Page.CountState("done") -> Paging.Count(2),
          Paging.Page.CountState("running") -> Paging.Count(1)
        )
      )
    )
    assert(!JobsView.live(done, page(Paging.Page.Total(2), None, Paging.Page.CountState("done") -> Paging.Count(2))))
    assert(JobsView.live(List(job(Job.Id("q"), JobState.Queued)), Paging.Page.empty)) // no counts: the rows say
    val run = Agents.RunInfo(
      Agents.RunInfo.Id("r1"),
      Agents.RunInfo.Pipeline("enrich-source"),
      Agents.RunInfo.Target("a2020_b"),
      Agents.RunInfo.Status("done")
    )
    assert(
      RunsView.anyLive(
        List(run),
        page(
          Paging.Page.Total(2),
          None,
          Paging.Page.CountState("done") -> Paging.Count(1),
          Paging.Page.CountState("queued") -> Paging.Count(1)
        )
      )
    )
    assert(
      !RunsView.anyLive(List(run), page(Paging.Page.Total(1), None, Paging.Page.CountState("done") -> Paging.Count(1)))
    )
    assertEquals(ApprovalsView.qualifier(ListFilters.ApprovalFilter()).map(_.value), Some("open"))
    assertEquals(
      ApprovalsView.qualifier(ListFilters.ApprovalFilter(state = Some(Approval.State.Wire("all")))),
      None
    )
