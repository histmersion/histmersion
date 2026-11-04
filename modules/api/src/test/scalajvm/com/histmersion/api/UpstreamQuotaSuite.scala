package com.histmersion.api

/** CORE's budget as `histmersion about`, the autopilot status and a candidate sweep show it. */

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.library.{CandidatesMarkdown, CandidateSweep, UpstreamQuota, UpstreamQuotaMarkdown}
import com.histmersion.api.views.AutopilotStatus
class UpstreamQuotaSuite extends munit.FunSuite:

  private val q =
    UpstreamQuota("core", "2026-10-26", 10, 4, 900, 600, 20, rateLimit = Some(25), rateRemaining = Some(18))

  test("one line: today's tokens against the budget, the searches' share, the pace and CORE's window"):
    assertEquals(
      UpstreamQuotaMarkdown.line(q),
      "CORE today (UTC 2026-10-26): 14 of 900 tokens — PDF lookups 10, searches 4 of 600 · at most 20 a minute · " +
        "CORE's window 18 of 25 left"
    )
    val blocked = q.modify(_.blockedUntil).setTo(Some("2026-10-26T18:52:11Z")).modify(_.blockedWhy).setTo(Some("HTTP 429"))
    assert(UpstreamQuotaMarkdown.line(blocked).endsWith("· **not asked until 2026-10-26T18:52:11Z** (HTTP 429)"))

  test("about and the autopilot status carry the line; without a budget yet, neither does"):
    val about = About("Histmersion", BuildStamp("0.1"), Health("ok", "ok", None, None), JobCounts(), None, None, None)
    assert(!AboutMarkdown.render(about).contains("CORE today"))
    assert(
      AboutMarkdown.render(about.modify(_.upstreams).setTo(List(q))).contains("- CORE today (UTC 2026-10-26): 14 of 900 tokens")
    )
    val st = AutopilotStatus(on = true)
    assert(!st.markdown.contains("CORE today"))
    val md = st.modify(_.upstreams).setTo(List(q)).markdown
    assert(md.contains("- legal open-access ingests today: 0\n- CORE today (UTC 2026-10-26)"), md)

  test("a sweep says which index it did not ask, and why"):
    val sw = CandidatesMarkdown.sweep(
      CandidateSweep("g", Nil, 0, 0, 0, Map.empty, 0, notes = List("core not asked this sweep: CORE asked to wait"))
    )
    assert(sw.contains("- **core not asked this sweep: CORE asked to wait**"), sw)
