package com.histmersion
package api

import com.histmersion.api.automation.activity.Counts
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.agents.ApprovalInfo
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.library.{Ingest, IngestMarkdown}
import com.histmersion.api.automation.Activity
import com.histmersion.api.library.Filter
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Approval
import com.histmersion.lint.{tolerated, LintRule}

/** The paged lists' pure parts: the heading's wording, the counts header, the page from the counts, and how the jobs /
  * runs / approvals listings say there is more.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PagingSuite extends munit.FunSuite:

  test("the heading says what is shown, and of how many, only when the page is not the whole list"):
    assertEquals(Paging.heading(Paging.ShownCount(37), Paging.Page.Total(37), Plural.Noun.Singular("job")), "37 jobs")
    assertEquals(Paging.heading(Paging.ShownCount(1), Paging.Page.Total(1), Plural.Noun.Singular("job")), "1 job")
    assertEquals(
      Paging.heading(Paging.ShownCount(100), Paging.Page.Total(2431), Plural.Noun.Singular("job")),
      "Latest 100 of 2,431 jobs"
    )
    assertEquals(
      Paging.heading(
        Paging.ShownCount(100),
        Paging.Page.Total(412),
        Plural.Noun.Singular("job"),
        qualifier = Some("failed")
      ),
      "Latest 100 of 412 failed jobs"
    )
    assertEquals(
      Paging
        .heading(Paging.ShownCount(37), Paging.Page.Total(37), Plural.Noun.Singular("job"), qualifier = Some("failed")),
      "37 failed jobs"
    )
    assertEquals(
      Paging.heading(
        Paging.ShownCount(1),
        Paging.Page.Total(1),
        Plural.Noun.Singular("query"),
        Plural.Noun.Plural("queries"),
        Some("open")
      ),
      "1 open query"
    )
    // a server without the headers: the total is unknown (0) and the page counts as the whole list
    assertEquals(Paging.heading(Paging.ShownCount(12), Paging.Page.Total(0), Plural.Noun.Singular("run")), "12 runs")
    assertEquals(Paging.heading(Paging.ShownCount(0), Paging.Page.Total(0), Plural.Noun.Singular("run")), "0 runs")

  test("numbers are grouped by hand, never through a locale"):
    assertEquals(Paging.grouped(Paging.GroupSize(0)), "0")
    assertEquals(Paging.grouped(Paging.GroupSize(999)), "999")
    assertEquals(Paging.grouped(Paging.GroupSize(1000)), "1,000")
    assertEquals(Paging.grouped(Paging.GroupSize(1234567)), "1,234,567")
    assertEquals(Paging.grouped(Paging.GroupSize(-4321)), "-4,321")

  test("the per-state counts round-trip through their header, a malformed pair skipped"):
    val counts = List(
      Paging.Page.CountState("done") -> Paging.Count(2400),
      Paging.Page.CountState("failed") -> Paging.Count(27),
      Paging.Page.CountState("queued") -> Paging.Count(3)
    )
    assertEquals(Paging.decodeCounts(Paging.encodeCounts(counts)), counts)
    assertEquals(Paging.encodeCounts(Nil), "")
    assertEquals(Paging.decodeCounts(Counts.Wire("")), Nil)
    assertEquals(
      Paging.decodeCounts(Counts.Wire(" done = 4 ,junk,x=y,running=1")),
      List("done" -> 4, "running" -> 1)
    )

  test("a page's total is its state's count under a filter, else every state's"):
    val counts =
      List(Paging.Page.CountState("done") -> Paging.Count(10), Paging.Page.CountState("failed") -> Paging.Count(3))
    val all = Paging.Page.of(counts, None, Some(Filter.Last("j9")))
    assertEquals((all.total, all.next, all.hasMore, all.all), (13, Some("j9"), true, 13))
    assertEquals(Paging.Page.of(counts, Some(Paging.Page.CountState("failed")), None).total, 3)
    assertEquals(Paging.Page.of(counts, Some(Paging.Page.CountState("running")), None).total, 0)
    assertEquals(all.count(Paging.Page.CountState("failed")), 3)
    assertEquals(all.count(Paging.Page.CountState("running")), 0)
    assert(!Paging.Page.empty.hasMore)

  test("limit + 1 rows say whether more lie beyond the page"):
    assertEquals(Paging.cut(Vector(1, 2, 3), Paging.Limit(2)), (Vector(1, 2), true))
    assertEquals(Paging.cut(Vector(1, 2), Paging.Limit(2)), (Vector(1, 2), false))
    assertEquals(Paging.cut(Vector.empty[Int], Paging.Limit(2)), (Vector.empty[Int], false))

  test("the older line names the next cursor, and is absent on the last page"):
    val more = Paging.Page(Paging.Page.Total(30), Some(Paging.Cursor("abc")), Nil)
    assertEquals(Paging.olderLine(more, c => Paging.OlderLine.Text(s"`--before $c`")), Some("Older: `--before abc`"))
    assertEquals(Paging.olderLine(more.modify(_.next).setTo(None), c => Paging.OlderLine.Text(c.value)), None)

  test("the jobs, runs and approvals listings head a partial page and say how to go on"):
    val page = Paging.Page(
      Paging.Page.Total(250),
      Some(Paging.Cursor("j2")),
      List(Paging.Page.CountState("done") -> Paging.Count(240), Paging.Page.CountState("failed") -> Paging.Count(10))
    )
    val js = Vector(
      JobInfo(SpatialHierarchyResult.Job.Id("j1"), JobKinds.Wire(Ingest.KindIngest), JobInfo.Status("done")),
      JobInfo(SpatialHierarchyResult.Job.Id("j2"), JobKinds.Wire(Ingest.KindIngest), JobInfo.Status("done"))
    )
    val md = IngestMarkdown.jobs(
      js,
      page,
      Some(Ingest.JobStatus("done")),
      Some(Ingest.JobAge("Older: `histmersion jobs --before j2`"))
    )
    assert(md.startsWith("### Latest 2 of 250 done jobs\n"), md)
    assert(md.endsWith("\n\nOlder: `histmersion jobs --before j2`"), md)
    assert(IngestMarkdown.jobs(js).startsWith("### 2 jobs\n"))
    assertEquals(IngestMarkdown.jobs(Vector.empty, page, Some(Ingest.JobStatus("failed"))), "_No failed jobs._")

    val rs = List(
      RunInfo(RunInfo.Id("r1"), RunInfo.Pipeline("enrich-source"), RunInfo.Target("a2020_b"), RunInfo.Status("done"))
    )
    val runs =
      AgentsMarkdown.runs(rs, page.modify(_.total).setTo(Paging.Page.Total(12)), None, Some(Paging.Cursor("Older: x")))
    assert(runs.startsWith("# Agent runs\n\nLatest 1 of 12 runs.\n\n- r1"), runs)
    assert(runs.endsWith("\n\nOlder: x"), runs)
    assertEquals(AgentsMarkdown.runs(rs), "# Agent runs\n\n- " + AgentsMarkdown.runLine(rs.head))
    assertEquals(AgentsMarkdown.runs(Nil, Paging.Page.empty, Some(RunInfo.Status("paused"))), "_No paused agent runs._")

    val as = List(
      ApprovalInfo(
        ApprovalInfo.Id(7),
        None,
        ApprovalInfo.Kind("status-change"),
        Citekey("a2020_b"),
        status = Some(ApprovalInfo.Status("contested"))
      )
    )
    val ap = AgentsMarkdown.approvals(
      as,
      Paging.Page(Paging.Page.Total(5), Some(Paging.Cursor("7")), Nil),
      Some(Approval.State.Wire("open")),
      Some(Paging.Cursor("Older: y"))
    )
    assert(ap.startsWith("# Approvals\n\nLatest 1 of 5 open approvals.\n\n- #7"), ap)
    assert(ap.endsWith("\n\nOlder: y"), ap)
    assert(!AgentsMarkdown.approvals(as).contains("Latest"))
