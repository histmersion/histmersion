package com.histmersion
package domain.research.workflows
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

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
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.Run
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Agents
import com.histmersion.domain.ReviewLedger
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.ManagerPolicy.Item

/** The review manager's pure rules (docs/research-process-managers.md §3.4): every stop rule over the review ledger,
  * the settings over research's defaults, the selection and when an article is stale.
  */
class ReviewManagerPolicySuite extends munit.FunSuite:

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

  private def f(
      id: Finding.Id,
      r: Reviewer,
      kind: Finding.Kind = Finding.Kind("synth"),
      sev: Severity = Severity.Major
  ) =
    Finding(
      id,
      r,
      sev,
      kind,
      Finding.Where(s"where ${id.value}"),
      Finding.Quote(s"quote of ${id.value}"),
      Finding.Problem(s"problem ${id.value}"),
      Finding.Fix("fix")
    )

  private def facts(
      open: ReviewFacts.OpenMajors,
      reopened: ReviewFacts.MaxReopened = ReviewFacts.MaxReopened(0),
      escalated: ReviewFacts.Escalated = ReviewFacts.Escalated(0),
      onlyMissing: ReviewFacts.OnlyMissingCritiques = ReviewFacts.OnlyMissingCritiques(false),
      pairs: Option[ReviewFacts.OpenPairs] = None
  ) =
    Some(ReviewFacts(open, reopened, escalated, onlyMissing, pairs))

  private def eval(
      st: ItemState = ItemState(),
      attempts: ManagerPolicy.Attempts = ManagerPolicy.Attempts(1),
      status: RunState = RunState.Done,
      fs: Option[ReviewFacts] = facts(ReviewFacts.OpenMajors(2)),
      task: Option[Item.Task] = Some(Item.Task("review-article [[tin]]: 2 open MAJORs — …")),
      failures: Next.Retry.Failures = Next.Retry.Failures(0),
      error: Option[Problem] = None
  ) =
    evaluateReview(
      s,
      Slug("tin"),
      attempts,
      failures,
      st,
      Run.Id("ccf9dbcf-a45c-e9ef-aef8-3e7aff5d2a57"),
      status,
      error,
      fs,
      task,
      now
    )

  test("no open MAJOR: done, converged after k runs"):
    eval(fs = facts(ReviewFacts.OpenMajors(0)), attempts = ManagerPolicy.Attempts(2)) match
      case Next.Done(o, st) =>
        assertEquals(o, "converged after 2 runs")
        assertEquals(st.history.map(_.after), List(0))
      case other => fail(other.toString)

  test("open MAJORs, under every limit: again after the cooldown, the run marked in the history"):
    eval(st = ItemState(before = Some(Managers.ItemState.Before(5)))) match
      case Next.Again(nb, st, note) =>
        assertEquals(nb, Some(now.plusSeconds(1800)))
        assertEquals(
          st.history,
          List(
            RunMark(
              Agents.RunInfo.Id("ccf9dbcf-a45c-e9ef-aef8-3e7aff5d2a57"),
              Some(Managers.RunMark.Before(5)),
              Managers.RunMark.After(2)
            )
          )
        )
        assertEquals(st.before, None)
        assert(note.value.contains("2 open MAJORs"), note)
      case other => fail(other.toString)

  test("stop rules: omission churn — only missing-critique MAJORs AND the open critique pairs did not go down"):
    // two runs, 5 open pairs before the first, 5 after the second: stalled
    val stalled = ItemState(
      history = List(
        RunMark(
          Agents.RunInfo.Id("r1"),
          Some(Managers.RunMark.Before(2)),
          Managers.RunMark.After(2),
          pairsBefore = Some(Managers.RunMark.PairsBefore(5)),
          pairsAfter = Some(Managers.RunMark.PairsAfter(5))
        )
      ),
      before = Some(Managers.ItemState.Before(2)),
      pairsBefore = Some(Managers.ItemState.PairsBefore(5))
    )
    eval(
      st = stalled,
      attempts = ManagerPolicy.Attempts(2),
      fs = facts(
        ReviewFacts.OpenMajors(2),
        onlyMissing = ReviewFacts.OnlyMissingCritiques(true),
        pairs = Some(ReviewFacts.OpenPairs(5))
      )
    ) match
      case Next.Stop(o, Some(t), st) =>
        assert(o.value.contains("omission churn") && o.value.contains("did not go down over 2 runs (now 5)"), o)
        assert(t.value.startsWith("review-article [[tin]]: 2 open MAJORs"), t)
        assert(t.value.contains("review manager stopped: omission churn"), t)
        assertEquals(
          st.history.last,
          RunMark(
            Agents.RunInfo.Id("ccf9dbcf-a45c-e9ef-aef8-3e7aff5d2a57"),
            Some(Managers.RunMark.Before(2)),
            Managers.RunMark.After(2),
            churn = Managers.RunMark.Churn(true),
            pairsBefore = Some(Managers.RunMark.PairsBefore(5)),
            pairsAfter = Some(Managers.RunMark.PairsAfter(5))
          )
        )
      case other => fail(other.toString)

  test("omission churn no longer stops while the open critique pairs go down — not even on flat open MAJORs"):
    // the old rule stopped this run (every open MAJOR a fresh missing-critique); 9 → 6 → 3 pairs is progress
    val going = ItemState(
      history = List(
        RunMark(
          Agents.RunInfo.Id("r1"),
          Some(Managers.RunMark.Before(2)),
          Managers.RunMark.After(2),
          pairsBefore = Some(Managers.RunMark.PairsBefore(9)),
          pairsAfter = Some(Managers.RunMark.PairsAfter(6))
        )
      ),
      before = Some(Managers.ItemState.Before(2)),
      pairsBefore = Some(Managers.ItemState.PairsBefore(6))
    )
    eval(
      st = going,
      attempts = ManagerPolicy.Attempts(2),
      fs = facts(
        ReviewFacts.OpenMajors(2),
        onlyMissing = ReviewFacts.OnlyMissingCritiques(true),
        pairs = Some(ReviewFacts.OpenPairs(3))
      )
    ) match
      case Next.Again(_, st, note) =>
        assert(note.value.contains("3 open critique pairs"), note)
        assertEquals(st.history.last.pairsAfter, Some(3))
      case other => fail(other.toString)
    // one run is no window: the first run's churn never stops it
    assert(
      eval(
        st =
          ItemState(before = Some(Managers.ItemState.Before(2)), pairsBefore = Some(Managers.ItemState.PairsBefore(4))),
        fs = facts(
          ReviewFacts.OpenMajors(2),
          onlyMissing = ReviewFacts.OnlyMissingCritiques(true),
          pairs = Some(ReviewFacts.OpenPairs(4))
        )
      )
        .isInstanceOf[Next.Again]
    )
    // counts unknown (runs evaluated before the checklist): no evidence of a stall
    assert(
      !pairsStalled(
        List(RunMark(Agents.RunInfo.Id("r1")), RunMark(Agents.RunInfo.Id("r2"))),
        ReviewSettings.NoProgressRuns(2)
      )
    )
    // a rules MAJOR among them: the open-MAJOR rule applies as before
    val flat = ItemState(
      history = List(
        RunMark(
          Agents.RunInfo.Id("r1"),
          Some(Managers.RunMark.Before(3)),
          Managers.RunMark.After(3),
          pairsBefore = Some(Managers.RunMark.PairsBefore(9)),
          pairsAfter = Some(Managers.RunMark.PairsAfter(6))
        )
      ),
      before = Some(Managers.ItemState.Before(3)),
      pairsBefore = Some(Managers.ItemState.PairsBefore(6))
    )
    assert(
      eval(
        st = flat,
        attempts = ManagerPolicy.Attempts(2),
        fs = facts(ReviewFacts.OpenMajors(3), pairs = Some(ReviewFacts.OpenPairs(3)))
      ).isInstanceOf[Next.Stop]
    )
    // max runs per article still stops it
    assert(
      eval(
        st = going,
        attempts = ManagerPolicy.Attempts(3),
        fs = facts(
          ReviewFacts.OpenMajors(2),
          onlyMissing = ReviewFacts.OnlyMissingCritiques(true),
          pairs = Some(ReviewFacts.OpenPairs(3))
        )
      ).isInstanceOf[Next.Stop]
    )

  test("stop rules: escalated, reopened to the limit, max runs per article, no progress"):
    def stopped(n: Next) = n match
      case Next.Stop(o, t, _) => assert(t.isDefined); o.value
      case other              => fail(other.toString)
    assert(
      stopped(eval(fs = facts(ReviewFacts.OpenMajors(1), escalated = ReviewFacts.Escalated(1)))).contains("escalated")
    )
    assert(
      stopped(eval(fs = facts(ReviewFacts.OpenMajors(1), reopened = ReviewFacts.MaxReopened(2))))
        .contains("reopened 2 times")
    )
    assert(stopped(eval(attempts = ManagerPolicy.Attempts(3))).contains("max-runs-per-article 3"))
    // two runs, open MAJORs 3 → 3 → 3: no progress over the window of 2
    val hist = ItemState(
      history = List(RunMark(Agents.RunInfo.Id("r1"), Some(Managers.RunMark.Before(3)), Managers.RunMark.After(3))),
      before = Some(Managers.ItemState.Before(3))
    )
    assert(
      stopped(eval(st = hist, attempts = ManagerPolicy.Attempts(2), fs = facts(ReviewFacts.OpenMajors(3))))
        .contains("did not go down over 2 runs")
    )
    // 4 → 3 → 2: progress, so again
    val better = ItemState(
      history = List(RunMark(Agents.RunInfo.Id("r1"), Some(Managers.RunMark.Before(4)), Managers.RunMark.After(3))),
      before = Some(Managers.ItemState.Before(3))
    )
    assert(
      eval(st = better, attempts = ManagerPolicy.Attempts(2), fs = facts(ReviewFacts.OpenMajors(2)))
        .isInstanceOf[Next.Again]
    )
    assert(
      !noProgress(
        List(RunMark(Agents.RunInfo.Id("r1"), Some(Managers.RunMark.Before(3)), Managers.RunMark.After(3))),
        ReviewSettings.NoProgressRuns(2)
      )
    ) // one run is no window of two

  test("a stop with no ledger line still files a task naming the article and the run"):
    eval(fs = facts(ReviewFacts.OpenMajors(1), escalated = ReviewFacts.Escalated(1)), task = None) match
      case Next.Stop(_, Some(t), _) =>
        assert(t.value.startsWith("review manager stopped [[tin]]: ") && t.value.contains("ccf9dbcf"), t)
      case other => fail(other.toString)

  test("failed runs back off exponentially; the max-failures-th stops the item with a task"):
    eval(status = RunState.Failed, fs = None, error = Some(Problem.Unclassified(Legacy.Text("boom")))) match
      case Next.Retry(nb, n, note) =>
        assertEquals((nb, n), (now.plusSeconds(15 * 60), 1)); assert(note.value.contains("boom"))
      case other => fail(other.toString)
    eval(status = RunState.Failed, fs = None, failures = Next.Retry.Failures(1)) match
      case Next.Retry(nb, n, _) if n.value == 2 => assertEquals(nb, now.plusSeconds(30 * 60))
      case other                                => fail(other.toString)
    eval(status = RunState.Failed, fs = None, failures = Next.Retry.Failures(2)) match
      case Next.Stop(o, Some(t), _) =>
        assert(o.value.contains("3 failed runs") && t.value.contains("3 failed runs"), (o, t))
      case other => fail(other.toString)
    assertEquals(
      ManagerPolicy
        .backoff(ReviewSettings.BackoffMinutes(15), ReviewSettings.MaxBackoffMinutes(360), Next.Retry.Failures(10))
        .toMinutes,
      360L
    )
    assertEquals(
      ManagerPolicy
        .backoff(ReviewSettings.BackoffMinutes(15), ReviewSettings.MaxBackoffMinutes(360), Next.Retry.Failures(1))
        .toMinutes,
      15L
    )

  test("a cancelled run stops the item without a task"):
    assert(eval(status = RunState.Cancelled, fs = None) match
      case Next.Stop(_, None, _) => true
      case _                     => false)

  test("facts from a ledger: open majors, reopen counts, escalation, whether every open MAJOR is a missing-critique"):
    val run = Run.Id("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59")
    val sk = f(Finding.Id("S1"), Reviewer.Skeptic, MissingCritique)
    val sk2 = f(Finding.Id("S2"), Reviewer.Skeptic, MissingCritique)
    val l1 = absorb(Ledger(ReviewLedger.Ledger.Slug("tin")), List(sk), run, ReviewLedger.Closure.Round(1))._1
    val l2 = applyChecks(
      applyFix(
        l1,
        Fix(
          ReviewArticle.Fix.Body("b"),
          Nil,
          List(Finding.Id("S1")),
          Nil,
          Nil,
          Nil,
          ReviewArticle.Fix.Notes(""),
          ReviewArticle.Fix.WordsBefore(1),
          ReviewArticle.Fix.WordsAfter(1)
        ),
        run,
        ReviewLedger.ApplyFixRound(1)
      ),
      Reviewer.Skeptic,
      List(
        Check(
          ReviewArticle.Check.Id("S1"),
          fixed = ReviewArticle.Check.Fixed(true),
          ReviewArticle.Check.Quote("q"),
          ReviewArticle.Check.Note("")
        )
      ),
      run,
      ReviewLedger.ApplyChecksRound(2)
    )
    val l3 = absorb(
      l2,
      List(
        sk2
          .modify(_.quote)
          .setTo(Finding.Quote("another passage entirely"))
          .modify(_.where)
          .setTo(Finding.Where("elsewhere"))
          .modify(_.problem)
          .setTo(Finding.Problem("omits X's critique"))
      ),
      run,
      ReviewLedger.Closure.Round(3)
    )._1
    val fx = ReviewFacts.of(l3, Some(ReviewFacts.OpenPairs(4)))
    assertEquals((fx.openMajors, fx.onlyMissingCritiques, fx.openPairs), (1, true, Some(4)))
    // a rules MAJOR among them: not only missing critiques
    val mixed = absorb(l3, List(f(Finding.Id("R1"), Reviewer.Rules)), run, ReviewLedger.Closure.Round(3))._1
    assert(!ReviewFacts.of(mixed).onlyMissingCritiques.value)
    // nothing open: not "only missing critiques" either
    assert(!ReviewFacts.of(l2).onlyMissingCritiques.value)
    // a missing-critique of any pass or run counts (the pairs decide whether it is churn)
    assert(ReviewFacts.of(l1).onlyMissingCritiques.value)

  test("selection order: open MAJORs, never reviewed, stale; an article without a filter gets infer-filter first"):
    val picks = select(
      majors = List(Slug("a") -> ReviewFacts.OpenMajors(3), Slug("b") -> ReviewFacts.OpenMajors(1)),
      unreviewed = List(Slug("c"), Slug("a")),
      stale = List(Slug("d") -> Staleness.Old(ReviewSettings.StaleAfterDays(90))),
      batch = ReviewSettings.RefillBatch(10),
      inferFilter = ReviewSettings.InferFilter(true),
      hasFilter = Set(Slug("a"), Slug("d")),
      inferredBefore = Set(Slug("c"))
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
    assertEquals(picks.find(_.target == Slug("d")).get.why.said, "stale: last converged over 90 days ago")
    assertEquals(picks.find(_.target == Slug("a")).get.why.said, "3 open MAJORs")
    // the batch counts reviews; inferFilter off adds none
    assertEquals(
      select(
        List(Slug("a") -> ReviewFacts.OpenMajors(1), Slug("b") -> ReviewFacts.OpenMajors(1)),
        List(Slug("c")),
        Nil,
        ReviewSettings.RefillBatch(2),
        inferFilter = ReviewSettings.InferFilter(false),
        _ => false,
        _ => false
      ).map(_.target),
      List("a", "b")
    )

  test("staleWhy: older than N days, or its filter gained sources; 0 days = never"):
    val at = now.minus(java.time.Duration.ofDays(100))
    assertEquals(
      staleWhy(at, now, ReviewSettings.StaleAfterDays(90), None, None).map(_.said),
      Some("last converged over 90 days ago")
    )
    assertEquals(
      staleWhy(
        now,
        now,
        ReviewSettings.StaleAfterDays(90),
        Some(ReviewManagerPolicy.FilterHits(4)),
        Some(ReviewManagerPolicy.FilterHits(6))
      ).map(_.said),
      Some("its filter gained 2 sources")
    )
    assertEquals(
      staleWhy(
        now,
        now,
        ReviewSettings.StaleAfterDays(90),
        Some(ReviewManagerPolicy.FilterHits(6)),
        Some(ReviewManagerPolicy.FilterHits(6))
      ),
      None
    )
    assertEquals(
      staleWhy(
        at,
        now,
        ReviewSettings.StaleAfterDays(0),
        Some(ReviewManagerPolicy.FilterHits(1)),
        Some(ReviewManagerPolicy.FilterHits(9))
      ),
      None
    )

  test("settings resolve over research's defaults; the defaults show every review key"):
    val d = ReviewManagerDefaults(
      ReviewManagerDefaults.MaxRuns(2),
      ReviewManagerDefaults.Rounds(3),
      ReviewManagerDefaults.MaxRunsPerArticle(3),
      ReviewManagerDefaults.NoProgressRuns(2),
      ReviewManagerDefaults.ReopenLimit(2),
      ReviewManagerDefaults.CooldownMinutes(30),
      ReviewManagerDefaults.LowWater(2),
      ReviewManagerDefaults.RefillBatch(10),
      ReviewManagerDefaults.StaleAfterDays(90),
      inferFilter = ReviewManagerDefaults.InferFilter(true),
      extendMinNew = ReviewManagerDefaults.ExtendMinNew(0)
    )
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
    val rs = settings(ManagerConfig(maxRuns = Some(Managers.ManagerConfig.MaxRuns(3))), d, m)
    assertEquals((rs.maxRuns, rs.maxOpen, rs.rounds, rs.maxFailures), (3, 6, 3, 3))
    assertEquals(settings(ManagerConfig(), d, m).staleAfterDays, 90)
    assertEquals(defaults(d, m).merge(ManagerConfig(rounds = Some(Managers.ManagerConfig.Rounds(5)))).rounds, Some(5))
