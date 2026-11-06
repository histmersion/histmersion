package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given

import com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.ManagerPolicy.*
import com.histmersion.api.automation.Managers.*

import java.time.Instant

/** The process managers' pure policy (docs/research-process-managers.md §3.4): the one-run items' rules, the backoff,
  * the dispatch order and fairness, capacity with pools, holds, hints, the cron and the daily cap.
  */
class ManagerPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-15T12:00:00Z")
  private val s = ReviewSettings(
    maxRuns = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2),
    maxOpen = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4),
    rounds = com.histmersion.domain.ManagerPolicy.ReviewSettings.Rounds(3),
    maxRunsPerArticle = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRunsPerArticle(3),
    noProgressRuns = com.histmersion.domain.ManagerPolicy.ReviewSettings.NoProgressRuns(2),
    reopenLimit = com.histmersion.domain.ManagerPolicy.ReviewSettings.ReopenLimit(2),
    cooldownMinutes = com.histmersion.domain.ManagerPolicy.ReviewSettings.CooldownMinutes(30),
    lowWater = com.histmersion.domain.ManagerPolicy.ReviewSettings.LowWater(2),
    refillBatch = com.histmersion.domain.ManagerPolicy.ReviewSettings.RefillBatch(10),
    maxFailures = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(3),
    backoffMinutes = com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15),
    maxBackoffMinutes = com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360)
  )

  test("dispatch order: priority, then the earliest not-before, then the oldest"):
    val cs = List(
      Candidate(com.histmersion.domain.ManagerPolicy.Candidate.Id(3), com.histmersion.domain.ManagerPolicy.Candidate.Target("c"), com.histmersion.domain.ManagerPolicy.Candidate.Priority(0), None),
      Candidate(com.histmersion.domain.ManagerPolicy.Candidate.Id(1), com.histmersion.domain.ManagerPolicy.Candidate.Target("a"), com.histmersion.domain.ManagerPolicy.Candidate.Priority(0), Some(now)),
      Candidate(com.histmersion.domain.ManagerPolicy.Candidate.Id(2), com.histmersion.domain.ManagerPolicy.Candidate.Target("b"), com.histmersion.domain.ManagerPolicy.Candidate.Priority(10), None),
      Candidate(com.histmersion.domain.ManagerPolicy.Candidate.Id(4), com.histmersion.domain.ManagerPolicy.Candidate.Target("d"), com.histmersion.domain.ManagerPolicy.Candidate.Priority(0), Some(now.minusSeconds(60)))
    )
    assertEquals(order(cs).map(_.target), List("b", "c", "d", "a"))
    assert(!due(Candidate(com.histmersion.domain.ManagerPolicy.Candidate.Id(5), com.histmersion.domain.ManagerPolicy.Candidate.Target("e"), com.histmersion.domain.ManagerPolicy.Candidate.Priority(0), Some(now.plusSeconds(1))), now))
    assert(due(Candidate(com.histmersion.domain.ManagerPolicy.Candidate.Id(5), com.histmersion.domain.ManagerPolicy.Candidate.Target("e"), com.histmersion.domain.ManagerPolicy.Candidate.Priority(0), Some(now)), now))

  test("capacity: the global room, the manager's runs and open runs, the pools"):
    assertEquals(capacity(com.histmersion.domain.ManagerPolicy.MaxConcurrent(4), com.histmersion.domain.ManagerPolicy.ActiveAll(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2), com.histmersion.domain.ManagerPolicy.MineRunning(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4), com.histmersion.domain.ManagerPolicy.MineOpen(0), None), 2)
    assertEquals(capacity(com.histmersion.domain.ManagerPolicy.MaxConcurrent(4), com.histmersion.domain.ManagerPolicy.ActiveAll(4), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2), com.histmersion.domain.ManagerPolicy.MineRunning(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4), com.histmersion.domain.ManagerPolicy.MineOpen(0), None), 0) // a person's runs fill the server
    assertEquals(capacity(com.histmersion.domain.ManagerPolicy.MaxConcurrent(4), com.histmersion.domain.ManagerPolicy.ActiveAll(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2), com.histmersion.domain.ManagerPolicy.MineRunning(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4), com.histmersion.domain.ManagerPolicy.MineOpen(1), None), 1)
    assertEquals(capacity(com.histmersion.domain.ManagerPolicy.MaxConcurrent(4), com.histmersion.domain.ManagerPolicy.ActiveAll(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2), com.histmersion.domain.ManagerPolicy.MineRunning(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4), com.histmersion.domain.ManagerPolicy.MineOpen(4), None), 0) // four of its runs paused or waiting
    assertEquals(capacity(com.histmersion.domain.ManagerPolicy.MaxConcurrent(4), com.histmersion.domain.ManagerPolicy.ActiveAll(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2), com.histmersion.domain.ManagerPolicy.MineRunning(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4), com.histmersion.domain.ManagerPolicy.MineOpen(0), Some(com.histmersion.domain.ManagerPolicy.PoolCap(1))), 1) // one plan call at a time
    assertEquals(capacity(com.histmersion.domain.ManagerPolicy.MaxConcurrent(1), com.histmersion.domain.ManagerPolicy.ActiveAll(3), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRuns(2), com.histmersion.domain.ManagerPolicy.MineRunning(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen(4), com.histmersion.domain.ManagerPolicy.MineOpen(0), None), 0) // never negative

  test("round robin: one each in turn, within each cap and the room"):
    val rr = roundRobin(List(("a", com.histmersion.domain.ManagerPolicy.Quota(2), List(1, 2, 3)), ("b", com.histmersion.domain.ManagerPolicy.Quota(5), List(10, 20)), ("c", com.histmersion.domain.ManagerPolicy.Quota(0), List(100))), com.histmersion.domain.ManagerPolicy.Room(4))
    assertEquals(rr, List("a" -> 1, "b" -> 10, "a" -> 2, "b" -> 20))
    assertEquals(roundRobin(List(("a", com.histmersion.domain.ManagerPolicy.Quota(2), List(1, 2, 3))), com.histmersion.domain.ManagerPolicy.Room(0)), Nil)

  test("holds: a date that passed, a source that exists; neither waits for a person"):
    assert(released(Hold(until = Some(com.histmersion.api.automation.Managers.Hold.Until("2026-10-15"))), now, _ => false))
    assert(!released(Hold(until = Some(com.histmersion.api.automation.Managers.Hold.Until("2026-10-16T00:00:00Z"))), now, _ => false))
    assert(released(Hold(untilSource = Some(com.histmersion.api.automation.Managers.Hold.UntilSource("10.1/x"))), now, _.value == "10.1/x"))
    assert(!released(Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("person")), now, _ => true))

  test("hint effects match by target, tag, topic or goal; notes are live and in scope"):
    val e = HintEffect(HintEffect.Action(HintEffect.Prioritise), tag = Some(HintEffect.Tag("Tin Trade")))
    assert(matches(e, com.histmersion.domain.ManagerPolicy.Candidate.Target("x"), List(com.histmersion.domain.ManagerPolicy.Tag("tin trade")), Nil, Nil))
    assert(!matches(e, com.histmersion.domain.ManagerPolicy.Candidate.Target("x"), List(com.histmersion.domain.ManagerPolicy.Tag("copper")), Nil, Nil))
    assert(matches(HintEffect(HintEffect.Action(HintEffect.Skip), targets = List(com.histmersion.api.automation.Managers.HintEffect.Targets("tin"))), com.histmersion.domain.ManagerPolicy.Candidate.Target("TIN"), Nil, Nil, Nil))
    assert(matches(HintEffect(HintEffect.Action(HintEffect.HoldAction), goal = Some(com.histmersion.api.automation.Goal.Slug("g"))), com.histmersion.domain.ManagerPolicy.Candidate.Target("x"), Nil, Nil, List(com.histmersion.domain.automation.Goal.Slug("g"))))
    val hs = List(
      ManagerHint(com.histmersion.api.automation.Managers.ManagerHint.Id(2), com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), None, com.histmersion.api.automation.Managers.ManagerHint.Text("b")),
      ManagerHint(com.histmersion.api.automation.Managers.ManagerHint.Id(1), com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), Some(com.histmersion.api.automation.Managers.ManagerHint.ItemId(7)), com.histmersion.api.automation.Managers.ManagerHint.Text("a")),
      ManagerHint(com.histmersion.api.automation.Managers.ManagerHint.Id(3), com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), Some(com.histmersion.api.automation.Managers.ManagerHint.ItemId(8)), com.histmersion.api.automation.Managers.ManagerHint.Text("other item")),
      ManagerHint(com.histmersion.api.automation.Managers.ManagerHint.Id(4), com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), None, com.histmersion.api.automation.Managers.ManagerHint.Text("gone"), retiredAt = Some(com.histmersion.api.automation.Managers.ManagerHint.RetiredAt("x")))
    )
    assertEquals(notesFor(hs, com.histmersion.domain.ManagerPolicy.ItemId(7)).map(_.text), List("a", "b"))

  test("status line: paused, starved, at capacity, pending, idle"):
    assertEquals(statusNote(com.histmersion.domain.automation.Manager.State.Paused, None, com.histmersion.domain.ManagerPolicy.Capacity(0), com.histmersion.domain.ManagerPolicy.PendingCount(3), com.histmersion.domain.ManagerPolicy.DueCount(3), com.histmersion.domain.ManagerPolicy.InFlightCount(0), None), "paused")
    assertEquals(
      statusNote(com.histmersion.domain.automation.Manager.State.Active, Some(com.histmersion.domain.ManagerPolicy.StatusNote("skeptic chain below its plan floor")), com.histmersion.domain.ManagerPolicy.Capacity(0), com.histmersion.domain.ManagerPolicy.PendingCount(3), com.histmersion.domain.ManagerPolicy.DueCount(3), com.histmersion.domain.ManagerPolicy.InFlightCount(0), None),
      "starved: skeptic chain below its plan floor"
    )
    assert(statusNote(com.histmersion.domain.automation.Manager.State.Active, None, com.histmersion.domain.ManagerPolicy.Capacity(0), com.histmersion.domain.ManagerPolicy.PendingCount(3), com.histmersion.domain.ManagerPolicy.DueCount(3), com.histmersion.domain.ManagerPolicy.InFlightCount(2), None).value.startsWith("at capacity"))
    assertEquals(statusNote(com.histmersion.domain.automation.Manager.State.Active, None, com.histmersion.domain.ManagerPolicy.Capacity(0), com.histmersion.domain.ManagerPolicy.PendingCount(0), com.histmersion.domain.ManagerPolicy.DueCount(0), com.histmersion.domain.ManagerPolicy.InFlightCount(0), None), "idle")
    assertEquals(statusNote(com.histmersion.domain.automation.Manager.State.Active, None, com.histmersion.domain.ManagerPolicy.Capacity(1), com.histmersion.domain.ManagerPolicy.PendingCount(3), com.histmersion.domain.ManagerPolicy.DueCount(0), com.histmersion.domain.ManagerPolicy.InFlightCount(0), Some(com.histmersion.domain.ManagerPolicy.BlockReason("budget: x"))), "budget: x")

  test("config: k=v pairs parse; a typo is an error; settings resolve over the defaults"):
    val c = ManagerConfig.parse(
      List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("max-runs=3"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("rounds=4"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("tags=a, b"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("models=fixer=claude-code:opus; skeptic=codex:gpt"))
    )
    assertEquals(c.map(_.maxRuns), Right(Some(3)))
    assertEquals(c.map(_.includeTags), Right(Some(List("a", "b"))))
    assertEquals(c.map(_.models.map(_.map(_.role))), Right(Some(List("fixer", "skeptic"))))
    assert(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("maxRun=3"))).isLeft)
    assert(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("max-runs=x"))).isLeft)

  // ── phase 2 ───────────────────────────────────────────────────────────────────────────────────

  test("waitsFor: an article's review waits while its infer-filter item is open; nothing else waits"):
    val open = List(ItemKind.InferFilter -> Candidate.Target("a"), ItemKind.ArticleReview -> Candidate.Target("a"), ItemKind.ArticleReview -> Candidate.Target("b"))
    assert(waitsFor(ItemKind.ArticleReview, com.histmersion.domain.ManagerPolicy.Candidate.Target("a"), open))
    assert(!waitsFor(ItemKind.ArticleReview, com.histmersion.domain.ManagerPolicy.Candidate.Target("b"), open))
    assert(!waitsFor(ItemKind.InferFilter, com.histmersion.domain.ManagerPolicy.Candidate.Target("a"), open))

  test("evaluateStep: done → finished, held for an approval, or stopped; failed backs off then stops; cancelled stops"):
    def ev(st: RunState, f: StepFacts, failures: Next.Retry.Failures = Next.Retry.Failures(0)) =
      evaluateStep(s, StepLabel(InferFilter), com.histmersion.domain.ManagerPolicy.Candidate.Target("tin"), failures, ItemState(), com.histmersion.domain.automation.Run.Id("ccf9dbcf-a45c-e9ef-aef8-3e7aff5d2a57"), st, Some(com.histmersion.domain.ManagerPolicy.Problem("boom")), f, now)
    assertEquals(ev(com.histmersion.domain.automation.Run.State.Done, StepFacts.Finished(com.histmersion.domain.ManagerPolicy.ItemOutcome("ok"))), Next.Done(com.histmersion.domain.ManagerPolicy.ItemOutcome("ok"), ItemState()))
    assertEquals(ev(com.histmersion.domain.automation.Run.State.Done, StepFacts.Waiting(com.histmersion.domain.ManagerPolicy.ApprovalId(7), com.histmersion.domain.ManagerPolicy.ItemNote("waits"))), Next.Wait(com.histmersion.domain.ManagerPolicy.ApprovalId(7), com.histmersion.domain.ManagerPolicy.ItemNote("waits")))
    assertEquals(ev(com.histmersion.domain.automation.Run.State.Done, StepFacts.NotDone(com.histmersion.domain.ManagerPolicy.ItemOutcome("no"), None)), Next.Stop(com.histmersion.domain.ManagerPolicy.ItemOutcome("no"), None, ItemState()))
    ev(com.histmersion.domain.automation.Run.State.Failed, StepFacts.Finished(com.histmersion.domain.ManagerPolicy.ItemOutcome("x"))) match
      case Next.Retry(nb, failures, _) if failures == Next.Retry.Failures(1) => assertEquals(nb, now.plusSeconds(15 * 60))
      case other                => fail(other.toString)
    ev(com.histmersion.domain.automation.Run.State.Failed, StepFacts.Finished(com.histmersion.domain.ManagerPolicy.ItemOutcome("x")), failures = Next.Retry.Failures(2)) match
      case Next.Stop(o, Some(t), _) => assert(o.value.contains("3 failed runs") && t.value.contains("[[tin]]"), t)
      case other                    => fail(other.toString)
    assert(ev(com.histmersion.domain.automation.Run.State.Cancelled, StepFacts.Finished(com.histmersion.domain.ManagerPolicy.ItemOutcome("x"))).isInstanceOf[Next.Stop])

  test("decided: an open approval is not decided; a decided one reads with its result"):
    assertEquals(decided(com.histmersion.domain.automation.Approval.State.Wire("open"), None), None)
    assertEquals(decided(com.histmersion.domain.automation.Approval.State.Wire("approved"), Some(com.histmersion.domain.automation.Approval.Outcome("applied: x"))), Some("approved: applied: x"))
    assertEquals(decided(com.histmersion.domain.automation.Approval.State.Wire("rejected"), None), Some("rejected"))

  test("settings by kind: enrich and seed take their own max-runs; seed's defaults show the plan's checks"):
    val m = ManagerDefaults(
      com.histmersion.domain.ManagerDefaults.MaxFailures(3),
      com.histmersion.domain.ManagerDefaults.BackoffMinutes(15),
      com.histmersion.domain.ManagerDefaults.MaxBackoffMinutes(360),
      com.histmersion.domain.ManagerDefaults.DailyUsd(0),
      ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(2)),
      ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(4)),
      ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(1)),
      ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(1))
    )
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Enrich, ManagerConfig(), m).maxRuns, 4)
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Seed, ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(2))), m).maxRuns, 2)
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Review, ManagerConfig(), m).maxRuns, 2)
    val sd = SeedPlanDefaults(maxArticles = com.histmersion.domain.SeedPlanDefaults.MaxArticles(6), minSources = com.histmersion.domain.SeedPlanDefaults.MinSources(5)).managerDefaults(m)
    assertEquals((sd.maxRuns, sd.minSources, sd.maxArticles), (Some(1), Some(5), Some(6)))
    val c = ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("stale-after-days=30"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("infer-filter=off"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("min-sources=3"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("max-articles=4")))
    assertEquals(
      c.map(x => (x.staleAfterDays, x.inferFilter, x.minSources, x.maxArticles)),
      Right((Some(30), Some(false), Some(3), Some(4)))
    )
    assert(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("infer-filter=maybe"))).isLeft)

  test("item kinds and the enqueue words"):
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, com.histmersion.api.ItemKind.Word("")), Some(ArticleReview))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, com.histmersion.api.ItemKind.Word("extend")), Some(ArticleExtend))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Seed, com.histmersion.api.ItemKind.Word("propose")), Some(ProposeArticles))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Enrich, com.histmersion.api.ItemKind.Word("review")), None)
    assertEquals(EnqueueKind.parse(com.histmersion.api.automation.Managers.EnqueueKind.Wire("source-enrich")), Some(EnqueueKind.Enrich))
    assertEquals(EnqueueKind.parse(com.histmersion.api.automation.Managers.EnqueueKind.Wire("")), Some(EnqueueKind.Review))
    assertEquals(EnqueueKind.parse(com.histmersion.api.automation.Managers.EnqueueKind.Wire("nope")), None)

  // ── phase 3: the watcher and the cron ──────────────────────────────────────────────────────────

  private val watch = WatchManagerDefaults(maxRuns = com.histmersion.domain.WatchManagerDefaults.MaxRuns(1), autoIngestPerDay = com.histmersion.domain.WatchManagerDefaults.AutoIngestPerDay(5))
  private val mcfg = ManagerDefaults(
    com.histmersion.domain.ManagerDefaults.MaxFailures(3),
    com.histmersion.domain.ManagerDefaults.BackoffMinutes(15),
    com.histmersion.domain.ManagerDefaults.MaxBackoffMinutes(360),
    com.histmersion.domain.ManagerDefaults.DailyUsd(0),
    ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(2)),
    ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(4)),
    ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(1)),
    ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(watch.maxRuns.value))
  )

  test("watcher: its item kinds and words, its sweeps (none named = both), its settings and defaults"):
    assertEquals(itemKinds(com.histmersion.api.automation.Manager.Kind.Watch), List(AcquisitionSweep, CurrencyCheck, OaIngest))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("")), Some(AcquisitionSweep))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("backlog")), Some(OaIngest))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, com.histmersion.api.ItemKind.Word("ingest")), None)
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("currency")), Some(CurrencyCheck))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("review")), None)
    assertEquals(watchId(com.histmersion.api.automation.Goal.Ref(" tin ")), "watch:tin")
    assertEquals(Sweep.of(ManagerConfig()), List(Sweep.Acquisition, Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(List(com.histmersion.api.automation.Managers.Sweep.Wire("currency"))))), List(Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(Nil))), Nil)
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Watch, ManagerConfig(), mcfg).maxRuns, 1)
    val d = watch.managerDefaults(mcfg)
    assertEquals(
      (d.autoIngest, d.autoIngestPerDay, d.sweeps),
      (Some(false), Some(5), Some(List("acquisition", "currency")))
    )
    val c = ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("sweeps=acquisition-sweep, currency"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("auto-ingest=on"), com.histmersion.api.automation.Managers.ManagerConfig.Spec("auto-ingest-per-day=3")))
    assertEquals(
      c.map(x => (x.sweeps, x.autoIngest, x.autoIngestPerDay)),
      Right((Some(List("acquisition", "currency")), Some(true), Some(3)))
    )
    assert(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("sweeps=weekly"))).left.exists(_.value.contains("not a sweep")))
    assert(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("auto-ingest=perhaps"))).isLeft)

  test("auto-ingest is opt-in: off by default; on, what is left of the daily cap (the watcher's own cap wins)"):
    assertEquals(watch.ingestAllowance(ManagerConfig(), com.histmersion.domain.WatchManagerDefaults.IngestedToday(0)), 0)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(false))), com.histmersion.domain.WatchManagerDefaults.IngestedToday(0)), 0)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(true))), com.histmersion.domain.WatchManagerDefaults.IngestedToday(0)), 5)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(true))), com.histmersion.domain.WatchManagerDefaults.IngestedToday(4)), 1)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(true))), com.histmersion.domain.WatchManagerDefaults.IngestedToday(9)), 0)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(true)), autoIngestPerDay = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngestPerDay(2))), com.histmersion.domain.WatchManagerDefaults.IngestedToday(1)), 1)
    assertEquals(dayStart(Instant.parse("2026-10-17T23:59:00Z")), Instant.parse("2026-10-17T00:00:00Z"))

  test("cron: not scheduled → its first firing recorded; due → fire once and move on; a missed firing fires once"):
    val weekly = Cron.parse(com.histmersion.domain.Cron.Text("@weekly")).toOption
    val sat = Instant.parse("2026-10-17T12:00:00Z")
    val sun = Instant.parse("2026-10-18T00:00:00Z")
    assertEquals(cronStep(None, None, sat), CronStep.Idle)
    assertEquals(cronStep(weekly, None, sat), CronStep.Schedule(Some(sun)))
    assertEquals(cronStep(weekly, Some(sun), sat), CronStep.Idle)
    assertEquals(cronStep(weekly, Some(sun), sun), CronStep.Fire(Some(Instant.parse("2026-10-25T00:00:00Z"))))
    // paused for three weeks: one firing now, the next from now — not three
    assertEquals(
      cronStep(weekly, Some(sun), Instant.parse("2026-11-10T09:00:00Z")),
      CronStep.Fire(Some(Instant.parse("2026-11-15T00:00:00Z")))
    )

  test("checkCron: it parses and fires no more often than the minimum interval"):
    assert(checkCron(com.histmersion.domain.Cron.Text("not cron"), now, com.histmersion.domain.ManagerPolicy.MinIntervalMinutes(60)).left.exists(_.value.contains("five fields")))
    assert(checkCron(com.histmersion.domain.Cron.Text("*/5 * * * *"), now, com.histmersion.domain.ManagerPolicy.MinIntervalMinutes(60)).left.exists(_.value.contains("min-interval")))
    assertEquals(checkCron(com.histmersion.domain.Cron.Text("@daily"), now, com.histmersion.domain.ManagerPolicy.MinIntervalMinutes(60)).map(_.text), Right("@daily"))

  test("daily cap: its own, else agents.managers.daily-usd (0 = none); blocks once the metered spend reaches it"):
    assertEquals(dailyCap(ManagerConfig(), mcfg), None)
    assertEquals(dailyCap(ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(2.0))), mcfg), Some(2.0))
    assertEquals(
      dailyCap(ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(0.0))), mcfg.modify(_.dailyUsd).setTo(com.histmersion.domain.ManagerDefaults.DailyUsd(5.0))),
      None,
      "0 on the manager = no cap"
    )
    assertEquals(dailyCap(ManagerConfig(), mcfg.modify(_.dailyUsd).setTo(com.histmersion.domain.ManagerDefaults.DailyUsd(5.0))), Some(5.0))
    assertEquals(dailyBlock(None, BigDecimal(100)), None)
    assertEquals(dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal("1.99")), None)
    assert(
      dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal(2)).exists(_.value.contains("metered calls only")),
      dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal(2))
    )
    assertEquals(
      ReviewSettings
        .defaultsFor(com.histmersion.domain.automation.Manager.Kind.Enrich, mcfg.modify(_.dailyUsd).setTo(com.histmersion.domain.ManagerDefaults.DailyUsd(5.0)))
        .dailyUsd,
      Some(5.0)
    )
    assertEquals(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("daily-usd=1.5"))).map(_.dailyUsd), Right(Some(1.5)))
    assert(ManagerConfig.parse(List(com.histmersion.api.automation.Managers.ManagerConfig.Spec("daily-usd=-1"))).isLeft)
