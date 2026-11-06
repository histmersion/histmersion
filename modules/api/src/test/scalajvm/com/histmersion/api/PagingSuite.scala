package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.{ApprovalInfo, RunInfo}
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.library.{Ingest, IngestMarkdown}

/** The paged lists' pure parts: the heading's wording, the counts header, the page from the counts, and how the jobs /
  * runs / approvals listings say there is more.
  */
class PagingSuite extends munit.FunSuite:

  test("the heading says what is shown, and of how many, only when the page is not the whole list"):
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(37), com.histmersion.api.Paging.Page.Total(37), com.histmersion.api.Plural.Noun.Singular("job")), "37 jobs")
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(1), com.histmersion.api.Paging.Page.Total(1), com.histmersion.api.Plural.Noun.Singular("job")), "1 job")
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(100), com.histmersion.api.Paging.Page.Total(2431), com.histmersion.api.Plural.Noun.Singular("job")), "Latest 100 of 2,431 jobs")
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(100), com.histmersion.api.Paging.Page.Total(412), com.histmersion.api.Plural.Noun.Singular("job"), qualifier = Some("failed")), "Latest 100 of 412 failed jobs")
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(37), com.histmersion.api.Paging.Page.Total(37), com.histmersion.api.Plural.Noun.Singular("job"), qualifier = Some("failed")), "37 failed jobs")
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(1), com.histmersion.api.Paging.Page.Total(1), com.histmersion.api.Plural.Noun.Singular("query"), com.histmersion.api.Plural.Noun.Plural("queries"), Some("open")), "1 open query")
    // a server without the headers: the total is unknown (0) and the page counts as the whole list
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(12), com.histmersion.api.Paging.Page.Total(0), com.histmersion.api.Plural.Noun.Singular("run")), "12 runs")
    assertEquals(Paging.heading(com.histmersion.api.Paging.ShownCount(0), com.histmersion.api.Paging.Page.Total(0), com.histmersion.api.Plural.Noun.Singular("run")), "0 runs")

  test("numbers are grouped by hand, never through a locale"):
    assertEquals(Paging.grouped(com.histmersion.api.Paging.GroupSize(0)), "0")
    assertEquals(Paging.grouped(com.histmersion.api.Paging.GroupSize(999)), "999")
    assertEquals(Paging.grouped(com.histmersion.api.Paging.GroupSize(1000)), "1,000")
    assertEquals(Paging.grouped(com.histmersion.api.Paging.GroupSize(1234567)), "1,234,567")
    assertEquals(Paging.grouped(com.histmersion.api.Paging.GroupSize(-4321)), "-4,321")

  test("the per-state counts round-trip through their header, a malformed pair skipped"):
    val counts = List("done" -> 2400, "failed" -> 27, "queued" -> 3)
    assertEquals(Paging.decodeCounts(Paging.encodeCounts(counts)), counts)
    assertEquals(Paging.encodeCounts(Nil), "")
    assertEquals(Paging.decodeCounts(com.histmersion.api.automation.Activity.Counts.Wire("")), Nil)
    assertEquals(Paging.decodeCounts(com.histmersion.api.automation.Activity.Counts.Wire(" done = 4 ,junk,x=y,running=1")), List("done" -> 4, "running" -> 1))

  test("a page's total is its state's count under a filter, else every state's"):
    val counts = List("done" -> 10, "failed" -> 3)
    val all = Paging.Page.of(counts, None, Some(com.histmersion.api.library.Filter.Last("j9")))
    assertEquals((all.total, all.next, all.hasMore, all.all), (13, Some("j9"), true, 13))
    assertEquals(Paging.Page.of(counts, Some(com.histmersion.api.Paging.Page.CountState("failed")), None).total, 3)
    assertEquals(Paging.Page.of(counts, Some(com.histmersion.api.Paging.Page.CountState("running")), None).total, 0)
    assertEquals(all.count("failed"), 3)
    assertEquals(all.count("running"), 0)
    assert(!Paging.Page.empty.hasMore)

  test("limit + 1 rows say whether more lie beyond the page"):
    assertEquals(Paging.cut(Vector(1, 2, 3), com.histmersion.api.Paging.Limit(2)), (Vector(1, 2), true))
    assertEquals(Paging.cut(Vector(1, 2), com.histmersion.api.Paging.Limit(2)), (Vector(1, 2), false))
    assertEquals(Paging.cut(Vector.empty[Int], com.histmersion.api.Paging.Limit(2)), (Vector.empty[Int], false))

  test("the older line names the next cursor, and is absent on the last page"):
    val more = Paging.Page(com.histmersion.api.Paging.Page.Total(30), Some(com.histmersion.api.Paging.Cursor("abc")), Nil)
    assertEquals(Paging.olderLine(more, c => s"`--before $c`"), Some("Older: `--before abc`"))
    assertEquals(Paging.olderLine(more.modify(_.next).setTo(None), c => c), None)

  test("the jobs, runs and approvals listings head a partial page and say how to go on"):
    val page = Paging.Page(com.histmersion.api.Paging.Page.Total(250), Some(com.histmersion.api.Paging.Cursor("j2")), List("done" -> 240, "failed" -> 10))
    val js = Vector(JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"), com.histmersion.api.jobs.JobKinds.Wire(Ingest.KindIngest), com.histmersion.api.jobs.JobInfo.Status("done")), JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id("j2"), com.histmersion.api.jobs.JobKinds.Wire(Ingest.KindIngest), com.histmersion.api.jobs.JobInfo.Status("done")))
    val md = IngestMarkdown.jobs(js, page, Some(com.histmersion.api.library.Ingest.JobStatus("done")), Some(com.histmersion.api.library.Ingest.JobAge("Older: `histmersion jobs --before j2`")))
    assert(md.startsWith("### Latest 2 of 250 done jobs\n"), md)
    assert(md.endsWith("\n\nOlder: `histmersion jobs --before j2`"), md)
    assert(IngestMarkdown.jobs(js).startsWith("### 2 jobs\n"))
    assertEquals(IngestMarkdown.jobs(Vector.empty, page, Some(com.histmersion.api.library.Ingest.JobStatus("failed"))), "_No failed jobs._")

    val rs = List(RunInfo(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), com.histmersion.api.automation.Agents.RunInfo.Pipeline("enrich-source"), com.histmersion.api.automation.Agents.RunInfo.Target("a2020_b"), com.histmersion.api.automation.Agents.RunInfo.Status("done")))
    val runs = AgentsMarkdown.runs(rs, page.modify(_.total).setTo(12), None, Some(com.histmersion.api.Paging.Cursor("Older: x")))
    assert(runs.startsWith("# Agent runs\n\nLatest 1 of 12 runs.\n\n- r1"), runs)
    assert(runs.endsWith("\n\nOlder: x"), runs)
    assertEquals(AgentsMarkdown.runs(rs), "# Agent runs\n\n- " + AgentsMarkdown.runLine(rs.head))
    assertEquals(AgentsMarkdown.runs(Nil, Paging.Page.empty, Some(com.histmersion.api.automation.Agents.RunInfo.Status("paused"))), "_No paused agent runs._")

    val as = List(ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(7), None, com.histmersion.api.automation.Agents.ApprovalInfo.Kind("status-change"), com.histmersion.api.library.Citekey("a2020_b"), status = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Status("contested"))))
    val ap = AgentsMarkdown.approvals(as, Paging.Page(com.histmersion.api.Paging.Page.Total(5), Some(com.histmersion.api.Paging.Cursor("7")), Nil), Some(com.histmersion.api.automation.Approval.State.Wire("open")), Some(com.histmersion.api.Paging.Cursor("Older: y")))
    assert(ap.startsWith("# Approvals\n\nLatest 1 of 5 open approvals.\n\n- #7"), ap)
    assert(ap.endsWith("\n\nOlder: y"), ap)
    assert(!AgentsMarkdown.approvals(as).contains("Latest"))
