package com.histmersion.domain

import com.histmersion.domain.AcquireRecheck.*

import java.time.Instant

/** The acquisition list's legal re-check, pure: when an item is due (its window, a new fetcher
  * generation), what a pass does with it (close, fetch into the metadata-only source, nothing without a
  * DOI), the reason it carries, how many items a pass may try within its caps and the shared allowance,
  * and the generation digest. */
class AcquireRecheckSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-27T12:00:00Z")
  private val Gen = "g1"
  private def daysAgo(d: Int) = now.minusSeconds(d.toLong * 86400L)
  private val settings = Settings(enabled = true, everyDays = 7, perDay = 40, perPass = 3, passMinutes = 10, sharePercent = 25)

  test("due: never checked, checked longer ago than every-days, or checked under another fetcher generation"):
    assert(isDue(None, Gen, 7, now))
    assert(!isDue(Some(LastCheck(daysAgo(2), Gen)), Gen, 7, now), "checked two days ago: not yet")
    assert(isDue(Some(LastCheck(daysAgo(7), Gen)), Gen, 7, now), "exactly the window: due")
    assert(isDue(Some(LastCheck(daysAgo(9), Gen)), Gen, 7, now))
    assert(isDue(Some(LastCheck(daysAgo(1), "g0")), Gen, 7, now), "a new fetcher or key: due again at once")
    assertEquals(dueAt(Some(LastCheck(daysAgo(2), Gen)), Gen, 7), Some(daysAgo(2).plusSeconds(7L * 86400L)))
    assertEquals(dueAt(Some(LastCheck(daysAgo(2), Gen)), Gen, 0), Some(daysAgo(1)), "a window under a day is a day")

  test("the generation bump: a different chain or setting is a different digest, the same parts the same"):
    val a = generation(List("unpaywall:open plugin=oa-unpaywall 1.0", "core:open plugin=oa-core 1.0 api-key="))
    val b = generation(List("unpaywall:open plugin=oa-unpaywall 1.0", "core:open plugin=oa-core 1.0 api-key=env:CORE_KEY"))
    val c = generation(List("unpaywall:open plugin=oa-unpaywall 1.0"))
    assertEquals(a, generation(List("unpaywall:open plugin=oa-unpaywall 1.0", "core:open plugin=oa-core 1.0 api-key=")))
    assert(a != b && a != c && b != c)
    assertEquals(a.length, 16)
    assert(a.forall(ch => ch.isDigit || ('a' to 'f').contains(ch)), a)

  test("the next time something falls due: the earliest window's end among the items of this generation"):
    val checks = List(LastCheck(daysAgo(1), Gen), LastCheck(daysAgo(3), Gen), LastCheck(daysAgo(1), "g0"))
    assertEquals(nextDue(checks, Gen, 7), Some(daysAgo(3).plusSeconds(7L * 86400L)))
    assertEquals(nextDue(Nil, Gen, 7), None)

  test("closing rules: in the corpus with its original → closed; metadata only → fetched into that source; else a new one"):
    val held = Map("10.1/a" -> Held("a2020_x", hasOriginal = true), "10.1/b" -> Held("b2021_y", hasOriginal = false))
    assertEquals(action(List("10.1/a"), held.get), Action.Close("a2020_x"))
    assertEquals(action(List("10.1/b"), held.get), Action.Fetch("10.1/b", Some("b2021_y")))
    assertEquals(action(List("10.1/c", "10.1/a"), held.get), Action.Fetch("10.1/c", None), "the first DOI an item names decides")
    assertEquals(action(Nil, held.get), Action.NoDoi)

  test("a failed legal ingest: no legal copy, or no answer"):
    assertEquals(failure(s"${IngestBacklog.NoLegalCopy} 10.1/x: no legal open-access PDF found"), Outcome.NoCopy)
    assertEquals(failure("HTTP 503 from the converter"), Outcome.Failed)
    assertEquals(Outcome.values.toList.flatMap(o => Outcome.parse(o.wire)), Outcome.values.toList)

  test("the reason: the day, what was found and what each fetcher answered"):
    val msg = s"${IngestBacklog.NoLegalCopy} 10.1/x: no legal open-access PDF found — unpaywall: no copy — none; " +
      "landing: refused — bot-protection 403"
    assertEquals(answers(msg), "unpaywall: no copy — none; landing: refused — bot-protection 403")
    assertEquals(reason(now, Outcome.NoCopy, msg),
      "checked 2026-10-27: no legal copy (unpaywall: no copy — none; landing: refused — bot-protection 403)")
    assertEquals(reason(now, Outcome.NoCopy, s"${IngestBacklog.NoLegalCopy} 10.1/x: no legal open-access PDF found"),
      "checked 2026-10-27: no legal copy (no legal open-access PDF found)")
    assertEquals(reason(now, Outcome.Closed, "a2020_x"), "checked 2026-10-27: in the corpus with its original as [[a2020_x]] — closed")
    assert(reason(now, Outcome.Fetched, "unpaywall").contains("a legal copy found (unpaywall)"))
    assertEquals(reason(now, Outcome.Failed, "HTTP 503"), "checked 2026-10-27: no answer (HTTP 503)")
    assert(reason(now, Outcome.NoCopy, msg + ("; x" * 400)).length <= 500)

  test("a pass's allowance: the day's cap, the pass's, and the shared allowance after the backlog, within the share"):
    assertEquals(allowance(settings, checkedToday = 0, shared = None), 3, "the pass's cap")
    assertEquals(allowance(settings, checkedToday = 39, shared = None), 1, "what is left of the day's")
    assertEquals(allowance(settings, checkedToday = 45, shared = None), 0, "never negative")
    assertEquals(allowance(settings, 0, Some(Shared(left = 60, cap = 60, backlogPending = 0, fetchedToday = 0))), 3)
    assertEquals(allowance(settings, 0, Some(Shared(left = 60, cap = 60, backlogPending = 59, fetchedToday = 0))), 1,
      "the watchers' backlog first")
    assertEquals(allowance(settings, 0, Some(Shared(left = 60, cap = 60, backlogPending = 0, fetchedToday = 14))), 1,
      "25 % of 60 = 15, 14 taken")
    assertEquals(allowance(settings, 0, Some(Shared(left = 2, cap = 60, backlogPending = 5, fetchedToday = 0))), 0)
    assertEquals(allowance(settings.copy(sharePercent = 0), 0, Some(Shared(60, 60, 0, 0))), 0, "no share: nothing under a shared cap")
    assertEquals(allowance(settings.copy(sharePercent = 100, perPass = 100), 0, Some(Shared(60, 60, 0, 0))), 40, "the day's cap still holds")

  test("a pass is due once its minutes passed since the last"):
    assert(passDue(None, 10, now))
    assert(!passDue(Some(now.minusSeconds(300)), 10, now))
    assert(passDue(Some(now.minusSeconds(600)), 10, now))
