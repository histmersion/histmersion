package com.histmersion.domain

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
    maxRuns = 2,
    maxOpen = 4,
    rounds = 3,
    maxRunsPerArticle = 3,
    noProgressRuns = 2,
    reopenLimit = 2,
    cooldownMinutes = 30,
    lowWater = 2,
    refillBatch = 10,
    maxFailures = 3,
    backoffMinutes = 15,
    maxBackoffMinutes = 360
  )

  test("dispatch order: priority, then the earliest not-before, then the oldest"):
    val cs = List(
      Candidate(3, "c", 0, None),
      Candidate(1, "a", 0, Some(now)),
      Candidate(2, "b", 10, None),
      Candidate(4, "d", 0, Some(now.minusSeconds(60)))
    )
    assertEquals(order(cs).map(_.target), List("b", "c", "d", "a"))
    assert(!due(Candidate(5, "e", 0, Some(now.plusSeconds(1))), now))
    assert(due(Candidate(5, "e", 0, Some(now)), now))

  test("capacity: the global room, the manager's runs and open runs, the pools"):
    assertEquals(capacity(4, 1, 2, 0, 4, 0, None), 2)
    assertEquals(capacity(4, 4, 2, 0, 4, 0, None), 0) // a person's runs fill the server
    assertEquals(capacity(4, 1, 2, 1, 4, 1, None), 1)
    assertEquals(capacity(4, 1, 2, 0, 4, 4, None), 0) // four of its runs paused or waiting
    assertEquals(capacity(4, 0, 2, 0, 4, 0, Some(1)), 1) // one plan call at a time
    assertEquals(capacity(1, 3, 2, 0, 4, 0, None), 0) // never negative

  test("round robin: one each in turn, within each cap and the room"):
    val rr = roundRobin(List(("a", 2, List(1, 2, 3)), ("b", 5, List(10, 20)), ("c", 0, List(100))), 4)
    assertEquals(rr, List("a" -> 1, "b" -> 10, "a" -> 2, "b" -> 20))
    assertEquals(roundRobin(List(("a", 2, List(1, 2, 3))), 0), Nil)

  test("holds: a date that passed, a source that exists; neither waits for a person"):
    assert(released(Hold(until = Some("2026-10-15")), now, _ => false))
    assert(!released(Hold(until = Some("2026-10-16T00:00:00Z")), now, _ => false))
    assert(released(Hold(untilSource = Some("10.1/x")), now, _ == "10.1/x"))
    assert(!released(Hold(reason = "person"), now, _ => true))

  test("hint effects match by target, tag, topic or goal; notes are live and in scope"):
    val e = HintEffect(HintEffect.Prioritise, tag = Some("Tin Trade"))
    assert(matches(e, "x", List("tin trade"), Nil, Nil))
    assert(!matches(e, "x", List("copper"), Nil, Nil))
    assert(matches(HintEffect(HintEffect.Skip, targets = List("tin")), "TIN", Nil, Nil, Nil))
    assert(matches(HintEffect(HintEffect.HoldAction, goal = Some("g")), "x", Nil, Nil, List("g")))
    val hs = List(
      ManagerHint(2, "review", None, "b"),
      ManagerHint(1, "review", Some(7), "a"),
      ManagerHint(3, "review", Some(8), "other item"),
      ManagerHint(4, "review", None, "gone", retiredAt = Some("x"))
    )
    assertEquals(notesFor(hs, 7).map(_.text), List("a", "b"))

  test("status line: paused, starved, at capacity, pending, idle"):
    assertEquals(statusNote(com.histmersion.domain.automation.Manager.State.Paused, None, 0, 3, 3, 0, None), "paused")
    assertEquals(
      statusNote(com.histmersion.domain.automation.Manager.State.Active, Some("skeptic chain below its plan floor"), 0, 3, 3, 0, None),
      "starved: skeptic chain below its plan floor"
    )
    assert(statusNote(com.histmersion.domain.automation.Manager.State.Active, None, 0, 3, 3, 2, None).startsWith("at capacity"))
    assertEquals(statusNote(com.histmersion.domain.automation.Manager.State.Active, None, 0, 0, 0, 0, None), "idle")
    assertEquals(statusNote(com.histmersion.domain.automation.Manager.State.Active, None, 1, 3, 0, 0, Some("budget: x")), "budget: x")

  test("config: k=v pairs parse; a typo is an error; settings resolve over the defaults"):
    val c = ManagerConfig.parse(
      List("max-runs=3", "rounds=4", "tags=a, b", "models=fixer=claude-code:opus; skeptic=codex:gpt")
    )
    assertEquals(c.map(_.maxRuns), Right(Some(3)))
    assertEquals(c.map(_.includeTags), Right(Some(List("a", "b"))))
    assertEquals(c.map(_.models.map(_.map(_.role))), Right(Some(List("fixer", "skeptic"))))
    assert(ManagerConfig.parse(List("maxRun=3")).isLeft)
    assert(ManagerConfig.parse(List("max-runs=x")).isLeft)

  // ── phase 2 ───────────────────────────────────────────────────────────────────────────────────

  test("waitsFor: an article's review waits while its infer-filter item is open; nothing else waits"):
    val open = List(InferFilter -> "a", ArticleReview -> "a", ArticleReview -> "b")
    assert(waitsFor(ArticleReview, "a", open))
    assert(!waitsFor(ArticleReview, "b", open))
    assert(!waitsFor(InferFilter, "a", open))

  test("evaluateStep: done → finished, held for an approval, or stopped; failed backs off then stops; cancelled stops"):
    def ev(st: RunState, f: StepFacts, failures: Int = 0) =
      evaluateStep(s, InferFilter, "tin", failures, ItemState(), "run-1234567890", st, Some("boom"), f, now)
    assertEquals(ev(com.histmersion.domain.automation.Run.State.Done, StepFacts.Finished("ok")), Next.Done("ok", ItemState()))
    assertEquals(ev(com.histmersion.domain.automation.Run.State.Done, StepFacts.Waiting(7, "waits")), Next.Wait(7, "waits"))
    assertEquals(ev(com.histmersion.domain.automation.Run.State.Done, StepFacts.NotDone("no", None)), Next.Stop("no", None, ItemState()))
    ev(com.histmersion.domain.automation.Run.State.Failed, StepFacts.Finished("x")) match
      case Next.Retry(nb, 1, _) => assertEquals(nb, now.plusSeconds(15 * 60))
      case other                => fail(other.toString)
    ev(com.histmersion.domain.automation.Run.State.Failed, StepFacts.Finished("x"), failures = 2) match
      case Next.Stop(o, Some(t), _) => assert(o.contains("3 failed runs") && t.contains("[[tin]]"), t)
      case other                    => fail(other.toString)
    assert(ev(com.histmersion.domain.automation.Run.State.Cancelled, StepFacts.Finished("x")).isInstanceOf[Next.Stop])

  test("decided: an open approval is not decided; a decided one reads with its result"):
    assertEquals(decided("open", None), None)
    assertEquals(decided("approved", Some("applied: x")), Some("approved: applied: x"))
    assertEquals(decided("rejected", None), Some("rejected"))

  test("settings by kind: enrich and seed take their own max-runs; seed's defaults show the plan's checks"):
    val m = ManagerDefaults(
      3,
      15,
      360,
      0,
      ManagerRunDefaults(2),
      ManagerRunDefaults(4),
      ManagerRunDefaults(1),
      ManagerRunDefaults(1)
    )
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Enrich, ManagerConfig(), m).maxRuns, 4)
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Seed, ManagerConfig(maxRuns = Some(2)), m).maxRuns, 2)
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Review, ManagerConfig(), m).maxRuns, 2)
    val sd = SeedPlanDefaults(maxArticles = 6, minSources = 5).managerDefaults(m)
    assertEquals((sd.maxRuns, sd.minSources, sd.maxArticles), (Some(1), Some(5), Some(6)))
    val c = ManagerConfig.parse(List("stale-after-days=30", "infer-filter=off", "min-sources=3", "max-articles=4"))
    assertEquals(
      c.map(x => (x.staleAfterDays, x.inferFilter, x.minSources, x.maxArticles)),
      Right((Some(30), Some(false), Some(3), Some(4)))
    )
    assert(ManagerConfig.parse(List("infer-filter=maybe")).isLeft)

  test("item kinds and the enqueue words"):
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, ""), Some(ArticleReview))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, "extend"), Some(ArticleExtend))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Seed, "propose"), Some(ProposeArticles))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Enrich, "review"), None)
    assertEquals(EnqueueKind.parse("source-enrich"), Some(EnqueueKind.Enrich))
    assertEquals(EnqueueKind.parse(""), Some(EnqueueKind.Review))
    assertEquals(EnqueueKind.parse("nope"), None)

  // ── phase 3: the watcher and the cron ──────────────────────────────────────────────────────────

  private val watch = WatchManagerDefaults(maxRuns = 1, autoIngestPerDay = 5)
  private val mcfg = ManagerDefaults(
    3,
    15,
    360,
    0,
    ManagerRunDefaults(2),
    ManagerRunDefaults(4),
    ManagerRunDefaults(1),
    ManagerRunDefaults(watch.maxRuns)
  )

  test("watcher: its item kinds and words, its sweeps (none named = both), its settings and defaults"):
    assertEquals(itemKinds(com.histmersion.api.automation.Manager.Kind.Watch), List(AcquisitionSweep, CurrencyCheck, OaIngest))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, ""), Some(AcquisitionSweep))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, "backlog"), Some(OaIngest))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Review, "ingest"), None)
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, "currency"), Some(CurrencyCheck))
    assertEquals(itemKindOf(com.histmersion.api.automation.Manager.Kind.Watch, "review"), None)
    assertEquals(watchId(" tin "), "watch:tin")
    assertEquals(Sweep.of(ManagerConfig()), List(Sweep.Acquisition, Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(List("currency")))), List(Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(Nil))), Nil)
    assertEquals(ReviewSettings.forKind(com.histmersion.domain.automation.Manager.Kind.Watch, ManagerConfig(), mcfg).maxRuns, 1)
    val d = watch.managerDefaults(mcfg)
    assertEquals(
      (d.autoIngest, d.autoIngestPerDay, d.sweeps),
      (Some(false), Some(5), Some(List("acquisition", "currency")))
    )
    val c = ManagerConfig.parse(List("sweeps=acquisition-sweep, currency", "auto-ingest=on", "auto-ingest-per-day=3"))
    assertEquals(
      c.map(x => (x.sweeps, x.autoIngest, x.autoIngestPerDay)),
      Right((Some(List("acquisition", "currency")), Some(true), Some(3)))
    )
    assert(ManagerConfig.parse(List("sweeps=weekly")).left.exists(_.contains("not a sweep")))
    assert(ManagerConfig.parse(List("auto-ingest=perhaps")).isLeft)

  test("auto-ingest is opt-in: off by default; on, what is left of the daily cap (the watcher's own cap wins)"):
    assertEquals(watch.ingestAllowance(ManagerConfig(), 0), 0)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(false)), 0), 0)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(true)), 0), 5)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(true)), 4), 1)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(true)), 9), 0)
    assertEquals(watch.ingestAllowance(ManagerConfig(autoIngest = Some(true), autoIngestPerDay = Some(2)), 1), 1)
    assertEquals(dayStart(Instant.parse("2026-10-17T23:59:00Z")), Instant.parse("2026-10-17T00:00:00Z"))

  test("cron: not scheduled → its first firing recorded; due → fire once and move on; a missed firing fires once"):
    val weekly = Cron.parse("@weekly").toOption
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
    assert(checkCron("not cron", now, 60).left.exists(_.contains("five fields")))
    assert(checkCron("*/5 * * * *", now, 60).left.exists(_.contains("min-interval")))
    assertEquals(checkCron("@daily", now, 60).map(_.text), Right("@daily"))

  test("daily cap: its own, else agents.managers.daily-usd (0 = none); blocks once the metered spend reaches it"):
    assertEquals(dailyCap(ManagerConfig(), mcfg), None)
    assertEquals(dailyCap(ManagerConfig(dailyUsd = Some(2.0)), mcfg), Some(2.0))
    assertEquals(
      dailyCap(ManagerConfig(dailyUsd = Some(0.0)), mcfg.copy(dailyUsd = 5)),
      None,
      "0 on the manager = no cap"
    )
    assertEquals(dailyCap(ManagerConfig(), mcfg.copy(dailyUsd = 5)), Some(5.0))
    assertEquals(dailyBlock(None, BigDecimal(100)), None)
    assertEquals(dailyBlock(Some(2.0), BigDecimal("1.99")), None)
    assert(
      dailyBlock(Some(2.0), BigDecimal(2)).exists(_.contains("metered calls only")),
      dailyBlock(Some(2.0), BigDecimal(2))
    )
    assertEquals(
      ReviewSettings
        .defaultsFor(com.histmersion.domain.automation.Manager.Kind.Enrich, mcfg.copy(dailyUsd = 5))
        .dailyUsd,
      Some(5.0)
    )
    assertEquals(ManagerConfig.parse(List("daily-usd=1.5")).map(_.dailyUsd), Right(Some(1.5)))
    assert(ManagerConfig.parse(List("daily-usd=-1")).isLeft)
