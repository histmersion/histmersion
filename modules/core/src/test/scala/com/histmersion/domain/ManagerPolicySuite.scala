package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given

import com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.ManagerPolicy.*
import com.histmersion.api.automation.Manager as ApiManager
import com.histmersion.api.automation.Managers.*

import java.time.Instant
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.Approval

/** The process managers' pure policy (docs/research-process-managers.md §3.4): the one-run items' rules, the backoff,
  * the dispatch order and fairness, capacity with pools, holds, hints, the cron and the daily cap.
  */
class ManagerPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-15T12:00:00Z")
  private val s = ReviewSettings(
    maxRuns = ManagerPolicy.ReviewSettings.MaxRuns(2),
    maxOpen = ManagerPolicy.ReviewSettings.MaxOpen(4),
    rounds = ManagerPolicy.ReviewSettings.Rounds(3),
    maxRunsPerArticle = ManagerPolicy.ReviewSettings.MaxRunsPerArticle(3),
    noProgressRuns = ManagerPolicy.ReviewSettings.NoProgressRuns(2),
    reopenLimit = ManagerPolicy.ReviewSettings.ReopenLimit(2),
    cooldownMinutes = ManagerPolicy.ReviewSettings.CooldownMinutes(30),
    lowWater = ManagerPolicy.ReviewSettings.LowWater(2),
    refillBatch = ManagerPolicy.ReviewSettings.RefillBatch(10),
    maxFailures = ManagerPolicy.ReviewSettings.MaxFailures(3),
    backoffMinutes = ManagerPolicy.ReviewSettings.BackoffMinutes(15),
    maxBackoffMinutes = ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360)
  )

  test("dispatch order: priority, then the earliest not-before, then the oldest"):
    val cs = List(
      Candidate(
        ManagerPolicy.Candidate.Id(3),
        ManagerPolicy.Candidate.Target("c"),
        ManagerPolicy.Candidate.Priority(0),
        None
      ),
      Candidate(
        ManagerPolicy.Candidate.Id(1),
        ManagerPolicy.Candidate.Target("a"),
        ManagerPolicy.Candidate.Priority(0),
        Some(now)
      ),
      Candidate(
        ManagerPolicy.Candidate.Id(2),
        ManagerPolicy.Candidate.Target("b"),
        ManagerPolicy.Candidate.Priority(10),
        None
      ),
      Candidate(
        ManagerPolicy.Candidate.Id(4),
        ManagerPolicy.Candidate.Target("d"),
        ManagerPolicy.Candidate.Priority(0),
        Some(now.minusSeconds(60))
      )
    )
    assertEquals(order(cs).map(_.target), List("b", "c", "d", "a"))
    assert(
      !due(
        Candidate(
          ManagerPolicy.Candidate.Id(5),
          ManagerPolicy.Candidate.Target("e"),
          ManagerPolicy.Candidate.Priority(0),
          Some(now.plusSeconds(1))
        ),
        now
      )
    )
    assert(
      due(
        Candidate(
          ManagerPolicy.Candidate.Id(5),
          ManagerPolicy.Candidate.Target("e"),
          ManagerPolicy.Candidate.Priority(0),
          Some(now)
        ),
        now
      )
    )

  test("capacity: the global room, the manager's runs and open runs, the pools"):
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(1),
        ManagerPolicy.ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ManagerPolicy.ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        None
      ),
      2
    )
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(4),
        ManagerPolicy.ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ManagerPolicy.ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        None
      ),
      0
    ) // a person's runs fill the server
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(1),
        ManagerPolicy.ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(1),
        ManagerPolicy.ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(1),
        None
      ),
      1
    )
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(1),
        ManagerPolicy.ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ManagerPolicy.ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(4),
        None
      ),
      0
    ) // four of its runs paused or waiting
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(0),
        ManagerPolicy.ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ManagerPolicy.ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        Some(ManagerPolicy.PoolCap(1))
      ),
      1
    ) // one plan call at a time
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(1),
        ManagerPolicy.ActiveAll(3),
        ManagerPolicy.ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ManagerPolicy.ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        None
      ),
      0
    ) // never negative

  test("round robin: one each in turn, within each cap and the room"):
    val rr = roundRobin(
      List(
        ("a", ManagerPolicy.Quota(2), List(1, 2, 3)),
        ("b", ManagerPolicy.Quota(5), List(10, 20)),
        ("c", ManagerPolicy.Quota(0), List(100))
      ),
      ManagerPolicy.Room(4)
    )
    assertEquals(rr, List("a" -> 1, "b" -> 10, "a" -> 2, "b" -> 20))
    assertEquals(roundRobin(List(("a", ManagerPolicy.Quota(2), List(1, 2, 3))), ManagerPolicy.Room(0)), Nil)

  test("holds: a date that passed, a source that exists; neither waits for a person"):
    assert(released(Hold(until = Some(Managers.Hold.Until("2026-10-15"))), now, _ => false))
    assert(!released(Hold(until = Some(Managers.Hold.Until("2026-10-16T00:00:00Z"))), now, _ => false))
    assert(released(Hold(untilSource = Some(Managers.Hold.UntilSource("10.1/x"))), now, _.value == "10.1/x"))
    assert(!released(Hold(reason = api.Problem("person")), now, _ => true))

  test("hint effects match by target, tag, topic or goal; notes are live and in scope"):
    val e = HintEffect(HintEffect.Action(HintEffect.Prioritise), tag = Some(HintEffect.Tag("Tin Trade")))
    assert(matches(e, ManagerPolicy.Candidate.Target("x"), List(ManagerPolicy.Tag("tin trade")), Nil, Nil))
    assert(!matches(e, ManagerPolicy.Candidate.Target("x"), List(ManagerPolicy.Tag("copper")), Nil, Nil))
    assert(
      matches(
        HintEffect(HintEffect.Action(HintEffect.Skip), targets = List(Managers.HintEffect.Targets("tin"))),
        ManagerPolicy.Candidate.Target("TIN"),
        Nil,
        Nil,
        Nil
      )
    )
    assert(
      matches(
        HintEffect(HintEffect.Action(HintEffect.HoldAction), goal = Some(Goal.Slug("g"))),
        ManagerPolicy.Candidate.Target("x"),
        Nil,
        Nil,
        List(com.histmersion.domain.automation.Goal.Slug("g"))
      )
    )
    val hs = List(
      ManagerHint(Managers.ManagerHint.Id(2), Managers.ManagerInfo.Id("review"), None, Managers.ManagerHint.Text("b")),
      ManagerHint(
        Managers.ManagerHint.Id(1),
        Managers.ManagerInfo.Id("review"),
        Some(Managers.ManagerHint.ItemId(7)),
        Managers.ManagerHint.Text("a")
      ),
      ManagerHint(
        Managers.ManagerHint.Id(3),
        Managers.ManagerInfo.Id("review"),
        Some(Managers.ManagerHint.ItemId(8)),
        Managers.ManagerHint.Text("other item")
      ),
      ManagerHint(
        Managers.ManagerHint.Id(4),
        Managers.ManagerInfo.Id("review"),
        None,
        Managers.ManagerHint.Text("gone"),
        retiredAt = Some(Managers.ManagerHint.RetiredAt("x"))
      )
    )
    assertEquals(notesFor(hs, ManagerPolicy.ItemId(7)).map(_.text), List("a", "b"))

  test("status line: paused, starved, at capacity, pending, idle"):
    val chain = SchedulerPolicy.Block.Chain("skeptic chain below its plan floor")
    assertEquals(
      statusNote(
        Manager.State.Paused,
        None,
        ManagerPolicy.Capacity(0),
        ManagerPolicy.PendingCount(3),
        ManagerPolicy.DueCount(3),
        ManagerPolicy.InFlightCount(0)
      ).render,
      "paused"
    )
    assertEquals(
      statusNote(
        Manager.State.Active,
        Some(SchedulerPolicy.Block.Why.Starved(chain)),
        ManagerPolicy.Capacity(0),
        ManagerPolicy.PendingCount(3),
        ManagerPolicy.DueCount(3),
        ManagerPolicy.InFlightCount(0)
      ).render,
      "starved: skeptic chain below its plan floor"
    )
    assertEquals(
      statusNote(
        Manager.State.Active,
        None,
        ManagerPolicy.Capacity(0),
        ManagerPolicy.PendingCount(3),
        ManagerPolicy.DueCount(3),
        ManagerPolicy.InFlightCount(2)
      ).is,
      ApiManager.Status.Kind.AtCapacity
    )
    assertEquals(
      statusNote(
        Manager.State.Active,
        None,
        ManagerPolicy.Capacity(0),
        ManagerPolicy.PendingCount(0),
        ManagerPolicy.DueCount(0),
        ManagerPolicy.InFlightCount(0)
      ).render,
      "idle"
    )
    val capped = dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal(2))
    val budget = statusNote(
      Manager.State.Active,
      capped,
      ManagerPolicy.Capacity(1),
      ManagerPolicy.PendingCount(3),
      ManagerPolicy.DueCount(0),
      ManagerPolicy.InFlightCount(0)
    )
    assertEquals(budget.is, ApiManager.Status.Kind.Budget)
    assertEquals(Some(budget.render), capped.map(_.render))

  test("a status line is read back as it was written"):
    val lines = List(
      "stopped",
      "paused",
      "paused (2 runs in flight)",
      "idle",
      "idle; the scheduler: capacity — no place free",
      "at capacity (1 run in flight, 3 pending)",
      "budget: its spend today (2.00 USD, metered calls only) reached its daily-usd (2.00); it starts again tomorrow (UTC)",
      "starved: skeptic chain below its plan floor; frees ~09:00",
      "focus: its goal waits for a slot — idle",
      "3 pending, 1 run in flight",
      "",
      "anything else a person wrote"
    )
    lines.foreach(l => assertEquals(ApiManager.Status.parse(l).render, l))
    assertEquals(ApiManager.Status.parse("idle").is, ApiManager.Status.Kind.Idle)
    assertEquals(ApiManager.Status.parse("idle; the scheduler: x").is, ApiManager.Status.Kind.Other)
    assertEquals(ApiManager.Status.parse("starved: x").is, ApiManager.Status.Kind.Starved)

  test("config: k=v pairs parse; a typo is an error; settings resolve over the defaults"):
    val c = ManagerConfig.parse(
      List(
        Managers.ManagerConfig.Spec("max-runs=3"),
        Managers.ManagerConfig.Spec("rounds=4"),
        Managers.ManagerConfig.Spec("tags=a, b"),
        Managers.ManagerConfig.Spec("models=fixer=claude-code:opus; skeptic=codex:gpt")
      )
    )
    assertEquals(c.map(_.maxRuns), Right(Some(3)))
    assertEquals(c.map(_.includeTags), Right(Some(List("a", "b"))))
    assertEquals(c.map(_.models.map(_.map(_.role))), Right(Some(List("fixer", "skeptic"))))
    assert(ManagerConfig.parse(List(Managers.ManagerConfig.Spec("maxRun=3"))).isLeft)
    assert(ManagerConfig.parse(List(Managers.ManagerConfig.Spec("max-runs=x"))).isLeft)

  // ── phase 2 ───────────────────────────────────────────────────────────────────────────────────

  test("waitsFor: an article's review waits while its infer-filter item is open; nothing else waits"):
    val open = List(
      ItemKind.InferFilter -> Candidate.Target("a"),
      ItemKind.ArticleReview -> Candidate.Target("a"),
      ItemKind.ArticleReview -> Candidate.Target("b")
    )
    assert(waitsFor(ItemKind.ArticleReview, ManagerPolicy.Candidate.Target("a"), open))
    assert(!waitsFor(ItemKind.ArticleReview, ManagerPolicy.Candidate.Target("b"), open))
    assert(!waitsFor(ItemKind.InferFilter, ManagerPolicy.Candidate.Target("a"), open))

  test("evaluateStep: done → finished, held for an approval, or stopped; failed backs off then stops; cancelled stops"):
    def ev(st: RunState, f: StepFacts, failures: Next.Retry.Failures = Next.Retry.Failures(0)) =
      evaluateStep(
        s,
        StepLabel(InferFilter),
        ManagerPolicy.Candidate.Target("tin"),
        failures,
        ItemState(),
        Run.Id("ccf9dbcf-a45c-e9ef-aef8-3e7aff5d2a57"),
        st,
        Some(Problem("boom")),
        f,
        now
      )
    assertEquals(
      ev(Run.State.Done, StepFacts.Finished(ManagerPolicy.ItemOutcome("ok"))),
      Next.Done(ManagerPolicy.ItemOutcome("ok"), ItemState())
    )
    assertEquals(
      ev(Run.State.Done, StepFacts.Waiting(ManagerPolicy.ApprovalId(7), ManagerPolicy.ItemNote("waits"))),
      Next.Wait(ManagerPolicy.ApprovalId(7), ManagerPolicy.ItemNote("waits"))
    )
    assertEquals(
      ev(Run.State.Done, StepFacts.NotDone(ManagerPolicy.ItemOutcome("no"), None)),
      Next.Stop(ManagerPolicy.ItemOutcome("no"), None, ItemState())
    )
    ev(Run.State.Failed, StepFacts.Finished(ManagerPolicy.ItemOutcome("x"))) match
      case Next.Retry(nb, failures, _) if failures == Next.Retry.Failures(1) =>
        assertEquals(nb, now.plusSeconds(15 * 60))
      case other => fail(other.toString)
    ev(Run.State.Failed, StepFacts.Finished(ManagerPolicy.ItemOutcome("x")), failures = Next.Retry.Failures(2)) match
      case Next.Stop(o, Some(t), _) => assert(o.value.contains("3 failed runs") && t.value.contains("[[tin]]"), t)
      case other                    => fail(other.toString)
    assert(ev(Run.State.Cancelled, StepFacts.Finished(ManagerPolicy.ItemOutcome("x"))).isInstanceOf[Next.Stop])

  test("decided: an open approval is not decided; a decided one reads with its result"):
    assertEquals(decided(Approval.State.Wire("open"), None), None)
    assertEquals(
      decided(Approval.State.Wire("approved"), Some(Approval.Outcome("applied: x"))),
      Some("approved: applied: x")
    )
    assertEquals(decided(Approval.State.Wire("rejected"), None), Some("rejected"))

  test("settings by kind: enrich and seed take their own max-runs; seed's defaults show the plan's checks"):
    val m = ManagerDefaults(
      ManagerDefaults.MaxFailures(3),
      ManagerDefaults.BackoffMinutes(15),
      ManagerDefaults.MaxBackoffMinutes(360),
      ManagerDefaults.DailyUsd(0),
      ManagerRunDefaults(ManagerRunDefaults.MaxRuns(2)),
      ManagerRunDefaults(ManagerRunDefaults.MaxRuns(4)),
      ManagerRunDefaults(ManagerRunDefaults.MaxRuns(1)),
      ManagerRunDefaults(ManagerRunDefaults.MaxRuns(1))
    )
    assertEquals(ReviewSettings.forKind(Manager.Kind.Enrich, ManagerConfig(), m).maxRuns, 4)
    assertEquals(
      ReviewSettings
        .forKind(Manager.Kind.Seed, ManagerConfig(maxRuns = Some(Managers.ManagerConfig.MaxRuns(2))), m)
        .maxRuns,
      2
    )
    assertEquals(ReviewSettings.forKind(Manager.Kind.Review, ManagerConfig(), m).maxRuns, 2)
    val sd =
      SeedPlanDefaults(maxArticles = SeedPlanDefaults.MaxArticles(6), minSources = SeedPlanDefaults.MinSources(5))
        .managerDefaults(m)
    assertEquals((sd.maxRuns, sd.minSources, sd.maxArticles), (Some(1), Some(5), Some(6)))
    val c = ManagerConfig.parse(
      List(
        Managers.ManagerConfig.Spec("stale-after-days=30"),
        Managers.ManagerConfig.Spec("infer-filter=off"),
        Managers.ManagerConfig.Spec("min-sources=3"),
        Managers.ManagerConfig.Spec("max-articles=4")
      )
    )
    assertEquals(
      c.map(x => (x.staleAfterDays, x.inferFilter, x.minSources, x.maxArticles)),
      Right((Some(30), Some(false), Some(3), Some(4)))
    )
    assert(ManagerConfig.parse(List(Managers.ManagerConfig.Spec("infer-filter=maybe"))).isLeft)

  test("item kinds and the enqueue words"):
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, com.histmersion.api.ItemKind.Word("")),
      Some(ArticleReview)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, com.histmersion.api.ItemKind.Word("extend")),
      Some(ArticleExtend)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Seed, com.histmersion.api.ItemKind.Word("propose")),
      Some(ProposeArticles)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Enrich, com.histmersion.api.ItemKind.Word("review")),
      None
    )
    assertEquals(EnqueueKind.parse(Managers.EnqueueKind.Wire("source-enrich")), Some(EnqueueKind.Enrich))
    assertEquals(EnqueueKind.parse(Managers.EnqueueKind.Wire("")), Some(EnqueueKind.Review))
    assertEquals(EnqueueKind.parse(Managers.EnqueueKind.Wire("nope")), None)

  // ── phase 3: the watcher and the cron ──────────────────────────────────────────────────────────

  private val watch = WatchManagerDefaults(
    maxRuns = WatchManagerDefaults.MaxRuns(1),
    autoIngestPerDay = WatchManagerDefaults.AutoIngestPerDay(5)
  )
  private val mcfg = ManagerDefaults(
    ManagerDefaults.MaxFailures(3),
    ManagerDefaults.BackoffMinutes(15),
    ManagerDefaults.MaxBackoffMinutes(360),
    ManagerDefaults.DailyUsd(0),
    ManagerRunDefaults(ManagerRunDefaults.MaxRuns(2)),
    ManagerRunDefaults(ManagerRunDefaults.MaxRuns(4)),
    ManagerRunDefaults(ManagerRunDefaults.MaxRuns(1)),
    ManagerRunDefaults(ManagerRunDefaults.MaxRuns(watch.maxRuns.value))
  )

  test("watcher: its item kinds and words, its sweeps (none named = both), its settings and defaults"):
    assertEquals(
      itemKinds(com.histmersion.api.automation.Manager.Kind.Watch),
      List(AcquisitionSweep, CurrencyCheck, OaIngest)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("")),
      Some(AcquisitionSweep)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("backlog")),
      Some(OaIngest)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, com.histmersion.api.ItemKind.Word("ingest")),
      None
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("currency")),
      Some(CurrencyCheck)
    )
    assertEquals(
      itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, com.histmersion.api.ItemKind.Word("review")),
      None
    )
    assertEquals(watchId(Goal.Ref(" tin ")), "watch:tin")
    assertEquals(Sweep.of(ManagerConfig()), List(Sweep.Acquisition, Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(List(Managers.Sweep.Wire("currency"))))), List(Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(Nil))), Nil)
    assertEquals(ReviewSettings.forKind(Manager.Kind.Watch, ManagerConfig(), mcfg).maxRuns, 1)
    val d = watch.managerDefaults(mcfg)
    assertEquals(
      (d.autoIngest, d.autoIngestPerDay, d.sweeps),
      (Some(false), Some(5), Some(List("acquisition", "currency")))
    )
    val c = ManagerConfig.parse(
      List(
        Managers.ManagerConfig.Spec("sweeps=acquisition-sweep, currency"),
        Managers.ManagerConfig.Spec("auto-ingest=on"),
        Managers.ManagerConfig.Spec("auto-ingest-per-day=3")
      )
    )
    assertEquals(
      c.map(x => (x.sweeps, x.autoIngest, x.autoIngestPerDay)),
      Right((Some(List("acquisition", "currency")), Some(true), Some(3)))
    )
    assert(
      ManagerConfig
        .parse(List(Managers.ManagerConfig.Spec("sweeps=weekly")))
        .left
        .exists(_.value.contains("not a sweep"))
    )
    assert(ManagerConfig.parse(List(Managers.ManagerConfig.Spec("auto-ingest=perhaps"))).isLeft)

  test("auto-ingest is opt-in: off by default; on, what is left of the daily cap (the watcher's own cap wins)"):
    assertEquals(watch.ingestAllowance(ManagerConfig(), WatchManagerDefaults.IngestedToday(0)), 0)
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(Managers.ManagerConfig.AutoIngest(false))),
        WatchManagerDefaults.IngestedToday(0)
      ),
      0
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(Managers.ManagerConfig.AutoIngest(true))),
        WatchManagerDefaults.IngestedToday(0)
      ),
      5
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(Managers.ManagerConfig.AutoIngest(true))),
        WatchManagerDefaults.IngestedToday(4)
      ),
      1
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(Managers.ManagerConfig.AutoIngest(true))),
        WatchManagerDefaults.IngestedToday(9)
      ),
      0
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(
          autoIngest = Some(Managers.ManagerConfig.AutoIngest(true)),
          autoIngestPerDay = Some(Managers.ManagerConfig.AutoIngestPerDay(2))
        ),
        WatchManagerDefaults.IngestedToday(1)
      ),
      1
    )
    assertEquals(dayStart(Instant.parse("2026-10-17T23:59:00Z")), Instant.parse("2026-10-17T00:00:00Z"))

  test("cron: not scheduled → its first firing recorded; due → fire once and move on; a missed firing fires once"):
    val weekly = Cron.parse(Cron.Text("@weekly")).toOption
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
    assert(
      checkCron(Cron.Text("not cron"), now, ManagerPolicy.MinIntervalMinutes(60)).left
        .exists(_.value.contains("five fields"))
    )
    assert(
      checkCron(Cron.Text("*/5 * * * *"), now, ManagerPolicy.MinIntervalMinutes(60)).left
        .exists(_.value.contains("min-interval"))
    )
    assertEquals(checkCron(Cron.Text("@daily"), now, ManagerPolicy.MinIntervalMinutes(60)).map(_.text), Right("@daily"))

  test("daily cap: its own, else agents.managers.daily-usd (0 = none); blocks once the metered spend reaches it"):
    assertEquals(dailyCap(ManagerConfig(), mcfg), None)
    assertEquals(dailyCap(ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(2.0))), mcfg), Some(2.0))
    assertEquals(
      dailyCap(
        ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(0.0))),
        mcfg.modify(_.dailyUsd).setTo(ManagerDefaults.DailyUsd(5.0))
      ),
      None,
      "0 on the manager = no cap"
    )
    assertEquals(dailyCap(ManagerConfig(), mcfg.modify(_.dailyUsd).setTo(ManagerDefaults.DailyUsd(5.0))), Some(5.0))
    assertEquals(dailyBlock(None, BigDecimal(100)), None)
    assertEquals(dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal("1.99")), None)
    assert(
      dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal(2)).exists(_.render.contains("metered calls only")),
      dailyBlock(Some(ManagerConfig.DailyUsd(2.0)), BigDecimal(2))
    )
    assertEquals(
      ReviewSettings
        .defaultsFor(Manager.Kind.Enrich, mcfg.modify(_.dailyUsd).setTo(ManagerDefaults.DailyUsd(5.0)))
        .dailyUsd,
      Some(5.0)
    )
    assertEquals(
      ManagerConfig.parse(List(Managers.ManagerConfig.Spec("daily-usd=1.5"))).map(_.dailyUsd),
      Right(Some(1.5))
    )
    assert(ManagerConfig.parse(List(Managers.ManagerConfig.Spec("daily-usd=-1"))).isLeft)
