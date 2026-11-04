package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Acquire.Checks.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.library.AcquireChecksMarkdown
import com.histmersion.api.views.{AutopilotStatus, QueueListing}

/** The acquisition list's re-check as every surface shows it: an item's last check under it in `queue acquire`, the
  * status line (`agent autopilot status`, `about`) and a pass's report. Pure.
  */
class AcquireChecksSuite extends munit.FunSuite:

  private val item = Queue.QueueItem(7, "acquire", "Zz 2019, Tin — https://doi.org/10.1/zz", Some("tin"), Queue.Open)
  private val check = AcquireCheck(
    7,
    "2026-10-27T10:00:00Z",
    NoCopy,
    "checked 2026-10-27: no legal copy (unpaywall: no copy — none; landing: refused — bot-protection 403)",
    2
  )

  test("`queue acquire` says under an item when it was last checked and what each fetcher answered"):
    val md = QueueListing(Queue.Kind.Acquire.label, List(item), List(check)).markdown.getOrElse("")
    assert(
      md.contains(
        "`#7`\n  - _checked 2026-10-27: no legal copy (unpaywall: no copy — none; landing: refused — bot-protection 403)_"
      ),
      md
    )
    assert(!Queue.markdown(Queue.Kind.Acquire, List(item)).contains("checked"), "an unchecked item says nothing")

  test("the status line: what is due, what it closed and found this week, today's tries, the next pass"):
    val s = RecheckStatus(
      enabled = true,
      withDoi = 455,
      due = 12,
      triedToday = 5,
      perDay = 40,
      fetchedToday = 1,
      closedWeek = 3,
      fetchedWeek = 1,
      everyDays = 7,
      nextPass = Some("at the next managers' tick")
    )
    assertEquals(
      AcquireChecksMarkdown.line(s),
      "acquire re-check: 12 of 455 items with a DOI due, 3 closed and 1 found this week, 5 of 40 tried today — next pass at the next managers' tick"
    )
    assert(
      AcquireChecksMarkdown
        .line(s.modify(_.note).setTo(Some("held while the autopilot drains")))
        .endsWith("(held while the autopilot drains)")
    )
    assertEquals(
      AcquireChecksMarkdown.line(RecheckStatus(enabled = false)),
      "acquire re-check: off (acquire.recheck.enabled = false)"
    )

  test("the autopilot status and about carry the line; the re-check's finds are named among the day's ingests"):
    val r = RecheckStatus(enabled = true, withDoi = 3, due = 1, triedToday = 2, perDay = 40, fetchedToday = 2)
    val st = AutopilotStatus(on = true, ingestedToday = 9, ingestCap = 60, acquireRecheck = Some(r))
    val md = st.markdown
    assert(
      md.contains(
        "- legal open-access ingests today: 9 of 60 (all watchers), 2 of them by the acquisition list's re-check"
      ),
      md
    )
    assert(md.contains("- acquire re-check: 1 of 3 items with a DOI due"), md)
    assert(!st.modify(_.acquireRecheck).setTo(None).markdown.contains("re-check"))

  test("a pass's report: the counts and one line per item; a dry run says it asked no fetcher"):
    val r = RecheckResult(
      dryRun = false,
      open = 4,
      withDoi = 3,
      due = 2,
      allowance = 2,
      closed = 1,
      fetched = 1,
      noCopy = 1,
      rows = List(
        RecheckRow(1, Some("10.1/a"), Closed, "in the corpus with its original", Some("a2020_x")),
        RecheckRow(2, Some("10.1/b"), Fetched, "checked 2026-10-27: a legal copy found (unpaywall)", Some("b2021_y")),
        RecheckRow(3, Some("10.1/c"), NoCopy, "checked 2026-10-27: no legal copy")
      )
    )
    val md = AcquireChecksMarkdown.result(r)
    assert(md.contains("- closed 1 item the corpus holds with its text"), md)
    assert(
      md.contains(
        "- a legal copy found for 1 (converting: the conversion closes the item), no legal copy for 1, no answer for 0"
      ),
      md
    )
    assert(md.contains("- #1 https://doi.org/10.1/a · closed [[a2020_x]]: in the corpus with its original"), md)
    val dry = AcquireChecksMarkdown.result(
      RecheckResult(dryRun = true, rows = List(RecheckRow(3, Some("10.1/c"), WouldTry, "never checked")))
    )
    assert(
      dry.contains("dry run: no fetcher asked, nothing stored") && dry.contains(
        "- would try 1 item through the legal chain"
      ),
      dry
    )
    assert(AcquireChecksMarkdown.result(RecheckResult(dryRun = false)).contains("_Nothing to do._"))

  test("a pass that runs as a job: what it re-checks and how to follow it; the jobs list names it"):
    val detail = RecheckJob(all = true, since = "2026-10-27T10:00:00Z", by = "cli")
    val job = JobInfo("j-1", RecheckJob.JobKind, "running", detail = Some(RecheckJob.Detail(Some(detail))))
    val md = AcquireChecksMarkdown.result(
      RecheckResult(dryRun = false, open = 460, withDoi = 453, due = 453, allowance = 453, job = Some(job))
    )
    assert(md.contains("- 460 open items, 453 with a DOI; 453 to try"), md)
    assert(
      md.contains(
        "- started as job `j-1`: the acquisition list's legal re-check of every open item with a DOI " +
          "— `histmersion jobs j-1` follows it"
      ),
      md
    )
    assertEquals(JobDetail.subject(job), Some("the acquisition list's legal re-check of every open item with a DOI"))
    assertEquals(
      RecheckJob.describe(RecheckJob(all = false, upTo = 5, resumes = Some("j-0"))),
      "the acquisition list's legal re-check of up to 5 due items (resuming job j-0)"
    )

  test("the status line names the re-check job running, on or off"):
    val running =
      Some("job j-1 running: 120/453 items · 3 closed · 9 fetched · 98 no legal copy · 10 failed (HTTP 429)")
    val s = RecheckStatus(enabled = true, withDoi = 453, due = 300, perDay = 40, job = running)
    assert(
      AcquireChecksMarkdown
        .line(s)
        .endsWith(
          " — job j-1 running: 120/453 items · 3 closed · 9 fetched · 98 no legal copy · " +
            "10 failed (HTTP 429)"
        ),
      AcquireChecksMarkdown.line(s)
    )
    assertEquals(
      AcquireChecksMarkdown.line(RecheckStatus(enabled = false, job = Some("job j-1 running: 0/2 items"))),
      "acquire re-check: off (acquire.recheck.enabled = false) — job j-1 running: 0/2 items"
    )
