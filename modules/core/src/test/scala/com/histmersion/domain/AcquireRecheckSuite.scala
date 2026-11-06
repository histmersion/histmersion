package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.AcquireRecheck.*

import java.time.Instant

/** The acquisition list's legal re-check, pure: when an item is due (its window, a new fetcher generation), what a pass
  * does with it (close, fetch into the metadata-only source, nothing without a DOI), the reason it carries, how many
  * items a pass may try within its caps and the shared allowance, and the generation digest.
  */
class AcquireRecheckSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-27T12:00:00Z")
  private val Gen = "g1"
  private def daysAgo(d: Int) = now.minusSeconds(d.toLong * 86400L)
  private val settings = Settings(
    enabled = com.histmersion.domain.AcquireRecheck.Settings.Enabled(true),
    everyDays = com.histmersion.domain.AcquireRecheck.Settings.EveryDays(7),
    perDay = com.histmersion.domain.AcquireRecheck.Settings.PerDay(40),
    perPass = com.histmersion.domain.AcquireRecheck.Settings.PerPass(3),
    passMinutes = com.histmersion.domain.AcquireRecheck.Settings.PassMinutes(10),
    sharePercent = com.histmersion.domain.AcquireRecheck.Settings.SharePercent(25),
    retryHours = com.histmersion.domain.AcquireRecheck.Settings.RetryHours(6)
  )

  test("due: never checked, checked longer ago than every-days, or checked under another fetcher generation"):
    assert(isDue(None, Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now))
    assert(!isDue(Some(LastCheck(daysAgo(2), Gen)), Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now), "checked two days ago: not yet")
    assert(isDue(Some(LastCheck(daysAgo(7), Gen)), Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now), "exactly the window: due")
    assert(isDue(Some(LastCheck(daysAgo(9), Gen)), Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now))
    assert(isDue(Some(LastCheck(daysAgo(1), com.histmersion.domain.AcquireRecheck.LastCheck.Generation("g0"))), Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now), "a new fetcher or key: due again at once")
    assertEquals(dueAt(Some(LastCheck(daysAgo(2), Gen)), Gen, com.histmersion.domain.AcquireRecheck.Settings.EveryDays(7), com.histmersion.domain.AcquireRecheck.Settings.RetryHours(6)), Some(daysAgo(2).plusSeconds(7L * 86400L)))
    assertEquals(dueAt(Some(LastCheck(daysAgo(2), Gen)), Gen, com.histmersion.domain.AcquireRecheck.Settings.EveryDays(0), com.histmersion.domain.AcquireRecheck.Settings.RetryHours(6)), Some(daysAgo(1)), "a window under a day is a day")

  test("the generation bump: a different chain or setting is a different digest, the same parts the same"):
    val a = generation(List(com.histmersion.domain.AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0"), com.histmersion.domain.AcquireRecheck.GenerationPart("core:open plugin=oa-core 1.0 api-key=")))
    val b =
      generation(List(com.histmersion.domain.AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0"), com.histmersion.domain.AcquireRecheck.GenerationPart("core:open plugin=oa-core 1.0 api-key=env:CORE_KEY")))
    val c = generation(List(com.histmersion.domain.AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0")))
    assertEquals(a, generation(List(com.histmersion.domain.AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0"), com.histmersion.domain.AcquireRecheck.GenerationPart("core:open plugin=oa-core 1.0 api-key="))))
    assert(a != b && a != c && b != c)
    assertEquals(a.length, 16)
    assert(a.forall(ch => ch.isDigit || ('a' to 'f').contains(ch)), a)

  test("the next time something falls due: the earliest window's end among the items of this generation"):
    val checks = List(LastCheck(daysAgo(1), Gen), LastCheck(daysAgo(3), Gen), LastCheck(daysAgo(1), com.histmersion.domain.AcquireRecheck.LastCheck.Generation("g0")))
    assertEquals(nextDue(checks, Gen, com.histmersion.domain.AcquireRecheck.NextDueEveryDays(7), com.histmersion.domain.AcquireRecheck.NextDueRetryHours(6)), Some(daysAgo(3).plusSeconds(7L * 86400L)))
    assertEquals(nextDue(Nil, Gen, com.histmersion.domain.AcquireRecheck.NextDueEveryDays(7), com.histmersion.domain.AcquireRecheck.NextDueRetryHours(6)), None)

  test(
    "closing rules: in the corpus with its original → closed; metadata only → fetched into that source; else a new one"
  ):
    val held = Map("10.1/a" -> Held(com.histmersion.domain.library.Citekey("a2020_x"), hasOriginal = com.histmersion.domain.AcquireRecheck.Held.HasOriginal(true)), "10.1/b" -> Held(com.histmersion.domain.library.Citekey("b2021_y"), hasOriginal = com.histmersion.domain.AcquireRecheck.Held.HasOriginal(false)))
    assertEquals(action(List(com.histmersion.domain.library.Doi("10.1/a")), held.get), Action.Close(com.histmersion.domain.library.Citekey("a2020_x")))
    assertEquals(action(List(com.histmersion.domain.library.Doi("10.1/b")), held.get), Action.Fetch(com.histmersion.domain.library.Doi("10.1/b"), Some(com.histmersion.domain.library.Citekey("b2021_y"))))
    assertEquals(
      action(List(com.histmersion.domain.library.Doi("10.1/c"), com.histmersion.domain.library.Doi("10.1/a")), held.get),
      Action.Fetch(com.histmersion.domain.library.Doi("10.1/c"), None),
      "the first DOI an item names decides"
    )
    assertEquals(action(Nil, held.get), Action.NoDoi)

  test("a failed legal ingest: no legal copy, or no answer"):
    assertEquals(failure(s"${IngestBacklog.NoLegalCopy} 10.1/x: no legal open-access PDF found"), Outcome.NoCopy)
    assertEquals(failure(com.histmersion.domain.IngestBacklog.Problem("HTTP 503 from the converter")), Outcome.Failed)
    assertEquals(Outcome.values.toList.flatMap(o => Outcome.parse(o.wire)), Outcome.values.toList)

  test("the reason: the day, what was found and what each fetcher answered"):
    val msg = s"${IngestBacklog.NoLegalCopy} 10.1/x: no legal open-access PDF found — unpaywall: no copy — none; " +
      "landing: refused — bot-protection 403"
    assertEquals(answers(msg), "unpaywall: no copy — none; landing: refused — bot-protection 403")
    assertEquals(
      reason(now, Outcome.NoCopy, msg),
      "checked 2026-10-27: no legal copy (unpaywall: no copy — none; landing: refused — bot-protection 403)"
    )
    assertEquals(
      reason(now, Outcome.NoCopy, s"${IngestBacklog.NoLegalCopy} 10.1/x: no legal open-access PDF found"),
      "checked 2026-10-27: no legal copy (no legal open-access PDF found)"
    )
    assertEquals(
      reason(now, Outcome.Closed, com.histmersion.domain.AcquireRecheck.Detail("a2020_x")),
      "checked 2026-10-27: in the corpus with its original as [[a2020_x]] — closed"
    )
    assert(reason(now, Outcome.Fetched, com.histmersion.domain.AcquireRecheck.Detail("unpaywall")).contains("a legal copy found (unpaywall)"))
    assertEquals(reason(now, Outcome.Failed, com.histmersion.domain.AcquireRecheck.Detail("HTTP 503")), "checked 2026-10-27: no answer (HTTP 503)")
    assert(reason(now, Outcome.NoCopy, msg + ("; x" * 400)).length <= 500)

  test("a pass's allowance: the day's cap, the pass's, and the shared allowance after the backlog, within the share"):
    assertEquals(allowance(settings, checkedToday = com.histmersion.domain.AcquireRecheck.CheckedToday(0), shared = None), 3, "the pass's cap")
    assertEquals(allowance(settings, checkedToday = com.histmersion.domain.AcquireRecheck.CheckedToday(39), shared = None), 1, "what is left of the day's")
    assertEquals(allowance(settings, checkedToday = com.histmersion.domain.AcquireRecheck.CheckedToday(45), shared = None), 0, "never negative")
    assertEquals(allowance(settings, com.histmersion.domain.AcquireRecheck.CheckedToday(0), Some(Shared(left = com.histmersion.domain.AcquireRecheck.Shared.Left(60), cap = com.histmersion.domain.AcquireRecheck.Shared.Cap(60), backlogPending = com.histmersion.domain.AcquireRecheck.Shared.BacklogPending(0), fetchedToday = com.histmersion.domain.AcquireRecheck.Shared.FetchedToday(0)))), 3)
    assertEquals(
      allowance(settings, com.histmersion.domain.AcquireRecheck.CheckedToday(0), Some(Shared(left = com.histmersion.domain.AcquireRecheck.Shared.Left(60), cap = com.histmersion.domain.AcquireRecheck.Shared.Cap(60), backlogPending = com.histmersion.domain.AcquireRecheck.Shared.BacklogPending(59), fetchedToday = com.histmersion.domain.AcquireRecheck.Shared.FetchedToday(0)))),
      1,
      "the watchers' backlog first"
    )
    assertEquals(
      allowance(settings, com.histmersion.domain.AcquireRecheck.CheckedToday(0), Some(Shared(left = com.histmersion.domain.AcquireRecheck.Shared.Left(60), cap = com.histmersion.domain.AcquireRecheck.Shared.Cap(60), backlogPending = com.histmersion.domain.AcquireRecheck.Shared.BacklogPending(0), fetchedToday = com.histmersion.domain.AcquireRecheck.Shared.FetchedToday(14)))),
      1,
      "25 % of 60 = 15, 14 taken"
    )
    assertEquals(allowance(settings, com.histmersion.domain.AcquireRecheck.CheckedToday(0), Some(Shared(left = com.histmersion.domain.AcquireRecheck.Shared.Left(2), cap = com.histmersion.domain.AcquireRecheck.Shared.Cap(60), backlogPending = com.histmersion.domain.AcquireRecheck.Shared.BacklogPending(5), fetchedToday = com.histmersion.domain.AcquireRecheck.Shared.FetchedToday(0)))), 0)
    assertEquals(
      allowance(settings.modify(_.sharePercent).setTo(0), com.histmersion.domain.AcquireRecheck.CheckedToday(0), Some(Shared(com.histmersion.domain.AcquireRecheck.Shared.Left(60), com.histmersion.domain.AcquireRecheck.Shared.Cap(60), com.histmersion.domain.AcquireRecheck.Shared.BacklogPending(0), com.histmersion.domain.AcquireRecheck.Shared.FetchedToday(0)))),
      0,
      "no share: nothing under a shared cap"
    )
    assertEquals(
      allowance(settings.modify(_.sharePercent).setTo(100).modify(_.perPass).setTo(100), com.histmersion.domain.AcquireRecheck.CheckedToday(0), Some(Shared(com.histmersion.domain.AcquireRecheck.Shared.Left(60), com.histmersion.domain.AcquireRecheck.Shared.Cap(60), com.histmersion.domain.AcquireRecheck.Shared.BacklogPending(0), com.histmersion.domain.AcquireRecheck.Shared.FetchedToday(0)))),
      40,
      "the day's cap still holds"
    )

  test("a pass is due once its minutes passed since the last"):
    assert(passDue(None, com.histmersion.domain.AcquireRecheck.Settings.PassMinutes(10), now))
    assert(!passDue(Some(now.minusSeconds(300)), com.histmersion.domain.AcquireRecheck.Settings.PassMinutes(10), now))
    assert(passDue(Some(now.minusSeconds(600)), com.histmersion.domain.AcquireRecheck.Settings.PassMinutes(10), now))

  test("a check that got no answer (a rate limit) is due again after retry-hours, not every-days"):
    val failed = LastCheck(now.minusSeconds(2L * 3600L), Gen, failed = com.histmersion.domain.AcquireRecheck.LastCheck.Failed(true))
    assert(!isDue(Some(failed), Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now), "two hours ago: not yet")
    assert(isDue(Some(failed.copy(at = now.minusSeconds(6L * 3600L))), Gen, com.histmersion.domain.AcquireRecheck.IsDueEveryDays(7), com.histmersion.domain.AcquireRecheck.IsDueRetryHours(6), now), "six hours ago: due")
    assertEquals(dueAt(Some(failed), Gen, com.histmersion.domain.AcquireRecheck.Settings.EveryDays(7), com.histmersion.domain.AcquireRecheck.Settings.RetryHours(6)), Some(failed.at.plusSeconds(6L * 3600L)))
    assertEquals(dueAt(Some(failed), Gen, com.histmersion.domain.AcquireRecheck.Settings.EveryDays(7), com.histmersion.domain.AcquireRecheck.Settings.RetryHours(0)), Some(failed.at.plusSeconds(3600L)), "a retry under an hour is an hour")
    assertEquals(nextDue(List(LastCheck(daysAgo(1), Gen), failed), Gen, com.histmersion.domain.AcquireRecheck.NextDueEveryDays(7), com.histmersion.domain.AcquireRecheck.NextDueRetryHours(6)), Some(failed.at.plusSeconds(6L * 3600L)))

  test("the order items are tried in: the never-checked first, then the oldest check, then the item id"):
    val cs = List(
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(5), Some(LastCheck(daysAgo(1), Gen))),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(9), None),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(2), Some(LastCheck(daysAgo(3), Gen))),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(4), None),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(1), Some(LastCheck(daysAgo(1), Gen)))
    )
    assertEquals(order(cs).map(_.item), List(4, 9, 2, 1, 5))

  test("a job over every item: all of them, due or not; resumed, it skips the ones checked since it started"):
    val since = now.minusSeconds(3600L)
    val cs = List(
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(1), None),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(2), Some(LastCheck(daysAgo(1), Gen))),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(3), Some(LastCheck(now.minusSeconds(60), Gen))),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(4), Some(LastCheck(since, Gen, failed = com.histmersion.domain.AcquireRecheck.LastCheck.Failed(true)))),
      Candidate(com.histmersion.domain.AcquireRecheck.Candidate.Item(5), Some(LastCheck(daysAgo(30), Gen)))
    )
    val notDue = (c: Candidate) => false
    assertEquals(
      jobPicks(JobPlan(all = com.histmersion.domain.AcquireRecheck.JobPlan.All(true), com.histmersion.domain.AcquireRecheck.JobPlan.UpTo(0), now), cs, com.histmersion.domain.AcquireRecheck.Tally.Tried(0), notDue).map(_.item),
      List(1, 5, 2, 4, 3),
      "a new job: every item, none of them due"
    )
    assertEquals(
      jobPicks(JobPlan(all = com.histmersion.domain.AcquireRecheck.JobPlan.All(true), com.histmersion.domain.AcquireRecheck.JobPlan.UpTo(0), since), cs, com.histmersion.domain.AcquireRecheck.Tally.Tried(2), notDue).map(_.item),
      List(1, 5, 2),
      "resumed: #3 and #4 were checked at or after its start — even the one that got no answer"
    )
    assert(
      checkedSince(Some(LastCheck(since, Gen)), since) && !checkedSince(
        Some(LastCheck(since.minusMillis(1), Gen)),
        since
      )
    )
    assert(!checkedSince(None, since))

  test("a capped job: only the due items, no more than it planned less what it tried already"):
    val since = now.minusSeconds(3600L)
    val cs = (1 to 6).toList.map(i => Candidate(i, if i == 6 then Some(LastCheck(now, Gen)) else None))
    val due = (c: Candidate) => c.item != 2
    assertEquals(jobPicks(JobPlan(all = com.histmersion.domain.AcquireRecheck.JobPlan.All(false), com.histmersion.domain.AcquireRecheck.JobPlan.UpTo(4), since), cs, com.histmersion.domain.AcquireRecheck.Tally.Tried(0), due).map(_.item), List(1, 3, 4, 5))
    assertEquals(jobPicks(JobPlan(all = com.histmersion.domain.AcquireRecheck.JobPlan.All(false), com.histmersion.domain.AcquireRecheck.JobPlan.UpTo(4), since), cs, com.histmersion.domain.AcquireRecheck.Tally.Tried(3), due).map(_.item), List(1), "resumed after 3 tries")
    assertEquals(jobPicks(JobPlan(all = com.histmersion.domain.AcquireRecheck.JobPlan.All(false), com.histmersion.domain.AcquireRecheck.JobPlan.UpTo(4), since), cs, com.histmersion.domain.AcquireRecheck.Tally.Tried(9), due), Nil)

  test("which stopped job a later one takes up: a restart's interrupted one, or one the drain stopped"):
    assert(resumable(com.histmersion.api.jobs.JobState.Wire("failed"), Some(com.histmersion.api.jobs.JobState.Interrupted)))
    assert(resumable(com.histmersion.api.jobs.JobState.Wire("failed"), Some(StoppedDraining)))
    assert(!resumable(com.histmersion.api.jobs.JobState.Wire("failed"), Some(com.histmersion.domain.AcquireRecheck.Problem("the acquisition toolset is not connected: nothing tried"))))
    assert(!resumable(com.histmersion.api.jobs.JobState.Wire("failed"), Some(com.histmersion.domain.AcquireRecheck.Problem("a restore started; stopped"))))
    assert(!resumable(com.histmersion.api.jobs.JobState.Wire("done"), None))
    assert(!resumable(com.histmersion.api.jobs.JobState.Wire("running"), None))

  test("the job's progress line counts what it found, with the last no-answer's reason, shortened"):
    val t = Tally(total = com.histmersion.domain.AcquireRecheck.Tally.Total(453))
      .add(Outcome.Closed, "a2020_x")
      .add(Outcome.Fetched, "unpaywall")
      .add(Outcome.NoCopy, "none")
      .add(
        Outcome.Failed,
        "HTTP 429 Too Many Requests from api.example.invalid — retry after 3600 seconds, said the upstream"
      )
    assertEquals(t.tried, 3)
    assertEquals(
      progressLine(t),
      "3/453 items · 1 closed · 1 fetched · 1 no legal copy · 1 failed (HTTP 429 Too Many Requests from api.example.invalid — retry …)"
    )
    assertEquals(progressLine(Tally(total = com.histmersion.domain.AcquireRecheck.Tally.Total(2))), "0/2 items · 0 closed · 0 fetched · 0 no legal copy · 0 failed")
