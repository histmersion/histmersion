package com.histmersion
package domain

import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.AcquireRecheck.*

import java.time.Instant
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey
import com.histmersion.api.jobs.JobState
import com.histmersion.lint.{LintRule, tolerated}

/** The acquisition list's legal re-check, pure: when an item is due (its window, a new fetcher generation), what a pass
  * does with it (close, fetch into the metadata-only source, nothing without a DOI), the reason it carries, how many
  * items a pass may try within its caps and the shared allowance, and the generation digest.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class AcquireRecheckSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-27T12:00:00Z")
  private val Gen = LastCheck.Generation("g1")
  private def daysAgo(d: Int) = now.minusSeconds(d.toLong * 86400L)
  private val settings = Settings(
    enabled = AcquireRecheck.Settings.Enabled(true),
    everyDays = AcquireRecheck.Settings.EveryDays(7),
    perDay = AcquireRecheck.Settings.PerDay(40),
    perPass = AcquireRecheck.Settings.PerPass(3),
    passMinutes = AcquireRecheck.Settings.PassMinutes(10),
    sharePercent = AcquireRecheck.Settings.SharePercent(25),
    retryHours = AcquireRecheck.Settings.RetryHours(6)
  )

  test("due: never checked, checked longer ago than every-days, or checked under another fetcher generation"):
    assert(isDue(None, Gen, AcquireRecheck.IsDueEveryDays(7), AcquireRecheck.IsDueRetryHours(6), now))
    assert(
      !isDue(
        Some(LastCheck(daysAgo(2), Gen)),
        Gen,
        AcquireRecheck.IsDueEveryDays(7),
        AcquireRecheck.IsDueRetryHours(6),
        now
      ),
      "checked two days ago: not yet"
    )
    assert(
      isDue(
        Some(LastCheck(daysAgo(7), Gen)),
        Gen,
        AcquireRecheck.IsDueEveryDays(7),
        AcquireRecheck.IsDueRetryHours(6),
        now
      ),
      "exactly the window: due"
    )
    assert(
      isDue(
        Some(LastCheck(daysAgo(9), Gen)),
        Gen,
        AcquireRecheck.IsDueEveryDays(7),
        AcquireRecheck.IsDueRetryHours(6),
        now
      )
    )
    assert(
      isDue(
        Some(LastCheck(daysAgo(1), AcquireRecheck.LastCheck.Generation("g0"))),
        Gen,
        AcquireRecheck.IsDueEveryDays(7),
        AcquireRecheck.IsDueRetryHours(6),
        now
      ),
      "a new fetcher or key: due again at once"
    )
    assertEquals(
      dueAt(
        Some(LastCheck(daysAgo(2), Gen)),
        Gen,
        AcquireRecheck.Settings.EveryDays(7),
        AcquireRecheck.Settings.RetryHours(6)
      ),
      Some(daysAgo(2).plusSeconds(7L * 86400L))
    )
    assertEquals(
      dueAt(
        Some(LastCheck(daysAgo(2), Gen)),
        Gen,
        AcquireRecheck.Settings.EveryDays(0),
        AcquireRecheck.Settings.RetryHours(6)
      ),
      Some(daysAgo(1)),
      "a window under a day is a day"
    )

  test("the generation bump: a different chain or setting is a different digest, the same parts the same"):
    val a = generation(
      List(
        AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0"),
        AcquireRecheck.GenerationPart("core:open plugin=oa-core 1.0 api-key=")
      )
    )
    val b =
      generation(
        List(
          AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0"),
          AcquireRecheck.GenerationPart("core:open plugin=oa-core 1.0 api-key=env:CORE_KEY")
        )
      )
    val c = generation(List(AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0")))
    assertEquals(
      a,
      generation(
        List(
          AcquireRecheck.GenerationPart("unpaywall:open plugin=oa-unpaywall 1.0"),
          AcquireRecheck.GenerationPart("core:open plugin=oa-core 1.0 api-key=")
        )
      )
    )
    assert(a != b && a != c && b != c)
    assertEquals(a.value.length, 16)
    assert(a.value.forall(ch => ch.isDigit || ('a' to 'f').contains(ch)), a)

  test("the next time something falls due: the earliest window's end among the items of this generation"):
    val checks = List(
      LastCheck(daysAgo(1), Gen),
      LastCheck(daysAgo(3), Gen),
      LastCheck(daysAgo(1), AcquireRecheck.LastCheck.Generation("g0"))
    )
    assertEquals(
      nextDue(checks, Gen, AcquireRecheck.NextDueEveryDays(7), AcquireRecheck.NextDueRetryHours(6)),
      Some(daysAgo(3).plusSeconds(7L * 86400L))
    )
    assertEquals(nextDue(Nil, Gen, AcquireRecheck.NextDueEveryDays(7), AcquireRecheck.NextDueRetryHours(6)), None)

  test(
    "closing rules: in the corpus with its original → closed; metadata only → fetched into that source; else a new one"
  ):
    val held = Map(
      Doi("10.1/a") -> Held(Citekey("a2020_x"), hasOriginal = AcquireRecheck.Held.HasOriginal(true)),
      Doi("10.1/b") -> Held(Citekey("b2021_y"), hasOriginal = AcquireRecheck.Held.HasOriginal(false))
    )
    assertEquals(action(List(Doi("10.1/a")), held.get), Action.Close(Citekey("a2020_x")))
    assertEquals(action(List(Doi("10.1/b")), held.get), Action.Fetch(Doi("10.1/b"), Some(Citekey("b2021_y"))))
    assertEquals(
      action(List(Doi("10.1/c"), Doi("10.1/a")), held.get),
      Action.Fetch(Doi("10.1/c"), None),
      "the first DOI an item names decides"
    )
    assertEquals(action(Nil, held.get), Action.NoDoi)

  test("a failed legal ingest: no legal copy, or no answer"):
    assertEquals(
      failure(Problem.NotFound.Copy(Problem.Raw("10.1/x"), Vector.empty)),
      Outcome.NoCopy
    )
    assertEquals(failure(Problem.Unclassified(Legacy.Text("HTTP 503 from the converter"))), Outcome.Failed)
    assertEquals(Outcome.values.toList.flatMap(o => Outcome.parse(o.wire)), Outcome.values.toList)

  test("the reason: the day, what was found and what each fetcher answered"):
    val copy = Problem.NotFound.Copy(
      Problem.Raw("10.1/x"),
      Vector(
        Problem.NotFound.Answer(
          Problem.NotFound.Fetcher("unpaywall"),
          Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NotHeld),
          Problem.NotFound.Copy(Problem.Raw("doi:10.1/x"), Vector.empty)
        ),
        Problem.NotFound.Answer(
          Problem.NotFound.Fetcher("landing"),
          Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.BotChallenge),
          Problem.Upstream.Challenged(
            Problem.Upstream.Name("landing"),
            Problem.Upstream.Url("https://x.example/f"),
            Problem.Upstream.Status(403),
            Vector.empty
          )
        )
      )
    )
    val said = "unpaywall: no copy — no legal open-access PDF found for doi:10.1/x; " +
      "landing: refused (scripts refused) — landing answered HTTP 403 at https://x.example/f with a bot-protection challenge, not the page"
    val answered = answers(copy)
    assertEquals(answered.map(_.said), copy.answers.map(_.said))
    assertEquals(answers(Problem.Upstream.Offline(Problem.Upstream.Name("converter"), None)), Vector.empty)
    def attempt(
        outcome: library.acquisition.AcquireAttempt.Outcome,
        said: Vector[Problem.NotFound.Answer] = Vector.empty
    ) =
      library.acquisition.AcquireAttempt(
        now,
        library.acquisition.AcquireAttempt.Trigger.Recheck,
        Gen,
        outcome,
        said,
        None
      )
    assertEquals(
      Rationale.of(attempt(library.acquisition.AcquireAttempt.Outcome.NoCopy, answered)).said,
      s"checked 2026-10-27: no legal copy ($said)"
    )
    assertEquals(
      Rationale.of(attempt(library.acquisition.AcquireAttempt.Outcome.NoCopy)).said,
      "checked 2026-10-27: no legal copy"
    )
    assertEquals(
      Rationale.of(attempt(library.acquisition.AcquireAttempt.Outcome.Closed(Citekey("a2020_x")))).said,
      "checked 2026-10-27: in the corpus with its original as [[a2020_x]] — closed"
    )
    assert(
      Rationale
        .of(
          attempt(
            library.acquisition.AcquireAttempt.Outcome
              .Fetched(library.acquisition.AcquireAttempt.Outcome.Source("unpaywall"))
          )
        )
        .said
        .contains("a legal copy found (unpaywall)")
    )
    assertEquals(
      Rationale
        .of(
          attempt(
            library.acquisition.AcquireAttempt.Outcome
              .Failed(Problems.of(Problem.Upstream.Offline(Problem.Upstream.Name("converter"), None)))
          )
        )
        .said,
      s"checked 2026-10-27: no answer (${Problem.Upstream.Offline(Problem.Upstream.Name("converter"), None).sentence})"
    )
    assertEquals(
      Rationale
        .of(
          attempt(
            library.acquisition.AcquireAttempt.Outcome.Legacy(
              library.acquisition.AcquireAttempt.Outcome.LegacyNote("checked 2026-10-01: no legal copy (as stored)"),
              library.acquisition.AcquireAttempt.Tries(2)
            )
          )
        )
        .said,
      "checked 2026-10-01: no legal copy (as stored)"
    )
    assert(
      Rationale
        .of(attempt(library.acquisition.AcquireAttempt.Outcome.NoCopy, Vector.fill(50)(answered.head)))
        .said
        .length <= 500
    )

  test("a pass's allowance: the day's cap, the pass's, and the shared allowance after the backlog, within the share"):
    assertEquals(allowance(settings, checkedToday = AcquireRecheck.CheckedToday(0), shared = None), 3, "the pass's cap")
    assertEquals(
      allowance(settings, checkedToday = AcquireRecheck.CheckedToday(39), shared = None),
      1,
      "what is left of the day's"
    )
    assertEquals(
      allowance(settings, checkedToday = AcquireRecheck.CheckedToday(45), shared = None),
      0,
      "never negative"
    )
    assertEquals(
      allowance(
        settings,
        AcquireRecheck.CheckedToday(0),
        Some(
          Shared(
            left = AcquireRecheck.Shared.Left(60),
            cap = AcquireRecheck.Shared.Cap(60),
            backlogPending = AcquireRecheck.Shared.BacklogPending(0),
            fetchedToday = AcquireRecheck.Shared.FetchedToday(0)
          )
        )
      ),
      3
    )
    assertEquals(
      allowance(
        settings,
        AcquireRecheck.CheckedToday(0),
        Some(
          Shared(
            left = AcquireRecheck.Shared.Left(60),
            cap = AcquireRecheck.Shared.Cap(60),
            backlogPending = AcquireRecheck.Shared.BacklogPending(59),
            fetchedToday = AcquireRecheck.Shared.FetchedToday(0)
          )
        )
      ),
      1,
      "the watchers' backlog first"
    )
    assertEquals(
      allowance(
        settings,
        AcquireRecheck.CheckedToday(0),
        Some(
          Shared(
            left = AcquireRecheck.Shared.Left(60),
            cap = AcquireRecheck.Shared.Cap(60),
            backlogPending = AcquireRecheck.Shared.BacklogPending(0),
            fetchedToday = AcquireRecheck.Shared.FetchedToday(14)
          )
        )
      ),
      1,
      "25 % of 60 = 15, 14 taken"
    )
    assertEquals(
      allowance(
        settings,
        AcquireRecheck.CheckedToday(0),
        Some(
          Shared(
            left = AcquireRecheck.Shared.Left(2),
            cap = AcquireRecheck.Shared.Cap(60),
            backlogPending = AcquireRecheck.Shared.BacklogPending(5),
            fetchedToday = AcquireRecheck.Shared.FetchedToday(0)
          )
        )
      ),
      0
    )
    assertEquals(
      allowance(
        settings.modify(_.sharePercent).setTo(AcquireRecheck.Settings.SharePercent(0)),
        AcquireRecheck.CheckedToday(0),
        Some(
          Shared(
            AcquireRecheck.Shared.Left(60),
            AcquireRecheck.Shared.Cap(60),
            AcquireRecheck.Shared.BacklogPending(0),
            AcquireRecheck.Shared.FetchedToday(0)
          )
        )
      ),
      0,
      "no share: nothing under a shared cap"
    )
    assertEquals(
      allowance(
        settings
          .modify(_.sharePercent)
          .setTo(AcquireRecheck.Settings.SharePercent(100))
          .modify(_.perPass)
          .setTo(AcquireRecheck.Settings.PerPass(100)),
        AcquireRecheck.CheckedToday(0),
        Some(
          Shared(
            AcquireRecheck.Shared.Left(60),
            AcquireRecheck.Shared.Cap(60),
            AcquireRecheck.Shared.BacklogPending(0),
            AcquireRecheck.Shared.FetchedToday(0)
          )
        )
      ),
      40,
      "the day's cap still holds"
    )

  test("a pass is due once its minutes passed since the last"):
    assert(passDue(None, AcquireRecheck.Settings.PassMinutes(10), now))
    assert(!passDue(Some(now.minusSeconds(300)), AcquireRecheck.Settings.PassMinutes(10), now))
    assert(passDue(Some(now.minusSeconds(600)), AcquireRecheck.Settings.PassMinutes(10), now))

  test("a check that got no answer (a rate limit) is due again after retry-hours, not every-days"):
    val failed = LastCheck(now.minusSeconds(2L * 3600L), Gen, failed = AcquireRecheck.LastCheck.Failed(true))
    assert(
      !isDue(Some(failed), Gen, AcquireRecheck.IsDueEveryDays(7), AcquireRecheck.IsDueRetryHours(6), now),
      "two hours ago: not yet"
    )
    assert(
      isDue(
        Some(failed.copy(at = now.minusSeconds(6L * 3600L))),
        Gen,
        AcquireRecheck.IsDueEveryDays(7),
        AcquireRecheck.IsDueRetryHours(6),
        now
      ),
      "six hours ago: due"
    )
    assertEquals(
      dueAt(Some(failed), Gen, AcquireRecheck.Settings.EveryDays(7), AcquireRecheck.Settings.RetryHours(6)),
      Some(failed.at.plusSeconds(6L * 3600L))
    )
    assertEquals(
      dueAt(Some(failed), Gen, AcquireRecheck.Settings.EveryDays(7), AcquireRecheck.Settings.RetryHours(0)),
      Some(failed.at.plusSeconds(3600L)),
      "a retry under an hour is an hour"
    )
    assertEquals(
      nextDue(
        List(LastCheck(daysAgo(1), Gen), failed),
        Gen,
        AcquireRecheck.NextDueEveryDays(7),
        AcquireRecheck.NextDueRetryHours(6)
      ),
      Some(failed.at.plusSeconds(6L * 3600L))
    )

  test("the order items are tried in: the never-checked first, then the oldest check, then the item id"):
    val cs = List(
      Candidate(AcquireRecheck.Candidate.Item(5), Some(LastCheck(daysAgo(1), Gen))),
      Candidate(AcquireRecheck.Candidate.Item(9), None),
      Candidate(AcquireRecheck.Candidate.Item(2), Some(LastCheck(daysAgo(3), Gen))),
      Candidate(AcquireRecheck.Candidate.Item(4), None),
      Candidate(AcquireRecheck.Candidate.Item(1), Some(LastCheck(daysAgo(1), Gen)))
    )
    assertEquals(order(cs).map(_.item), List(4, 9, 2, 1, 5))

  test("a job over every item: all of them, due or not; resumed, it skips the ones checked since it started"):
    val since = now.minusSeconds(3600L)
    val cs = List(
      Candidate(AcquireRecheck.Candidate.Item(1), None),
      Candidate(AcquireRecheck.Candidate.Item(2), Some(LastCheck(daysAgo(1), Gen))),
      Candidate(AcquireRecheck.Candidate.Item(3), Some(LastCheck(now.minusSeconds(60), Gen))),
      Candidate(
        AcquireRecheck.Candidate.Item(4),
        Some(LastCheck(since, Gen, failed = AcquireRecheck.LastCheck.Failed(true)))
      ),
      Candidate(AcquireRecheck.Candidate.Item(5), Some(LastCheck(daysAgo(30), Gen)))
    )
    val notDue = (c: Candidate) => false
    assertEquals(
      jobPicks(
        JobPlan(all = AcquireRecheck.JobPlan.All(true), AcquireRecheck.JobPlan.UpTo(0), now),
        cs,
        AcquireRecheck.Tally.Tried(0),
        notDue
      ).map(_.item),
      List(1, 5, 2, 4, 3),
      "a new job: every item, none of them due"
    )
    assertEquals(
      jobPicks(
        JobPlan(all = AcquireRecheck.JobPlan.All(true), AcquireRecheck.JobPlan.UpTo(0), since),
        cs,
        AcquireRecheck.Tally.Tried(2),
        notDue
      ).map(_.item),
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
    val cs = (1 to 6).toList.map(i => Candidate(Candidate.Item(i), if i == 6 then Some(LastCheck(now, Gen)) else None))
    val due = (c: Candidate) => c.item.value != 2
    assertEquals(
      jobPicks(
        JobPlan(all = AcquireRecheck.JobPlan.All(false), AcquireRecheck.JobPlan.UpTo(4), since),
        cs,
        AcquireRecheck.Tally.Tried(0),
        due
      ).map(_.item),
      List(1, 3, 4, 5)
    )
    assertEquals(
      jobPicks(
        JobPlan(all = AcquireRecheck.JobPlan.All(false), AcquireRecheck.JobPlan.UpTo(4), since),
        cs,
        AcquireRecheck.Tally.Tried(3),
        due
      ).map(_.item),
      List(1),
      "resumed after 3 tries"
    )
    assertEquals(
      jobPicks(
        JobPlan(all = AcquireRecheck.JobPlan.All(false), AcquireRecheck.JobPlan.UpTo(4), since),
        cs,
        AcquireRecheck.Tally.Tried(9),
        due
      ),
      Nil
    )

  test("the job's progress line counts what it found, with the last no-answer's reason, shortened"):
    val t = Tally(total = AcquireRecheck.Tally.Total(453))
      .add(Outcome.Closed, AcquireRecheck.Detail("a2020_x"))
      .add(Outcome.Fetched, AcquireRecheck.Detail("unpaywall"))
      .add(Outcome.NoCopy, Detail("none"))
      .add(
        Outcome.Failed,
        Detail("HTTP 429 Too Many Requests from api.example.invalid — retry after 3600 seconds, said the upstream")
      )
    assertEquals(t.tried, 3)
    assertEquals(
      progressLine(t),
      "3/453 items · 1 closed · 1 fetched · 1 no legal copy · 1 failed (HTTP 429 Too Many Requests from api.example.invalid — retry …)"
    )
    assertEquals(
      progressLine(Tally(total = AcquireRecheck.Tally.Total(2))),
      "0/2 items · 0 closed · 0 fetched · 0 no legal copy · 0 failed"
    )
