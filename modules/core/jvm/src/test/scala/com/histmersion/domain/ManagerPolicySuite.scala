package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.Agents.RunState
import com.histmersion.domain.ManagerPolicy.*
import com.histmersion.api.Managers.*
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.domain.ReviewLedger.*

import java.time.Instant

/** The process managers' pure policy (docs/research-process-managers.md §3.4): every stop rule of the
  * review manager, the backoff, the dispatch order and fairness, capacity with pools, holds, hints. */
class ManagerPolicySuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-15T12:00:00Z")
  private val s = ReviewSettings(maxRuns = 2, maxOpen = 4, rounds = 3, maxRunsPerArticle = 3, noProgressRuns = 2,
    reopenLimit = 2, cooldownMinutes = 30, lowWater = 2, refillBatch = 10, maxFailures = 3, backoffMinutes = 15,
    maxBackoffMinutes = 360)

  private def f(id: String, r: Reviewer, kind: String = "synth", sev: Severity = Severity.Major) =
    Finding(id, r, sev, kind, s"where $id", s"quote of $id", s"problem $id", "fix")

  private def facts(open: Int, reopened: Int = 0, escalated: Int = 0, onlyMissing: Boolean = false, pairs: Option[Int] = None) =
    Some(ReviewFacts(open, reopened, escalated, onlyMissing, pairs))

  private def eval(st: ItemState = ItemState(), attempts: Int = 1, status: RunState = RunState.Done,
      fs: Option[ReviewFacts] = facts(2), task: Option[String] = Some("review-article [[tin]]: 2 open MAJORs — …"),
      failures: Int = 0, error: Option[String] = None) =
    evaluateReview(s, "tin", attempts, failures, st, "run-1234567890", status, error, fs, task, now)

  test("no open MAJOR: done, converged after k runs"):
    eval(fs = facts(0), attempts = 2) match
      case Next.Done(o, st) =>
        assertEquals(o, "converged after 2 runs")
        assertEquals(st.history.map(_.after), List(0))
      case other => fail(other.toString)

  test("open MAJORs, under every limit: again after the cooldown, the run marked in the history"):
    eval(st = ItemState(before = Some(5))) match
      case Next.Again(nb, st, note) =>
        assertEquals(nb, Some(now.plusSeconds(1800)))
        assertEquals(st.history, List(RunMark("run-1234567890", Some(5), 2)))
        assertEquals(st.before, None)
        assert(note.contains("2 open MAJORs"), note)
      case other => fail(other.toString)

  test("stop rules: omission churn — only missing-critique MAJORs AND the open critique pairs did not go down"):
    // two runs, 5 open pairs before the first, 5 after the second: stalled
    val stalled = ItemState(history = List(RunMark("r1", Some(2), 2, pairsBefore = Some(5), pairsAfter = Some(5))),
      before = Some(2), pairsBefore = Some(5))
    eval(st = stalled, attempts = 2, fs = facts(2, onlyMissing = true, pairs = Some(5))) match
      case Next.Stop(o, Some(t), st) =>
        assert(o.contains("omission churn") && o.contains("did not go down over 2 runs (now 5)"), o)
        assert(t.startsWith("review-article [[tin]]: 2 open MAJORs"), t)
        assert(t.contains("review manager stopped: omission churn"), t)
        assertEquals(st.history.last, RunMark("run-1234567890", Some(2), 2, churn = true, pairsBefore = Some(5), pairsAfter = Some(5)))
      case other => fail(other.toString)

  test("omission churn no longer stops while the open critique pairs go down — not even on flat open MAJORs"):
    // the old rule stopped this run (every open MAJOR a fresh missing-critique); 9 → 6 → 3 pairs is progress
    val going = ItemState(history = List(RunMark("r1", Some(2), 2, pairsBefore = Some(9), pairsAfter = Some(6))),
      before = Some(2), pairsBefore = Some(6))
    eval(st = going, attempts = 2, fs = facts(2, onlyMissing = true, pairs = Some(3))) match
      case Next.Again(_, st, note) =>
        assert(note.contains("3 open critique pairs"), note)
        assertEquals(st.history.last.pairsAfter, Some(3))
      case other => fail(other.toString)
    // one run is no window: the first run's churn never stops it
    assert(eval(st = ItemState(before = Some(2), pairsBefore = Some(4)), fs = facts(2, onlyMissing = true, pairs = Some(4)))
      .isInstanceOf[Next.Again])
    // counts unknown (runs evaluated before the checklist): no evidence of a stall
    assert(!pairsStalled(List(RunMark("r1"), RunMark("r2")), 2))
    // a rules MAJOR among them: the open-MAJOR rule applies as before
    val flat = ItemState(history = List(RunMark("r1", Some(3), 3, pairsBefore = Some(9), pairsAfter = Some(6))), before = Some(3),
      pairsBefore = Some(6))
    assert(eval(st = flat, attempts = 2, fs = facts(3, pairs = Some(3))).isInstanceOf[Next.Stop])
    // max runs per article still stops it
    assert(eval(st = going, attempts = 3, fs = facts(2, onlyMissing = true, pairs = Some(3))).isInstanceOf[Next.Stop])

  test("stop rules: escalated, reopened to the limit, max runs per article, no progress"):
    def stopped(n: Next) = n match
      case Next.Stop(o, t, _) => assert(t.isDefined); o
      case other              => fail(other.toString)
    assert(stopped(eval(fs = facts(1, escalated = 1))).contains("escalated"))
    assert(stopped(eval(fs = facts(1, reopened = 2))).contains("reopened 2 times"))
    assert(stopped(eval(attempts = 3)).contains("max-runs-per-article 3"))
    // two runs, open MAJORs 3 → 3 → 3: no progress over the window of 2
    val hist = ItemState(history = List(RunMark("r1", Some(3), 3)), before = Some(3))
    assert(stopped(eval(st = hist, attempts = 2, fs = facts(3))).contains("did not go down over 2 runs"))
    // 4 → 3 → 2: progress, so again
    val better = ItemState(history = List(RunMark("r1", Some(4), 3)), before = Some(3))
    assert(eval(st = better, attempts = 2, fs = facts(2)).isInstanceOf[Next.Again])
    assert(!noProgress(List(RunMark("r1", Some(3), 3)), 2)) // one run is no window of two

  test("a stop with no ledger line still files a task naming the article and the run"):
    eval(fs = facts(1, escalated = 1), task = None) match
      case Next.Stop(_, Some(t), _) => assert(t.startsWith("review manager stopped [[tin]]: ") && t.contains("run-1234"), t)
      case other => fail(other.toString)

  test("failed runs back off exponentially; the max-failures-th stops the item with a task"):
    eval(status = RunState.Failed, fs = None, error = Some("boom")) match
      case Next.Retry(nb, n, note) =>
        assertEquals((nb, n), (now.plusSeconds(15 * 60), 1)); assert(note.contains("boom"))
      case other => fail(other.toString)
    eval(status = RunState.Failed, fs = None, failures = 1) match
      case Next.Retry(nb, 2, _) => assertEquals(nb, now.plusSeconds(30 * 60))
      case other => fail(other.toString)
    eval(status = RunState.Failed, fs = None, failures = 2) match
      case Next.Stop(o, Some(t), _) => assert(o.contains("3 failed runs") && t.contains("3 failed runs"), (o, t))
      case other => fail(other.toString)
    assertEquals(backoff(15, 360, 10).toMinutes, 360L)
    assertEquals(backoff(15, 360, 1).toMinutes, 15L)

  test("a cancelled run stops the item without a task"):
    assert(eval(status = RunState.Cancelled, fs = None) match
      case Next.Stop(_, None, _) => true
      case _                     => false)

  test("facts from a ledger: open majors, reopen counts, escalation, whether every open MAJOR is a missing-critique"):
    val run = "run-a"
    val sk  = f("S1", Reviewer.Skeptic, MissingCritique)
    val sk2 = f("S2", Reviewer.Skeptic, MissingCritique)
    val l1 = absorb(Ledger("tin"), List(sk), run, 1)._1
    val l2 = applyChecks(applyFix(l1, Fix("b", Nil, List("S1"), Nil, Nil, Nil, "", 1, 1), run, 1), Reviewer.Skeptic,
      List(Check("S1", fixed = true, "q", "")), run, 2)
    val l3 = absorb(l2, List(sk2.copy(quote = "another passage entirely", where = "elsewhere", problem = "omits X's critique")), run, 3)._1
    val fx = ReviewFacts.of(l3, Some(4))
    assertEquals((fx.openMajors, fx.onlyMissingCritiques, fx.openPairs), (1, true, Some(4)))
    // a rules MAJOR among them: not only missing critiques
    val mixed = absorb(l3, List(f("R1", Reviewer.Rules)), run, 3)._1
    assert(!ReviewFacts.of(mixed).onlyMissingCritiques)
    // nothing open: not "only missing critiques" either
    assert(!ReviewFacts.of(l2).onlyMissingCritiques)
    // a missing-critique of any pass or run counts (the pairs decide whether it is churn)
    assert(ReviewFacts.of(l1).onlyMissingCritiques)

  test("dispatch order: priority, then the earliest not-before, then the oldest"):
    val cs = List(Candidate(3, "c", 0, None), Candidate(1, "a", 0, Some(now)), Candidate(2, "b", 10, None),
      Candidate(4, "d", 0, Some(now.minusSeconds(60))))
    assertEquals(order(cs).map(_.target), List("b", "c", "d", "a"))
    assert(!due(Candidate(5, "e", 0, Some(now.plusSeconds(1))), now))
    assert(due(Candidate(5, "e", 0, Some(now)), now))

  test("capacity: the global room, the manager's runs and open runs, the pools"):
    assertEquals(capacity(4, 1, 2, 0, 4, 0, None), 2)
    assertEquals(capacity(4, 4, 2, 0, 4, 0, None), 0)       // a person's runs fill the server
    assertEquals(capacity(4, 1, 2, 1, 4, 1, None), 1)
    assertEquals(capacity(4, 1, 2, 0, 4, 4, None), 0)       // four of its runs paused or waiting
    assertEquals(capacity(4, 0, 2, 0, 4, 0, Some(1)), 1)    // one plan call at a time
    assertEquals(capacity(1, 3, 2, 0, 4, 0, None), 0)       // never negative

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
    val hs = List(ManagerHint(2, "review", None, "b"), ManagerHint(1, "review", Some(7), "a"),
      ManagerHint(3, "review", Some(8), "other item"), ManagerHint(4, "review", None, "gone", retiredAt = Some("x")))
    assertEquals(notesFor(hs, 7).map(_.text), List("a", "b"))

  test("status line: paused, starved, at capacity, pending, idle"):
    assertEquals(statusNote(State.Paused, None, 0, 3, 3, 0, None), "paused")
    assertEquals(statusNote(State.Active, Some("skeptic chain below its plan floor"), 0, 3, 3, 0, None),
      "starved: skeptic chain below its plan floor")
    assert(statusNote(State.Active, None, 0, 3, 3, 2, None).startsWith("at capacity"))
    assertEquals(statusNote(State.Active, None, 0, 0, 0, 0, None), "idle")
    assertEquals(statusNote(State.Active, None, 1, 3, 0, 0, Some("budget: x")), "budget: x")

  test("config: k=v pairs parse; a typo is an error; settings resolve over the defaults"):
    val c = ManagerConfig.parse(List("max-runs=3", "rounds=4", "tags=a, b", "models=fixer=claude-code:opus; skeptic=codex:gpt"))
    assertEquals(c.map(_.maxRuns), Right(Some(3)))
    assertEquals(c.map(_.includeTags), Right(Some(List("a", "b"))))
    assertEquals(c.map(_.models.map(_.map(_.role))), Right(Some(List("fixer", "skeptic"))))
    assert(ManagerConfig.parse(List("maxRun=3")).isLeft)
    assert(ManagerConfig.parse(List("max-runs=x")).isLeft)
    val d = ReviewManagerConfig(2, 3, 3, 2, 2, 30, 2, 10, 90)
    val m = AgentManagersConfig(true, 30, 90, 15, 360, 3, d, SimpleManagerConfig(4), SimpleManagerConfig(1), watch)
    val rs = ReviewSettings.of(ManagerConfig(maxRuns = Some(3)), d, m)
    assertEquals((rs.maxRuns, rs.maxOpen, rs.rounds, rs.maxFailures), (3, 6, 3, 3))
    assertEquals(ReviewSettings.defaults(d, m).merge(ManagerConfig(rounds = Some(5))).rounds, Some(5))

  // ── phase 2 ───────────────────────────────────────────────────────────────────────────────────

  test("selection order: open MAJORs, never reviewed, stale; an article without a filter gets infer-filter first"):
    val picks = select(majors = List("a" -> 3, "b" -> 1), unreviewed = List("c", "a"), stale = List("d" -> "old"), batch = 10,
      inferFilter = true, hasFilter = Set("a", "d"), inferredBefore = Set("c"))
    assertEquals(picks.map(p => (p.kind, p.target, p.priority)), List(
      (ArticleReview, "a", MajorsPriority),
      (InferFilter, "b", MajorsPriority), (ArticleReview, "b", MajorsPriority),
      (ArticleReview, "c", UnreviewedPriority), // its filter was inferred before: not again
      (ArticleReview, "d", StalePriority)))
    assert(picks.find(_.target == "d").get.why.startsWith("stale"))
    // the batch counts reviews; inferFilter off adds none
    assertEquals(select(List("a" -> 1, "b" -> 1), List("c"), Nil, 2, inferFilter = false, _ => false, _ => false).map(_.target),
      List("a", "b"))

  test("waitsFor: an article's review waits while its infer-filter item is open; nothing else waits"):
    val open = List(InferFilter -> "a", ArticleReview -> "a", ArticleReview -> "b")
    assert(waitsFor(ArticleReview, "a", open))
    assert(!waitsFor(ArticleReview, "b", open))
    assert(!waitsFor(InferFilter, "a", open))

  test("staleWhy: older than N days, or its filter gained sources; 0 days = never"):
    val at = now.minus(java.time.Duration.ofDays(100))
    assertEquals(staleWhy(at, now, 90, None, None), Some("last converged over 90 days ago"))
    assertEquals(staleWhy(now, now, 90, Some(4), Some(6)), Some("its filter gained 2 sources"))
    assertEquals(staleWhy(now, now, 90, Some(6), Some(6)), None)
    assertEquals(staleWhy(at, now, 0, Some(1), Some(9)), None)

  test("evaluateStep: done → finished, held for an approval, or stopped; failed backs off then stops; cancelled stops"):
    def ev(st: RunState, f: StepFacts, failures: Int = 0) =
      evaluateStep(s, InferFilter, "tin", failures, ItemState(), "run-1234567890", st, Some("boom"), f, now)
    assertEquals(ev(RunState.Done, StepFacts.Finished("ok")), Next.Done("ok", ItemState()))
    assertEquals(ev(RunState.Done, StepFacts.Waiting(7, "waits")), Next.Wait(7, "waits"))
    assertEquals(ev(RunState.Done, StepFacts.NotDone("no", None)), Next.Stop("no", None, ItemState()))
    ev(RunState.Failed, StepFacts.Finished("x")) match
      case Next.Retry(nb, 1, _) => assertEquals(nb, now.plusSeconds(15 * 60))
      case other => fail(other.toString)
    ev(RunState.Failed, StepFacts.Finished("x"), failures = 2) match
      case Next.Stop(o, Some(t), _) => assert(o.contains("3 failed runs") && t.contains("[[tin]]"), t)
      case other => fail(other.toString)
    assert(ev(RunState.Cancelled, StepFacts.Finished("x")).isInstanceOf[Next.Stop])

  test("decided: an open approval is not decided; a decided one reads with its result"):
    assertEquals(decided("open", None), None)
    assertEquals(decided("approved", Some("applied: x")), Some("approved: applied: x"))
    assertEquals(decided("rejected", None), Some("rejected"))

  test("settings by kind: enrich and seed take their own max-runs; seed's defaults show the plan's checks"):
    val d = ReviewManagerConfig(2, 3, 3, 2, 2, 30, 2, 10, 90)
    val m = AgentManagersConfig(true, 30, 90, 15, 360, 3, d, SimpleManagerConfig(4), SimpleManagerConfig(1), watch)
    assertEquals(ReviewSettings.forKind(Kind.Enrich, ManagerConfig(), m).maxRuns, 4)
    assertEquals(ReviewSettings.forKind(Kind.Seed, ManagerConfig(maxRuns = Some(2)), m).maxRuns, 2)
    assertEquals(ReviewSettings.forKind(Kind.Review, ManagerConfig(), m).staleAfterDays, 90)
    val sd = ReviewSettings.defaultsFor(Kind.Seed, m, AgentSeedConfig(6, 5, 0.5))
    assertEquals((sd.maxRuns, sd.minSources, sd.maxArticles), (Some(1), Some(5), Some(6)))
    val c = ManagerConfig.parse(List("stale-after-days=30", "infer-filter=off", "min-sources=3", "max-articles=4"))
    assertEquals(c.map(x => (x.staleAfterDays, x.inferFilter, x.minSources, x.maxArticles)), Right((Some(30), Some(false), Some(3), Some(4))))
    assert(ManagerConfig.parse(List("infer-filter=maybe")).isLeft)

  test("item kinds and the enqueue words"):
    assertEquals(itemKindOf(Kind.Review, ""), Some(ArticleReview))
    assertEquals(itemKindOf(Kind.Review, "extend"), Some(ArticleExtend))
    assertEquals(itemKindOf(Kind.Seed, "propose"), Some(ProposeArticles))
    assertEquals(itemKindOf(Kind.Enrich, "review"), None)
    assertEquals(EnqueueKind.parse("source-enrich"), Some(EnqueueKind.Enrich))
    assertEquals(EnqueueKind.parse(""), Some(EnqueueKind.Review))
    assertEquals(EnqueueKind.parse("nope"), None)

  // ── phase 3: the watcher and the cron ──────────────────────────────────────────────────────────

  private val watch = WatchManagerConfig(maxRuns = 1, cron = "@weekly", autoIngestPerDay = 5)
  private val mcfg  = AgentManagersConfig(true, 30, 90, 15, 360, 3, ReviewManagerConfig(2, 3, 3, 2, 2, 30, 2, 10, 90),
    SimpleManagerConfig(4), SimpleManagerConfig(1), watch)

  test("watcher: its item kinds and words, its sweeps (none named = both), its settings and defaults"):
    assertEquals(itemKinds(Kind.Watch), List(AcquisitionSweep, CurrencyCheck, OaIngest))
    assertEquals(itemKindOf(Kind.Watch, ""), Some(AcquisitionSweep))
    assertEquals(itemKindOf(Kind.Watch, "backlog"), Some(OaIngest))
    assertEquals(itemKindOf(Kind.Review, "ingest"), None)
    assertEquals(itemKindOf(Kind.Watch, "currency"), Some(CurrencyCheck))
    assertEquals(itemKindOf(Kind.Watch, "review"), None)
    assertEquals(watchId(" tin "), "watch:tin")
    assertEquals(Sweep.of(ManagerConfig()), List(Sweep.Acquisition, Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(List("currency")))), List(Sweep.Currency))
    assertEquals(Sweep.of(ManagerConfig(sweeps = Some(Nil))), Nil)
    assertEquals(ReviewSettings.forKind(Kind.Watch, ManagerConfig(), mcfg).maxRuns, 1)
    val d = ReviewSettings.defaultsFor(Kind.Watch, mcfg, AgentSeedConfig(6, 5, 0.5))
    assertEquals((d.autoIngest, d.autoIngestPerDay, d.sweeps), (Some(false), Some(5), Some(List("acquisition", "currency"))))
    val c = ManagerConfig.parse(List("sweeps=acquisition-sweep, currency", "auto-ingest=on", "auto-ingest-per-day=3"))
    assertEquals(c.map(x => (x.sweeps, x.autoIngest, x.autoIngestPerDay)),
      Right((Some(List("acquisition", "currency")), Some(true), Some(3))))
    assert(ManagerConfig.parse(List("sweeps=weekly")).left.exists(_.contains("not a sweep")))
    assert(ManagerConfig.parse(List("auto-ingest=perhaps")).isLeft)

  test("auto-ingest is opt-in: off by default; on, what is left of the daily cap (the watcher's own cap wins)"):
    assertEquals(ingestAllowance(ManagerConfig(), mcfg, 0), 0)
    assertEquals(ingestAllowance(ManagerConfig(autoIngest = Some(false)), mcfg, 0), 0)
    assertEquals(ingestAllowance(ManagerConfig(autoIngest = Some(true)), mcfg, 0), 5)
    assertEquals(ingestAllowance(ManagerConfig(autoIngest = Some(true)), mcfg, 4), 1)
    assertEquals(ingestAllowance(ManagerConfig(autoIngest = Some(true)), mcfg, 9), 0)
    assertEquals(ingestAllowance(ManagerConfig(autoIngest = Some(true), autoIngestPerDay = Some(2)), mcfg, 1), 1)
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
    assertEquals(cronStep(weekly, Some(sun), Instant.parse("2026-11-10T09:00:00Z")),
      CronStep.Fire(Some(Instant.parse("2026-11-15T00:00:00Z"))))

  test("checkCron: it parses and fires no more often than the minimum interval"):
    assert(checkCron("not cron", now, 60).left.exists(_.contains("five fields")))
    assert(checkCron("*/5 * * * *", now, 60).left.exists(_.contains("min-interval")))
    assertEquals(checkCron("@daily", now, 60).map(_.text), Right("@daily"))

  test("daily cap: its own, else agents.managers.daily-usd (0 = none); blocks once the metered spend reaches it"):
    assertEquals(dailyCap(ManagerConfig(), mcfg), None)
    assertEquals(dailyCap(ManagerConfig(dailyUsd = Some(2.0)), mcfg), Some(2.0))
    assertEquals(dailyCap(ManagerConfig(dailyUsd = Some(0.0)), mcfg.copy(dailyUsd = 5)), None, "0 on the manager = no cap")
    assertEquals(dailyCap(ManagerConfig(), mcfg.copy(dailyUsd = 5)), Some(5.0))
    assertEquals(dailyBlock(None, BigDecimal(100)), None)
    assertEquals(dailyBlock(Some(2.0), BigDecimal("1.99")), None)
    assert(dailyBlock(Some(2.0), BigDecimal(2)).exists(_.contains("metered calls only")), dailyBlock(Some(2.0), BigDecimal(2)))
    assertEquals(ReviewSettings.defaultsFor(Kind.Enrich, mcfg.copy(dailyUsd = 5), AgentSeedConfig(6, 5, 0.5)).dailyUsd, Some(5.0))
    assertEquals(ManagerConfig.parse(List("daily-usd=1.5")).map(_.dailyUsd), Right(Some(1.5)))
    assert(ManagerConfig.parse(List("daily-usd=-1")).isLeft)
