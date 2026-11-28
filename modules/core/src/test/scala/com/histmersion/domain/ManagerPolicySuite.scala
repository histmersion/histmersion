package com.histmersion
package domain

import com.histmersion.domain.managerpolicy.ReviewSettings
import com.histmersion.domain.managerpolicy.Assignable
import com.histmersion.api.automation.managers.HintEffect
import com.histmersion.api.automation.managers.ManagerHint
import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.domain.schedulerpolicy.Block
import com.histmersion.api.automation.managers.ManagerConfig
import com.histmersion.domain.managerpolicy.StepFacts
import com.histmersion.domain.managerpolicy.Next
import com.histmersion.api.automation.managers.ItemState
import com.histmersion.api.automation.managers.EnqueueKind
import com.histmersion.api.automation.managers.Sweep
import com.histmersion.domain.managerpolicy.CronStep

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.problem.{Legacy, Problem}
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
import com.histmersion.lint.{tolerated, LintRule}

/** The process managers' pure policy (docs/research-process-managers.md §3.4): the one-run items' rules, the backoff,
  * the dispatch order and fairness, capacity with pools, holds, hints, the cron and the daily cap.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class ManagerPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-15T12:00:00Z")
  private val s = ReviewSettings(
    maxRuns = ReviewSettings.MaxRuns(2),
    maxOpen = ReviewSettings.MaxOpen(4),
    rounds = ReviewSettings.Rounds(3),
    maxRunsPerArticle = ReviewSettings.MaxRunsPerArticle(3),
    noProgressRuns = ReviewSettings.NoProgressRuns(2),
    reopenLimit = ReviewSettings.ReopenLimit(2),
    cooldownMinutes = ReviewSettings.CooldownMinutes(30),
    lowWater = ReviewSettings.LowWater(2),
    refillBatch = ReviewSettings.RefillBatch(10),
    maxFailures = ReviewSettings.MaxFailures(3),
    backoffMinutes = ReviewSettings.BackoffMinutes(15),
    maxBackoffMinutes = ReviewSettings.MaxBackoffMinutes(360)
  )

  test("dispatch order: priority, then the earliest not-before, then the oldest"):
    val cs = List(
      Assignable(
        Assignable.Id(3),
        Assignable.Target("c"),
        Assignable.Priority(0),
        None
      ),
      Assignable(
        Assignable.Id(1),
        Assignable.Target("a"),
        Assignable.Priority(0),
        Some(now)
      ),
      Assignable(
        Assignable.Id(2),
        Assignable.Target("b"),
        Assignable.Priority(10),
        None
      ),
      Assignable(
        Assignable.Id(4),
        Assignable.Target("d"),
        Assignable.Priority(0),
        Some(now.minusSeconds(60))
      )
    )
    assertEquals(order(cs).map(_.target), List("b", "c", "d", "a"))
    assert(
      !Assignable(
        Assignable.Id(5),
        Assignable.Target("e"),
        Assignable.Priority(0),
        Some(now.plusSeconds(1))
      ).due(now)
    )
    assert(
      Assignable(
        Assignable.Id(5),
        Assignable.Target("e"),
        Assignable.Priority(0),
        Some(now)
      ).due(now)
    )

  test("capacity: the global room, the manager's runs and open runs, the pools"):
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(1),
        ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        None
      ),
      2
    )
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(4),
        ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        None
      ),
      0
    ) // a person's runs fill the server
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(1),
        ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(1),
        ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(1),
        None
      ),
      1
    )
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(1),
        ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(4),
        None
      ),
      0
    ) // four of its runs paused or waiting
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(4),
        ManagerPolicy.ActiveAll(0),
        ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ReviewSettings.MaxOpen(4),
        ManagerPolicy.MineOpen(0),
        Some(ManagerPolicy.PoolCap(1))
      ),
      1
    ) // one plan call at a time
    assertEquals(
      capacity(
        ManagerPolicy.MaxConcurrent(1),
        ManagerPolicy.ActiveAll(3),
        ReviewSettings.MaxRuns(2),
        ManagerPolicy.MineRunning(0),
        ReviewSettings.MaxOpen(4),
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
    assert(
      released(
        api.automation.managers.Hold(until = Some(api.automation.managers.Hold.Until("2026-10-15"))),
        now,
        _ => false
      )
    )
    assert(
      !released(
        api.automation.managers.Hold(until = Some(api.automation.managers.Hold.Until("2026-10-16T00:00:00Z"))),
        now,
        _ => false
      )
    )
    assert(
      released(
        api.automation.managers.Hold(untilSource = Some(api.automation.managers.Hold.UntilSource("10.1/x"))),
        now,
        _.value == "10.1/x"
      )
    )
    assert(!released(api.automation.managers.Hold(reason = Managers.Rationale("person")), now, _ => true))

  test("hint effects match by target, tag, topic or goal; notes are live and in scope"):
    val e = HintEffect(HintEffect.Verb.Prioritise.word, tag = Some(HintEffect.Tag("Tin Trade")))
    assert(matches(e, Assignable.Target("x"), List(ManagerPolicy.Tag("tin trade")), Nil, Nil))
    assert(!matches(e, Assignable.Target("x"), List(ManagerPolicy.Tag("copper")), Nil, Nil))
    assert(
      matches(
        HintEffect(HintEffect.Verb.Skip.word, targets = List(HintEffect.Targets("tin"))),
        Assignable.Target("TIN"),
        Nil,
        Nil,
        Nil
      )
    )
    assert(
      matches(
        HintEffect(HintEffect.Verb.Hold.word, goal = Some(Goal.Slug("g"))),
        Assignable.Target("x"),
        Nil,
        Nil,
        List(automation.Goal.Slug("g"))
      )
    )
    val hs = List(
      ManagerHint(ManagerHint.Id(2), ManagerInfo.Id("review"), None, ManagerHint.Text("b")),
      ManagerHint(
        ManagerHint.Id(1),
        ManagerInfo.Id("review"),
        Some(ManagerHint.ItemId(7)),
        ManagerHint.Text("a")
      ),
      ManagerHint(
        ManagerHint.Id(3),
        ManagerInfo.Id("review"),
        Some(ManagerHint.ItemId(8)),
        ManagerHint.Text("other item")
      ),
      ManagerHint(
        ManagerHint.Id(4),
        ManagerInfo.Id("review"),
        None,
        ManagerHint.Text("gone"),
        retiredAt = Some(ManagerHint.RetiredAt("x"))
      )
    )
    assertEquals(notesFor(hs, Item.Id(7)).map(_.text), List("a", "b"))

  test("status line: paused, starved, at capacity, pending, idle"):
    val chain = Block.Chain("skeptic chain below its plan floor")
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
        Some(Block.Why.Starved(chain)),
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
        ManagerConfig.Spec("max-runs=3"),
        ManagerConfig.Spec("rounds=4"),
        ManagerConfig.Spec("tags=a, b"),
        ManagerConfig.Spec("models=fixer=claude-code:opus; skeptic=codex:gpt")
      )
    )
    assertEquals(c.map(_.maxRuns), Right(Some(3)))
    assertEquals(c.map(_.includeTags), Right(Some(List("a", "b"))))
    assertEquals(c.map(_.models.map(_.map(_.role))), Right(Some(List("fixer", "skeptic"))))
    assert(ManagerConfig.parse(List(ManagerConfig.Spec("maxRun=3"))).isLeft)
    assert(ManagerConfig.parse(List(ManagerConfig.Spec("max-runs=x"))).isLeft)

  // ── phase 2 ───────────────────────────────────────────────────────────────────────────────────

  test("waitsFor: an article's review waits while its infer-filter item is open; nothing else waits"):
    val open = List(
      Item.Kind.InferFilter -> Assignable.Target("a"),
      Item.Kind.ArticleReview -> Assignable.Target("a"),
      Item.Kind.ArticleReview -> Assignable.Target("b")
    )
    assert(waitsFor(Item.Kind.ArticleReview, Assignable.Target("a"), open))
    assert(!waitsFor(Item.Kind.ArticleReview, Assignable.Target("b"), open))
    assert(!waitsFor(Item.Kind.InferFilter, Assignable.Target("a"), open))

  test("evaluateStep: done → finished, held for an approval, or stopped; failed backs off then stops; cancelled stops"):
    def ev(st: RunState, f: StepFacts, failures: Next.Retry.Failures = Next.Retry.Failures(0)) =
      s.evaluateStep(
        StepLabel(InferFilter),
        Assignable.Target("tin"),
        failures,
        ItemState(),
        Run.Id("ccf9dbcf-a45c-e9ef-aef8-3e7aff5d2a57"),
        st,
        Some(Problem.Unclassified(Legacy.Text("boom"))),
        f,
        now
      )
    assertEquals(
      ev(Run.State.Done, StepFacts.Finished(Item.Outcome("ok"))),
      Next.Done(Item.Outcome("ok"), ItemState())
    )
    assertEquals(
      ev(Run.State.Done, StepFacts.Waiting(ManagerPolicy.ApprovalId(7), Item.Note("waits"))),
      Next.Wait(ManagerPolicy.ApprovalId(7), Item.Note("waits"))
    )
    assertEquals(
      ev(Run.State.Done, StepFacts.NotDone(Item.Outcome("no"), None)),
      Next.Stop(Item.Outcome("no"), None, ItemState())
    )
    ev(Run.State.Failed, StepFacts.Finished(Item.Outcome("x"))) match
      case Next.Retry(nb, failures, _) if failures == Next.Retry.Failures(1) =>
        assertEquals(nb, now.plusSeconds(15 * 60))
      case other => fail(other.toString)
    ev(Run.State.Failed, StepFacts.Finished(Item.Outcome("x")), failures = Next.Retry.Failures(2)) match
      case Next.Stop(o, Some(t), _) => assert(o.value.contains("3 failed runs") && t.value.contains("[[tin]]"), t)
      case other                    => fail(other.toString)
    assert(ev(Run.State.Cancelled, StepFacts.Finished(Item.Outcome("x"))).isInstanceOf[Next.Stop])

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
        .forKind(Manager.Kind.Seed, ManagerConfig(maxRuns = Some(ManagerConfig.MaxRuns(2))), m)
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
        ManagerConfig.Spec("stale-after-days=30"),
        ManagerConfig.Spec("infer-filter=off"),
        ManagerConfig.Spec("min-sources=3"),
        ManagerConfig.Spec("max-articles=4")
      )
    )
    assertEquals(
      c.map(x => (x.staleAfterDays, x.inferFilter.map(_.inferFilter), x.minSources, x.maxArticles)),
      Right((Some(30), Some(false), Some(3), Some(4)))
    )
    assert(ManagerConfig.parse(List(ManagerConfig.Spec("infer-filter=maybe"))).isLeft)

  test("item kinds and the enqueue words"):
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Review, api.ItemKind.Word("")),
      Some(ArticleReview)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Review, api.ItemKind.Word("extend")),
      Some(ArticleExtend)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Seed, api.ItemKind.Word("propose")),
      Some(ProposeArticles)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Enrich, api.ItemKind.Word("review")),
      None
    )
    assertEquals(EnqueueKind.parse(EnqueueKind.Wire("source-enrich")), Some(EnqueueKind.Enrich))
    assertEquals(EnqueueKind.parse(EnqueueKind.Wire("")), Some(EnqueueKind.Review))
    assertEquals(EnqueueKind.parse(EnqueueKind.Wire("nope")), None)

  // ── phase 3: the watcher and the cron ──────────────────────────────────────────────────────────

  private val watch = WatchManagerDefaults(
    maxRuns = WatchManagerDefaults.MaxRuns(1),
    autoIngestPerDay = WatchManagerDefaults.AutoIngestPerDay(5)
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
      itemKinds(api.automation.Manager.Kind.Watch),
      List(AcquisitionSweep, CurrencyCheck, OaIngest)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Watch, api.ItemKind.Word("")),
      Some(AcquisitionSweep)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Watch, api.ItemKind.Word("backlog")),
      Some(OaIngest)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Review, api.ItemKind.Word("ingest")),
      None
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Watch, api.ItemKind.Word("currency")),
      Some(CurrencyCheck)
    )
    assertEquals(
      itemKindOf(api.automation.Manager.Kind.Watch, api.ItemKind.Word("review")),
      None
    )
    assertEquals(watchId(Goal.Ref(" tin ")), "watch:tin")
    assertEquals(Sweep.of(ManagerConfig()), List(Sweep.Acquisition, Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(List(Sweep.Wire("currency"))))), List(Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(Nil))), Nil)
    assertEquals(ReviewSettings.forKind(Manager.Kind.Watch, ManagerConfig(), mcfg).maxRuns, 1)
    val d = watch.managerDefaults(mcfg)
    assertEquals(
      (d.autoIngest.map(_.autoIngest), d.autoIngestPerDay, d.sweeps),
      (Some(false), Some(5), Some(List("acquisition", "currency")))
    )
    val c = ManagerConfig.parse(
      List(
        ManagerConfig.Spec("sweeps=acquisition-sweep, currency"),
        ManagerConfig.Spec("auto-ingest=on"),
        ManagerConfig.Spec("auto-ingest-per-day=3")
      )
    )
    assertEquals(
      c.map(x => (x.sweeps, x.autoIngest.map(_.autoIngest), x.autoIngestPerDay)),
      Right((Some(List("acquisition", "currency")), Some(true), Some(3)))
    )
    assert(
      ManagerConfig
        .parse(List(ManagerConfig.Spec("sweeps=weekly")))
        .left
        .exists(_.exists {
          case Problem.Invalid.Unknown(_, raw, _, at) =>
            raw == Problem.Raw("weekly") && at == Vector(Problem.Field("sweeps"))
          case _ => false
        })
    )
    assert(ManagerConfig.parse(List(ManagerConfig.Spec("auto-ingest=perhaps"))).isLeft)

  test("auto-ingest is opt-in: off by default; on, what is left of the daily cap (the watcher's own cap wins)"):
    assertEquals(watch.ingestAllowance(ManagerConfig(), WatchManagerDefaults.IngestedToday(0)), 0)
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(ManagerConfig.AutoIngest.Manual)),
        WatchManagerDefaults.IngestedToday(0)
      ),
      0
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(ManagerConfig.AutoIngest.Auto)),
        WatchManagerDefaults.IngestedToday(0)
      ),
      5
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(ManagerConfig.AutoIngest.Auto)),
        WatchManagerDefaults.IngestedToday(4)
      ),
      1
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(autoIngest = Some(ManagerConfig.AutoIngest.Auto)),
        WatchManagerDefaults.IngestedToday(9)
      ),
      0
    )
    assertEquals(
      watch.ingestAllowance(
        ManagerConfig(
          autoIngest = Some(ManagerConfig.AutoIngest.Auto),
          autoIngestPerDay = Some(ManagerConfig.AutoIngestPerDay(2))
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
        .exists(_.sentence.contains("five fields"))
    )
    assert(
      checkCron(Cron.Text("*/5 * * * *"), now, ManagerPolicy.MinIntervalMinutes(60)).left
        .exists(_.sentence.contains("min-interval"))
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
      ManagerConfig.parse(List(ManagerConfig.Spec("daily-usd=1.5"))).map(_.dailyUsd),
      Right(Some(1.5))
    )
    assert(ManagerConfig.parse(List(ManagerConfig.Spec("daily-usd=-1"))).isLeft)
