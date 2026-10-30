package com.histmersion.api

import com.histmersion.api.Agents.{ApprovalInfo, RunInfo}

/** The paged lists' pure parts: the heading's wording, the counts header, the page from the counts, and how the jobs /
  * runs / approvals listings say there is more.
  */
class PagingSuite extends munit.FunSuite:

  test("the heading says what is shown, and of how many, only when the page is not the whole list"):
    assertEquals(Paging.heading(37, 37, "job"), "37 jobs")
    assertEquals(Paging.heading(1, 1, "job"), "1 job")
    assertEquals(Paging.heading(100, 2431, "job"), "Latest 100 of 2,431 jobs")
    assertEquals(Paging.heading(100, 412, "job", qualifier = Some("failed")), "Latest 100 of 412 failed jobs")
    assertEquals(Paging.heading(37, 37, "job", qualifier = Some("failed")), "37 failed jobs")
    assertEquals(Paging.heading(1, 1, "query", "queries", Some("open")), "1 open query")
    // a server without the headers: the total is unknown (0) and the page counts as the whole list
    assertEquals(Paging.heading(12, 0, "run"), "12 runs")
    assertEquals(Paging.heading(0, 0, "run"), "0 runs")

  test("numbers are grouped by hand, never through a locale"):
    assertEquals(Paging.grouped(0), "0")
    assertEquals(Paging.grouped(999), "999")
    assertEquals(Paging.grouped(1000), "1,000")
    assertEquals(Paging.grouped(1234567), "1,234,567")
    assertEquals(Paging.grouped(-4321), "-4,321")

  test("the per-state counts round-trip through their header, a malformed pair skipped"):
    val counts = List("done" -> 2400, "failed" -> 27, "queued" -> 3)
    assertEquals(Paging.decodeCounts(Paging.encodeCounts(counts)), counts)
    assertEquals(Paging.encodeCounts(Nil), "")
    assertEquals(Paging.decodeCounts(""), Nil)
    assertEquals(Paging.decodeCounts(" done = 4 ,junk,x=y,running=1"), List("done" -> 4, "running" -> 1))

  test("a page's total is its state's count under a filter, else every state's"):
    val counts = List("done" -> 10, "failed" -> 3)
    val all = Paging.Page.of(counts, None, Some("j9"))
    assertEquals((all.total, all.next, all.hasMore, all.all), (13, Some("j9"), true, 13))
    assertEquals(Paging.Page.of(counts, Some("failed"), None).total, 3)
    assertEquals(Paging.Page.of(counts, Some("running"), None).total, 0)
    assertEquals(all.count("failed"), 3)
    assertEquals(all.count("running"), 0)
    assert(!Paging.Page.empty.hasMore)

  test("limit + 1 rows say whether more lie beyond the page"):
    assertEquals(Paging.cut(Vector(1, 2, 3), 2), (Vector(1, 2), true))
    assertEquals(Paging.cut(Vector(1, 2), 2), (Vector(1, 2), false))
    assertEquals(Paging.cut(Vector.empty[Int], 2), (Vector.empty[Int], false))

  test("the older line names the next cursor, and is absent on the last page"):
    val more = Paging.Page(30, Some("abc"), Nil)
    assertEquals(Paging.olderLine(more, c => s"`--before $c`"), Some("Older: `--before abc`"))
    assertEquals(Paging.olderLine(more.copy(next = None), c => c), None)

  test("the jobs, runs and approvals listings head a partial page and say how to go on"):
    val page = Paging.Page(250, Some("j2"), List("done" -> 240, "failed" -> 10))
    val js = Vector(JobInfo("j1", Ingest.KindIngest, "done"), JobInfo("j2", Ingest.KindIngest, "done"))
    val md = IngestMarkdown.jobs(js, page, Some("done"), Some("Older: `histmersion jobs --before j2`"))
    assert(md.startsWith("### Latest 2 of 250 done jobs\n"), md)
    assert(md.endsWith("\n\nOlder: `histmersion jobs --before j2`"), md)
    assert(IngestMarkdown.jobs(js).startsWith("### 2 jobs\n"))
    assertEquals(IngestMarkdown.jobs(Vector.empty, page, Some("failed")), "_No failed jobs._")

    val rs = List(RunInfo("r1", "enrich-source", "a2020_b", "done"))
    val runs = AgentsMarkdown.runs(rs, page.copy(total = 12), None, Some("Older: x"))
    assert(runs.startsWith("# Agent runs\n\nLatest 1 of 12 runs.\n\n- r1"), runs)
    assert(runs.endsWith("\n\nOlder: x"), runs)
    assertEquals(AgentsMarkdown.runs(rs), "# Agent runs\n\n- " + AgentsMarkdown.runLine(rs.head))
    assertEquals(AgentsMarkdown.runs(Nil, Paging.Page.empty, Some("paused")), "_No paused agent runs._")

    val as = List(ApprovalInfo(7, None, "status-change", "a2020_b", status = Some("contested")))
    val ap = AgentsMarkdown.approvals(as, Paging.Page(5, Some("7"), Nil), Some("open"), Some("Older: y"))
    assert(ap.startsWith("# Approvals\n\nLatest 1 of 5 open approvals.\n\n- #7"), ap)
    assert(ap.endsWith("\n\nOlder: y"), ap)
    assert(!AgentsMarkdown.approvals(as).contains("Latest"))
