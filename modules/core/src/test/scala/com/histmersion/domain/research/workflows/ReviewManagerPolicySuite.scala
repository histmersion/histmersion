package com.histmersion.domain.research.workflows

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given

import com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.{ManagerDefaults, ManagerPolicy, ManagerRunDefaults, ReviewManagerDefaults}
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings}
import com.histmersion.domain.research.workflows.ReviewManagerPolicy.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.domain.ReviewLedger.*

import java.time.Instant

/** The review manager's pure rules (docs/research-process-managers.md §3.4): every stop rule over the review ledger,
  * the settings over research's defaults, the selection and when an article is stale.
  */
class ReviewManagerPolicySuite extends munit.FunSuite:

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

  private def f(id: String, r: Reviewer, kind: String = "synth", sev: Severity = Severity.Major) =
    Finding(id, r, sev, kind, s"where $id", s"quote of $id", s"problem $id", com.histmersion.domain.ReviewArticle.Finding.Fix("fix"))

  private def facts(
      open: Int,
      reopened: Int = 0,
      escalated: Int = 0,
      onlyMissing: Boolean = false,
      pairs: Option[Int] = None
  ) =
    Some(ReviewFacts(open, reopened, escalated, onlyMissing, pairs))

  private def eval(
      st: ItemState = ItemState(),
      attempts: Int = 1,
      status: RunState = RunState.Done,
      fs: Option[ReviewFacts] = facts(2),
      task: Option[String] = Some("review-article [[tin]]: 2 open MAJORs — …"),
      failures: Int = 0,
      error: Option[String] = None
  ) =
    evaluateReview(s, com.histmersion.domain.research.Slug("tin"), attempts, failures, st, com.histmersion.domain.automation.Run.Id("run-1234567890"), status, error, fs, task, now)

  test("no open MAJOR: done, converged after k runs"):
    eval(fs = facts(0), attempts = 2) match
      case Next.Done(o, st) =>
        assertEquals(o, "converged after 2 runs")
        assertEquals(st.history.map(_.after), List(0))
      case other => fail(other.toString)

  test("open MAJORs, under every limit: again after the cooldown, the run marked in the history"):
    eval(st = ItemState(before = Some(com.histmersion.api.automation.Managers.ItemState.Before(5)))) match
      case Next.Again(nb, st, note) =>
        assertEquals(nb, Some(now.plusSeconds(1800)))
        assertEquals(st.history, List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("run-1234567890"), Some(com.histmersion.api.automation.Managers.RunMark.Before(5)), com.histmersion.api.automation.Managers.RunMark.After(2))))
        assertEquals(st.before, None)
        assert(note.contains("2 open MAJORs"), note)
      case other => fail(other.toString)

  test("stop rules: omission churn — only missing-critique MAJORs AND the open critique pairs did not go down"):
    // two runs, 5 open pairs before the first, 5 after the second: stalled
    val stalled = ItemState(
      history = List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), Some(com.histmersion.api.automation.Managers.RunMark.Before(2)), com.histmersion.api.automation.Managers.RunMark.After(2), pairsBefore = Some(com.histmersion.api.automation.Managers.RunMark.PairsBefore(5)), pairsAfter = Some(com.histmersion.api.automation.Managers.RunMark.PairsAfter(5)))),
      before = Some(com.histmersion.api.automation.Managers.ItemState.Before(2)),
      pairsBefore = Some(com.histmersion.api.automation.Managers.ItemState.PairsBefore(5))
    )
    eval(st = stalled, attempts = 2, fs = facts(2, onlyMissing = true, pairs = Some(5))) match
      case Next.Stop(o, Some(t), st) =>
        assert(o.contains("omission churn") && o.contains("did not go down over 2 runs (now 5)"), o)
        assert(t.startsWith("review-article [[tin]]: 2 open MAJORs"), t)
        assert(t.contains("review manager stopped: omission churn"), t)
        assertEquals(
          st.history.last,
          RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("run-1234567890"), Some(com.histmersion.api.automation.Managers.RunMark.Before(2)), com.histmersion.api.automation.Managers.RunMark.After(2), churn = com.histmersion.api.automation.Managers.RunMark.Churn(true), pairsBefore = Some(com.histmersion.api.automation.Managers.RunMark.PairsBefore(5)), pairsAfter = Some(com.histmersion.api.automation.Managers.RunMark.PairsAfter(5)))
        )
      case other => fail(other.toString)

  test("omission churn no longer stops while the open critique pairs go down — not even on flat open MAJORs"):
    // the old rule stopped this run (every open MAJOR a fresh missing-critique); 9 → 6 → 3 pairs is progress
    val going = ItemState(
      history = List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), Some(com.histmersion.api.automation.Managers.RunMark.Before(2)), com.histmersion.api.automation.Managers.RunMark.After(2), pairsBefore = Some(com.histmersion.api.automation.Managers.RunMark.PairsBefore(9)), pairsAfter = Some(com.histmersion.api.automation.Managers.RunMark.PairsAfter(6)))),
      before = Some(com.histmersion.api.automation.Managers.ItemState.Before(2)),
      pairsBefore = Some(com.histmersion.api.automation.Managers.ItemState.PairsBefore(6))
    )
    eval(st = going, attempts = 2, fs = facts(2, onlyMissing = true, pairs = Some(3))) match
      case Next.Again(_, st, note) =>
        assert(note.contains("3 open critique pairs"), note)
        assertEquals(st.history.last.pairsAfter, Some(3))
      case other => fail(other.toString)
    // one run is no window: the first run's churn never stops it
    assert(
      eval(st = ItemState(before = Some(com.histmersion.api.automation.Managers.ItemState.Before(2)), pairsBefore = Some(com.histmersion.api.automation.Managers.ItemState.PairsBefore(4))), fs = facts(2, onlyMissing = true, pairs = Some(4)))
        .isInstanceOf[Next.Again]
    )
    // counts unknown (runs evaluated before the checklist): no evidence of a stall
    assert(!pairsStalled(List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1")), RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r2"))), com.histmersion.domain.ManagerPolicy.ReviewSettings.NoProgressRuns(2)))
    // a rules MAJOR among them: the open-MAJOR rule applies as before
    val flat = ItemState(
      history = List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), Some(com.histmersion.api.automation.Managers.RunMark.Before(3)), com.histmersion.api.automation.Managers.RunMark.After(3), pairsBefore = Some(com.histmersion.api.automation.Managers.RunMark.PairsBefore(9)), pairsAfter = Some(com.histmersion.api.automation.Managers.RunMark.PairsAfter(6)))),
      before = Some(com.histmersion.api.automation.Managers.ItemState.Before(3)),
      pairsBefore = Some(com.histmersion.api.automation.Managers.ItemState.PairsBefore(6))
    )
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
    val hist = ItemState(history = List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), Some(com.histmersion.api.automation.Managers.RunMark.Before(3)), com.histmersion.api.automation.Managers.RunMark.After(3))), before = Some(com.histmersion.api.automation.Managers.ItemState.Before(3)))
    assert(stopped(eval(st = hist, attempts = 2, fs = facts(3))).contains("did not go down over 2 runs"))
    // 4 → 3 → 2: progress, so again
    val better = ItemState(history = List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), Some(com.histmersion.api.automation.Managers.RunMark.Before(4)), com.histmersion.api.automation.Managers.RunMark.After(3))), before = Some(com.histmersion.api.automation.Managers.ItemState.Before(3)))
    assert(eval(st = better, attempts = 2, fs = facts(2)).isInstanceOf[Next.Again])
    assert(!noProgress(List(RunMark(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), Some(com.histmersion.api.automation.Managers.RunMark.Before(3)), com.histmersion.api.automation.Managers.RunMark.After(3))), com.histmersion.domain.ManagerPolicy.ReviewSettings.NoProgressRuns(2))) // one run is no window of two

  test("a stop with no ledger line still files a task naming the article and the run"):
    eval(fs = facts(1, escalated = 1), task = None) match
      case Next.Stop(_, Some(t), _) =>
        assert(t.startsWith("review manager stopped [[tin]]: ") && t.contains("run-1234"), t)
      case other => fail(other.toString)

  test("failed runs back off exponentially; the max-failures-th stops the item with a task"):
    eval(status = RunState.Failed, fs = None, error = Some("boom")) match
      case Next.Retry(nb, n, note) =>
        assertEquals((nb, n), (now.plusSeconds(15 * 60), 1)); assert(note.contains("boom"))
      case other => fail(other.toString)
    eval(status = RunState.Failed, fs = None, failures = 1) match
      case Next.Retry(nb, 2, _) => assertEquals(nb, now.plusSeconds(30 * 60))
      case other                => fail(other.toString)
    eval(status = RunState.Failed, fs = None, failures = 2) match
      case Next.Stop(o, Some(t), _) => assert(o.contains("3 failed runs") && t.contains("3 failed runs"), (o, t))
      case other                    => fail(other.toString)
    assertEquals(ManagerPolicy.backoff(com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), com.histmersion.domain.ManagerPolicy.Next.Retry.Failures(10)).toMinutes, 360L)
    assertEquals(ManagerPolicy.backoff(com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), com.histmersion.domain.ManagerPolicy.Next.Retry.Failures(1)).toMinutes, 15L)

  test("a cancelled run stops the item without a task"):
    assert(eval(status = RunState.Cancelled, fs = None) match
      case Next.Stop(_, None, _) => true
      case _                     => false)

  test("facts from a ledger: open majors, reopen counts, escalation, whether every open MAJOR is a missing-critique"):
    val run = "run-a"
    val sk = f("S1", Reviewer.Skeptic, MissingCritique)
    val sk2 = f("S2", Reviewer.Skeptic, MissingCritique)
    val l1 = absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("tin")), List(sk), run, com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    val l2 = applyChecks(
      applyFix(l1, Fix(com.histmersion.domain.ReviewArticle.Fix.Body("b"), Nil, List(com.histmersion.domain.ReviewArticle.Finding.Id("S1")), Nil, Nil, Nil, com.histmersion.domain.ReviewArticle.Fix.Notes(""), com.histmersion.domain.ReviewArticle.Fix.WordsBefore(1), com.histmersion.domain.ReviewArticle.Fix.WordsAfter(1)), run, com.histmersion.domain.ReviewLedger.ApplyFixRound(1)),
      Reviewer.Skeptic,
      List(Check(com.histmersion.domain.ReviewArticle.Check.Id("S1"), fixed = com.histmersion.domain.ReviewArticle.Check.Fixed(true), com.histmersion.domain.ReviewArticle.Check.Quote("q"), com.histmersion.domain.ReviewArticle.Check.Note(""))),
      run,
      com.histmersion.domain.ReviewLedger.ApplyChecksRound(2)
    )
    val l3 = absorb(
      l2,
      List(sk2.modify(_.quote).setTo("another passage entirely").modify(_.where).setTo("elsewhere").modify(_.problem).setTo("omits X's critique")),
      run,
      com.histmersion.domain.ReviewLedger.Closure.Round(3)
    )._1
    val fx = ReviewFacts.of(l3, Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.ReviewFacts.(4)))
    assertEquals((fx.openMajors, fx.onlyMissingCritiques, fx.openPairs), (1, true, Some(4)))
    // a rules MAJOR among them: not only missing critiques
    val mixed = absorb(l3, List(f("R1", Reviewer.Rules)), run, com.histmersion.domain.ReviewLedger.Closure.Round(3))._1
    assert(!ReviewFacts.of(mixed).onlyMissingCritiques)
    // nothing open: not "only missing critiques" either
    assert(!ReviewFacts.of(l2).onlyMissingCritiques)
    // a missing-critique of any pass or run counts (the pairs decide whether it is churn)
    assert(ReviewFacts.of(l1).onlyMissingCritiques)

  test("selection order: open MAJORs, never reviewed, stale; an article without a filter gets infer-filter first"):
    val picks = select(
      majors = List("a" -> 3, "b" -> 1),
      unreviewed = List(com.histmersion.domain.research.Slug("c"), com.histmersion.domain.research.Slug("a")),
      stale = List("d" -> "old"),
      batch = com.histmersion.domain.ManagerPolicy.ReviewSettings.RefillBatch(10),
      inferFilter = com.histmersion.domain.ManagerPolicy.ReviewSettings.InferFilter(true),
      hasFilter = Set("a", "d"),
      inferredBefore = Set("c")
    )
    assertEquals(
      picks.map(p => (p.kind, p.target, p.priority)),
      List(
        (ArticleReview, "a", MajorsPriority),
        (InferFilter, "b", MajorsPriority),
        (ArticleReview, "b", MajorsPriority),
        (ArticleReview, "c", UnreviewedPriority), // its filter was inferred before: not again
        (ArticleReview, "d", StalePriority)
      )
    )
    assert(picks.find(_.target == "d").get.why.startsWith("stale"))
    // the batch counts reviews; inferFilter off adds none
    assertEquals(
      select(List("a" -> 1, "b" -> 1), List(com.histmersion.domain.research.Slug("c")), Nil, com.histmersion.domain.ManagerPolicy.ReviewSettings.RefillBatch(2), inferFilter = com.histmersion.domain.ManagerPolicy.ReviewSettings.InferFilter(false), _ => false, _ => false).map(_.target),
      List("a", "b")
    )

  test("staleWhy: older than N days, or its filter gained sources; 0 days = never"):
    val at = now.minus(java.time.Duration.ofDays(100))
    assertEquals(staleWhy(at, now, com.histmersion.domain.ManagerPolicy.ReviewSettings.StaleAfterDays(90), None, None), Some("last converged over 90 days ago"))
    assertEquals(staleWhy(now, now, com.histmersion.domain.ManagerPolicy.ReviewSettings.StaleAfterDays(90), Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.FilterHits(4)), Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.FilterHits(6))), Some("its filter gained 2 sources"))
    assertEquals(staleWhy(now, now, com.histmersion.domain.ManagerPolicy.ReviewSettings.StaleAfterDays(90), Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.FilterHits(6)), Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.FilterHits(6))), None)
    assertEquals(staleWhy(at, now, com.histmersion.domain.ManagerPolicy.ReviewSettings.StaleAfterDays(0), Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.FilterHits(1)), Some(com.histmersion.domain.research.workflows.ReviewManagerPolicy.FilterHits(9))), None)

  test("settings resolve over research's defaults; the defaults show every review key"):
    val d = ReviewManagerDefaults(com.histmersion.domain.ReviewManagerDefaults.MaxRuns(2), com.histmersion.domain.ReviewManagerDefaults.Rounds(3), com.histmersion.domain.ReviewManagerDefaults.MaxRunsPerArticle(3), com.histmersion.domain.ReviewManagerDefaults.NoProgressRuns(2), com.histmersion.domain.ReviewManagerDefaults.ReopenLimit(2), com.histmersion.domain.ReviewManagerDefaults.CooldownMinutes(30), com.histmersion.domain.ReviewManagerDefaults.LowWater(2), com.histmersion.domain.ReviewManagerDefaults.RefillBatch(10), com.histmersion.domain.ReviewManagerDefaults.StaleAfterDays(90), inferFilter = com.histmersion.domain.ReviewManagerDefaults.InferFilter(true), extendMinNew = com.histmersion.domain.ReviewManagerDefaults.ExtendMinNew(0))
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
    val rs = settings(ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(3))), d, m)
    assertEquals((rs.maxRuns, rs.maxOpen, rs.rounds, rs.maxFailures), (3, 6, 3, 3))
    assertEquals(settings(ManagerConfig(), d, m).staleAfterDays, 90)
    assertEquals(defaults(d, m).merge(ManagerConfig(rounds = Some(com.histmersion.api.automation.Managers.ManagerConfig.Rounds(5)))).rounds, Some(5))
