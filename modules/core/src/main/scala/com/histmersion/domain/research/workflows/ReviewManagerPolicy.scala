package com.histmersion.domain.research.workflows

import com.histmersion.api.Plural

import com.histmersion.api.Managers.*
import com.histmersion.domain.{ManagerDefaults, ManagerPolicy, ReviewArticle, ReviewManagerDefaults}
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings}
import com.histmersion.domain.ReviewLedger.Ledger
import com.histmersion.values.RunState

import java.time.{Duration, Instant}

/** The review manager's pure rules (docs/research-process-managers.md §3.4): its settings over research's defaults,
  * what to do with a review item after its run settled — the stop rules over the article's review ledger —, the
  * selection that refills its queue and when a converged article is due again. No I/O: the kind reads the facts and the
  * supervisor writes the result.
  */
object ReviewManagerPolicy:

  /** A review manager's settings in effect: its saved config over research's defaults (`d`) and the engine's (`m`). */
  def settings(c: ManagerConfig, d: ReviewManagerDefaults, m: ManagerDefaults): ReviewSettings =
    val maxRuns = math.max(0, c.maxRuns.getOrElse(d.maxRuns))
    ReviewSettings(
      maxRuns,
      math.max(maxRuns, c.maxOpen.getOrElse(2 * maxRuns)),
      math.max(1, c.rounds.getOrElse(d.rounds)),
      math.max(1, c.maxRunsPerArticle.getOrElse(d.maxRunsPerArticle)),
      math.max(1, c.noProgressRuns.getOrElse(d.noProgressRuns)),
      math.max(1, c.reopenLimit.getOrElse(d.reopenLimit)),
      math.max(0, c.cooldownMinutes.getOrElse(d.cooldownMinutes)),
      math.max(0, c.lowWater.getOrElse(d.lowWater)),
      math.max(0, c.refillBatch.getOrElse(d.refillBatch)),
      math.max(1, c.maxFailures.getOrElse(m.maxFailures)),
      math.max(1, m.backoffMinutes),
      math.max(1, m.maxBackoffMinutes),
      math.max(0, c.staleAfterDays.getOrElse(d.staleAfterDays)),
      c.inferFilter.getOrElse(d.inferFilter)
    )

  /** The defaults as a config (what `effective` shows for an unset key). */
  def defaults(d: ReviewManagerDefaults, m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(d.maxRuns),
      maxOpen = Some(2 * d.maxRuns),
      rounds = Some(d.rounds),
      maxRunsPerArticle = Some(d.maxRunsPerArticle),
      noProgressRuns = Some(d.noProgressRuns),
      reopenLimit = Some(d.reopenLimit),
      cooldownMinutes = Some(d.cooldownMinutes),
      lowWater = Some(d.lowWater),
      refillBatch = Some(d.refillBatch),
      maxFailures = Some(m.maxFailures),
      staleAfterDays = Some(d.staleAfterDays),
      inferFilter = Some(d.inferFilter),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m),
      extendMinNew = Some(d.extendMinNew)
    )

  /** What a settled run of the review manager left: the ledger's open MAJORs, the most often reopened, the escalated
    * ones, whether every open MAJOR is a `missing-critique` finding, and the open pairs of the article's critique
    * checklist now (None when not known).
    */
  final case class ReviewFacts(
      openMajors: Int,
      maxReopened: Int,
      escalated: Int,
      onlyMissingCritiques: Boolean,
      openPairs: Option[Int] = None
  )

  object ReviewFacts:
    /** The facts of `l`, with the checklist's open pairs counted by the caller. */
    def of(l: Ledger, openPairs: Option[Int] = None): ReviewFacts =
      val majors = l.openMajors
      ReviewFacts(
        majors.size,
        majors.map(_.reopened).maxOption.getOrElse(0),
        majors.count(_.finding.escalated),
        majors.nonEmpty && majors.forall(e => ReviewArticle.isMissingCritique(e.finding)),
        openPairs
      )

  /** The open MAJORs over the last `n` runs went from `before` (when the window's first run was dispatched) to `after`
    * without going down.
    */
  def noProgress(history: List[RunMark], n: Int): Boolean =
    val window = history.takeRight(n)
    window.size >= n && {
      val start = window.head.before.getOrElse(Int.MaxValue)
      window.last.after >= start
    }

  /** The open pairs of the critique checklist did not go down over the last `n` runs: from `pairsBefore` of the
    * window's first run to `pairsAfter` of its last. Unknown counts (a run evaluated before the checklist existed) are
    * no evidence of a stall.
    */
  def pairsStalled(history: List[RunMark], n: Int): Boolean =
    val window = history.takeRight(n)
    window.size >= n && window.head.pairsBefore.zip(window.last.pairsAfter).exists((start, end) => end >= start)

  /** Evaluate a review item after its run `run` settled `status` (a terminal state). `attempts` = the runs it has had
    * (this one included); `facts` = what the ledger says now (None for a failed or cancelled run); `taskText` = the
    * ledger's own task line for a stop. The stop rules, in order: omission churn, a finding escalated or reopened
    * `reopenLimit` times, `maxRunsPerArticle` runs, no progress over `noProgressRuns` runs.
    *
    * OMISSION CHURN (§6.8 of docs/research-review-convergence.md): every open MAJOR is a missing-critique finding AND
    * the open pairs of the critique checklist did not go down over `noProgressRuns` runs. While those pairs go down the
    * runs are finishing a closed list, so neither churn nor the open-MAJOR count stops the item (a missing-critique
    * MAJOR per open pair keeps that count flat by design); `maxRunsPerArticle` still does.
    */
  def evaluateReview(
      s: ReviewSettings,
      slug: String,
      attempts: Int,
      failures: Int,
      state: ItemState,
      run: String,
      status: RunState,
      error: Option[String],
      facts: Option[ReviewFacts],
      taskText: Option[String],
      now: Instant
  ): Next =
    status match
      case RunState.Done =>
        val f = facts.getOrElse(ReviewFacts(0, 0, 0, onlyMissingCritiques = false))
        val mark = RunMark(
          run,
          state.before,
          f.openMajors,
          f.maxReopened,
          f.escalated,
          f.onlyMissingCritiques,
          state.pairsBefore,
          f.openPairs
        )
        val st = state.copy(history = state.history :+ mark, before = None, pairsBefore = None)
        val churn = f.onlyMissingCritiques && pairsStalled(st.history, s.noProgressRuns)
        val pairs = f.openPairs.fold("")(n => s", ${Plural.count(n, "open critique pair")}")
        def stop(why: String) =
          val task = taskText.fold(s"review manager stopped [[$slug]]: $why (agent run ${run.take(8)})")(t =>
            s"$t — review manager stopped: $why"
          )
          Next.Stop(s"stopped: $why", Some(task.take(2000)), st)
        if f.openMajors == 0 then Next.Done(s"converged after ${Plural.count(attempts, "run")}", st)
        else if churn then
          stop(
            s"omission churn — every open MAJOR (${f.openMajors}) is a missing-critique finding and the open critique " +
              s"pairs did not go down over ${Plural.count(s.noProgressRuns, "run")} (now ${f.openPairs.getOrElse(0)})"
          )
        else if f.escalated > 0 then
          stop(s"${Plural.count(f.escalated, "MAJOR")} raised again after the fixer rejected it (escalated)")
        else if f.maxReopened >= s.reopenLimit then
          stop(s"a finding was reopened ${f.maxReopened} times (reopen-limit ${s.reopenLimit})")
        else if attempts >= s.maxRunsPerArticle then
          stop(
            s"${Plural.count(f.openMajors, "open MAJOR")} after ${Plural.count(attempts, "run")} (max-runs-per-article ${s.maxRunsPerArticle})"
          )
        else if !f.onlyMissingCritiques && noProgress(st.history, s.noProgressRuns) then
          stop(s"the open MAJORs did not go down over ${Plural.count(s.noProgressRuns, "run")} (now ${f.openMajors})")
        else
          Next.Again(
            Option.when(s.cooldownMinutes > 0)(now.plusSeconds(s.cooldownMinutes.toLong * 60L)),
            st,
            s"${Plural.count(f.openMajors, "open MAJOR")}$pairs after run $attempts; again" +
              (if s.cooldownMinutes > 0 then s" after a ${s.cooldownMinutes}-minute cooldown" else "")
          )
      case RunState.Cancelled =>
        Next.Stop("stopped: its run was cancelled", None, state.copy(before = None))
      case _ => // failed (or anything else terminal)
        val n = failures + 1
        val why = error.filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
        if n >= s.maxFailures then
          Next.Stop(
            s"stopped: ${Plural.count(n, "failed run")}$why",
            Some(
              s"review manager stopped [[$slug]] after ${Plural.count(n, "failed run")}$why (agent run ${run.take(8)})"
            ),
            state.copy(before = None)
          )
        else
          Next.Retry(
            now.plus(ManagerPolicy.backoff(s.backoffMinutes, s.maxBackoffMinutes, n)),
            n,
            s"run failed ($n of ${s.maxFailures})$why"
          )

  // ── selection (the review manager's refill) ────────────────────────────────────────────────────

  /** One item the selection adds: its kind, target, priority, and why (for the event). */
  final case class Pick(kind: String, target: String, priority: Int, why: String)

  /** The priorities of what the selection picks: open MAJORs, never reviewed, stale (a person's enqueue is 10, a
    * chained review 2 — both above).
    */
  val MajorsPriority = 1
  val UnreviewedPriority = 0
  val StalePriority = -1

  /** The review manager's selection, in its order — articles with open MAJORs (most first), never reviewed ones, then
    * stale ones (converged long ago, or their filter gained sources) — at most `batch` articles. With `inferFilter`, an
    * article that records no coverage filter (`hasFilter`) and never had an infer-filter item (`inferredBefore`) gets
    * one too, at its review's priority: the review waits for it ([[waitsFor]]).
    */
  def select(
      majors: List[(String, Int)],
      unreviewed: List[String],
      stale: List[(String, String)],
      batch: Int,
      inferFilter: Boolean,
      hasFilter: String => Boolean,
      inferredBefore: String => Boolean
  ): List[Pick] =
    val reviews =
      (majors.map((slug, n) => Pick(ArticleReview, slug, MajorsPriority, s"${Plural.count(n, "open MAJOR")}")) ++
        unreviewed.map(slug => Pick(ArticleReview, slug, UnreviewedPriority, "never reviewed")) ++
        stale.map((slug, why) => Pick(ArticleReview, slug, StalePriority, s"stale: $why")))
        .foldLeft(Vector.empty[Pick])((acc, p) => if acc.exists(_.target == p.target) then acc else acc :+ p)
        .take(math.max(0, batch))
        .toList
    reviews.flatMap { p =>
      val infer = Option.when(inferFilter && !hasFilter(p.target) && !inferredBefore(p.target))(
        Pick(InferFilter, p.target, p.priority, "it records no coverage filter")
      )
      infer.toList :+ p
    }

  /** Why a converged article is due again, if it is: its review converged more than `days` days ago, or its filter
    * returns more sources now (`hitsNow`) than when it converged (`hitsThen`). `days` 0 = never.
    */
  def staleWhy(
      convergedAt: Instant,
      now: Instant,
      days: Int,
      hitsThen: Option[Int],
      hitsNow: Option[Int]
  ): Option[String] =
    if days <= 0 then None
    else
      val gained = hitsThen.zip(hitsNow).collect { case (a, b) if b > a => b - a }
      gained
        .map(n => s"its filter gained ${Plural.count(n, "source")}")
        .orElse(
          Option
            .when(!convergedAt.plus(Duration.ofDays(days.toLong)).isAfter(now))(s"last converged over $days days ago")
        )
