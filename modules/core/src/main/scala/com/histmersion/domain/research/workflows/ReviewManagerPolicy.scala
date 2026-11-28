package com.histmersion
package domain.research.workflows

import com.histmersion.api.automation.managers.ManagerConfig
import com.histmersion.domain.automation.managers.ReviewSettings
import com.histmersion.domain.research.workflows.Ledger
import com.histmersion.api.automation.managers.RunMark
import com.histmersion.domain.automation.managers.Next
import com.histmersion.api.automation.managers.ItemState
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.domain.automation.managers.Assignable
import com.histmersion.api.automation.managers.ManagerItem
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.{ManagerDefaults, ManagerPolicy, ReviewArticle, ReviewManagerDefaults}
import com.histmersion.domain.ManagerPolicy.counted

import java.time.{Duration, Instant}
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.Slug
import com.histmersion.api.automation.Agents
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.lint.{tolerated, LintRule}

/** The review manager's pure rules (docs/research-process-managers.md §3.4): its settings over research's defaults,
  * what to do with a review item after its run settled — the stop rules over the article's review ledger —, the
  * selection that refills its queue and when a converged article is due again. No I/O: the kind reads the facts and the
  * supervisor writes the result.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object ReviewManagerPolicy:
  type OpenPairs = OpenPairs.T
  object OpenPairs extends Newtype[Int, Pure]

  /** How many sources an article's coverage filter returns. */
  type FilterHits = FilterHits.T
  object FilterHits extends Newtype[Int, Pure]

  /** A review manager's settings in effect: its saved config over research's defaults (`d`) and the engine's (`m`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def settings(c: ManagerConfig, d: ReviewManagerDefaults, m: ManagerDefaults): ReviewSettings =
    val maxRuns = math.max(0, c.maxRuns.fold(d.maxRuns.value)(_.value))
    ReviewSettings(
      ReviewSettings.MaxRuns(maxRuns),
      ReviewSettings.MaxOpen(math.max(maxRuns, c.maxOpen.fold(2 * maxRuns)(_.value))),
      ReviewSettings.Rounds(math.max(1, c.rounds.fold(d.rounds.value)(_.value))),
      ReviewSettings.MaxRunsPerArticle(math.max(1, c.maxRunsPerArticle.fold(d.maxRunsPerArticle.value)(_.value))),
      ReviewSettings.NoProgressRuns(math.max(1, c.noProgressRuns.fold(d.noProgressRuns.value)(_.value))),
      ReviewSettings.ReopenLimit(math.max(1, c.reopenLimit.fold(d.reopenLimit.value)(_.value))),
      ReviewSettings.CooldownMinutes(math.max(0, c.cooldownMinutes.fold(d.cooldownMinutes.value)(_.value))),
      ReviewSettings.LowWater(math.max(0, c.lowWater.fold(d.lowWater.value)(_.value))),
      ReviewSettings.RefillBatch(math.max(0, c.refillBatch.fold(d.refillBatch.value)(_.value))),
      ReviewSettings.MaxFailures(math.max(1, c.maxFailures.fold(m.maxFailures.value)(_.value))),
      ReviewSettings.BackoffMinutes(math.max(1, m.backoffMinutes.value)),
      ReviewSettings.MaxBackoffMinutes(math.max(1, m.maxBackoffMinutes.value)),
      ReviewSettings.StaleAfterDays(math.max(0, c.staleAfterDays.fold(d.staleAfterDays.value)(_.value))),
      ReviewSettings.InferFilter(c.inferFilter.fold(d.inferFilter.value)(_.inferFilter))
    )

  /** The defaults as a config (what `effective` shows for an unset key). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def defaults(d: ReviewManagerDefaults, m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(ManagerConfig.MaxRuns(d.maxRuns.value)),
      maxOpen = Some(ManagerConfig.MaxOpen(2 * d.maxRuns.value)),
      rounds = Some(ManagerConfig.Rounds(d.rounds.value)),
      maxRunsPerArticle = Some(ManagerConfig.MaxRunsPerArticle(d.maxRunsPerArticle.value)),
      noProgressRuns = Some(ManagerConfig.NoProgressRuns(d.noProgressRuns.value)),
      reopenLimit = Some(ManagerConfig.ReopenLimit(d.reopenLimit.value)),
      cooldownMinutes = Some(ManagerConfig.CooldownMinutes(d.cooldownMinutes.value)),
      lowWater = Some(ManagerConfig.LowWater(d.lowWater.value)),
      refillBatch = Some(ManagerConfig.RefillBatch(d.refillBatch.value)),
      maxFailures = Some(ManagerConfig.MaxFailures(m.maxFailures.value)),
      staleAfterDays = Some(ManagerConfig.StaleAfterDays(d.staleAfterDays.value)),
      inferFilter =
        Some((if d.inferFilter.value then ManagerConfig.InferFilter.Infer else ManagerConfig.InferFilter.Explicit)),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m),
      extendMinNew = Some(ManagerConfig.ExtendMinNew(d.extendMinNew.value))
    )

  /** What a settled run of the review manager left: the ledger's open MAJORs, the most often reopened, the escalated
    * ones, whether every open MAJOR is a `missing-critique` finding, and the open pairs of the article's critique
    * checklist now (None when not known).
    */
  final case class ReviewFacts(
      openMajors: ReviewFacts.OpenMajors,
      maxReopened: ReviewFacts.MaxReopened,
      escalated: ReviewFacts.Escalated,
      onlyMissingCritiques: ReviewFacts.OnlyMissingCritiques,
      openPairs: Option[ReviewFacts.OpenPairs] = None
  )

  object ReviewFacts:
    type OpenMajors = OpenMajors.T
    object OpenMajors extends Newtype[Int, Pure]
    type MaxReopened = MaxReopened.T
    object MaxReopened extends Newtype[Int, Pure]
    type Escalated = Escalated.T
    object Escalated extends Newtype[Int, Pure]
    type OnlyMissingCritiques = OnlyMissingCritiques.T
    object OnlyMissingCritiques extends Newtype[Boolean, Pure]
    type OpenPairs = OpenPairs.T
    object OpenPairs extends Newtype[Int, Pure]

    /** The facts of `l`, with the checklist's open pairs counted by the caller. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def of(l: Ledger, openPairs: Option[OpenPairs] = None): ReviewFacts =
      val majors = l.openMajors
      ReviewFacts(
        OpenMajors(majors.size),
        MaxReopened(majors.map(_.reopened.value).maxOption.getOrElse(0)),
        Escalated(majors.count(_.finding.escalated.value)),
        OnlyMissingCritiques(majors.nonEmpty && majors.forall(e => e.finding.isMissingCritique)),
        openPairs
      )

  /** The open MAJORs over the last `n` runs went from `before` (when the window's first run was dispatched) to `after`
    * without going down.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def noProgress(history: List[RunMark], n: ReviewSettings.NoProgressRuns): Boolean =
    val window = history.takeRight(n.value)
    window.size >= n.value && {
      val start = window.head.before.fold(Int.MaxValue)(_.value)
      window.last.after.value >= start
    }

  /** The open pairs of the critique checklist did not go down over the last `n` runs: from `pairsBefore` of the
    * window's first run to `pairsAfter` of its last. Unknown counts (a run evaluated before the checklist existed) are
    * no evidence of a stall.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pairsStalled(history: List[RunMark], n: ReviewSettings.NoProgressRuns): Boolean =
    val window = history.takeRight(n.value)
    window.size >= n.value && window.head.pairsBefore
      .zip(window.last.pairsAfter)
      .exists((start, end) => end.value >= start.value)

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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def evaluateReview(
      s: ReviewSettings,
      slug: Slug,
      attempts: ManagerPolicy.Attempts,
      failures: Next.Retry.Failures,
      state: ItemState,
      run: Run.Id,
      status: Run.State,
      error: Option[Problem],
      facts: Option[ReviewFacts],
      taskText: Option[Item.Task],
      now: Instant
  ): Next =
    status match
      case Run.State.Done =>
        val f = facts.getOrElse(
          ReviewFacts(
            ReviewFacts.OpenMajors(0),
            ReviewFacts.MaxReopened(0),
            ReviewFacts.Escalated(0),
            onlyMissingCritiques = ReviewFacts.OnlyMissingCritiques(false)
          )
        )
        val mark = RunMark(
          RunInfo.Id(run.value),
          state.before.map(b => RunMark.Before(b.value)),
          RunMark.After(f.openMajors.value),
          RunMark.MaxReopened(f.maxReopened.value),
          RunMark.Escalated(f.escalated.value),
          (if f.onlyMissingCritiques.value then RunMark.Churn.Churn else RunMark.Churn.Progress),
          state.pairsBefore.map(b => RunMark.PairsBefore(b.value)),
          f.openPairs.map(p => RunMark.PairsAfter(p.value))
        )
        val st = state
          .modify(_.history)
          .setTo(state.history :+ mark)
          .modify(_.before)
          .setTo(None)
          .modify(_.pairsBefore)
          .setTo(None)
        val churn = f.onlyMissingCritiques.value && pairsStalled(st.history, s.noProgressRuns)
        val pairs = f.openPairs.fold("")(n => s", ${counted(n.value, "open critique pair")}")
        def stop(why: String) =
          val task = taskText.fold(s"review manager stopped [[$slug]]: $why (agent run ${run.value.take(8)})")(t =>
            s"$t — review manager stopped: $why"
          )
          Next.Stop(Item.Outcome(s"stopped: $why"), Some(Item.Task(task.take(2000))), st)
        if f.openMajors.value == 0 then
          Next.Done(Item.Outcome(s"converged after ${counted(attempts.value, "run")}"), st)
        else if churn then
          stop(
            s"omission churn — every open MAJOR (${f.openMajors}) is a missing-critique finding and the open critique " +
              s"pairs did not go down over ${counted(s.noProgressRuns.value, "run")} (now ${f.openPairs.fold(0)(_.value)})"
          )
        else if f.escalated.value > 0 then
          stop(s"${counted(f.escalated.value, "MAJOR")} raised again after the fixer rejected it (escalated)")
        else if f.maxReopened.value >= s.reopenLimit.value then
          stop(s"a finding was reopened ${f.maxReopened} times (reopen-limit ${s.reopenLimit})")
        else if attempts.value >= s.maxRunsPerArticle.value then
          stop(
            s"${counted(f.openMajors.value, "open MAJOR")} after ${counted(attempts.value, "run")} (max-runs-per-article ${s.maxRunsPerArticle})"
          )
        else if !f.onlyMissingCritiques.value && noProgress(st.history, s.noProgressRuns) then
          stop(s"the open MAJORs did not go down over ${counted(s.noProgressRuns.value, "run")} (now ${f.openMajors})")
        else
          Next.Again(
            Option.when(s.cooldownMinutes.value > 0)(now.plusSeconds(s.cooldownMinutes.value.toLong * 60L)),
            st,
            Item.Note(
              s"${counted(f.openMajors.value, "open MAJOR")}$pairs after run $attempts; again" +
                (if s.cooldownMinutes.value > 0 then s" after a ${s.cooldownMinutes}-minute cooldown" else "")
            )
          )
      case Run.State.Cancelled =>
        Next.Stop(Item.Outcome("stopped: its run was cancelled"), None, state.modify(_.before).setTo(None))
      case _ => // failed (or anything else terminal)
        val n = failures.value + 1
        val why = error.map(_.sentence).filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
        if n >= s.maxFailures.value then
          Next.Stop(
            Item.Outcome(s"stopped: ${counted(n, "failed run")}$why"),
            Some(
              Item.Task(
                s"review manager stopped [[$slug]] after ${counted(n, "failed run")}$why (agent run ${run.value.take(8)})"
              )
            ),
            state.modify(_.before).setTo(None)
          )
        else
          Next.Retry(
            now.plus(ReviewSettings.backoff(s.backoffMinutes, s.maxBackoffMinutes, Next.Retry.Failures(n))),
            Next.Retry.Failures(n),
            Item.Note(s"run failed ($n of ${s.maxFailures})$why")
          )

  // ── selection (the review manager's refill) ────────────────────────────────────────────────────

  import Pick.Rationale

  /** One item the selection adds: its kind, target (the article's slug), priority, and why (for the event). */
  final case class Pick(
      kind: Item.Kind,
      target: Slug,
      priority: Assignable.Priority,
      why: Rationale
  ):
    /** The item's outcome when a stale article is selected again: why. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def outcome: ManagerItem.Outcome = ManagerItem.Outcome(why.said)

  object Pick:
    /** Why the selection picks an article: the account of the refill, not a failure. */
    enum Rationale:
      /** It has `majors` open MAJOR findings. */
      case OpenMajors(majors: ReviewFacts.OpenMajors)

      /** It was never reviewed. */
      case NeverReviewed

      /** It converged, and is due again as `why` says. */
      case Stale(why: Staleness)

      /** It records no coverage filter (an infer-filter item comes first). */
      case NoFilter

      /** The account as the manager's event and its item's outcome show it. */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def said: String = this match
        case OpenMajors(majors) => counted(majors.value, "open MAJOR").value
        case NeverReviewed      => "never reviewed"
        case Stale(why)         => s"stale: ${why.said}"
        case NoFilter           => "it records no coverage filter"

  /** Why a converged article is due again. */
  enum Staleness:
    /** Its filter returns `now` sources, more than the `converged` it returned when it converged. */
    case Gained(converged: FilterHits, now: FilterHits)

    /** It converged more than `days` days ago. */
    case Old(days: ReviewSettings.StaleAfterDays)

    /** The reason as the selection shows it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case Gained(converged, now) => s"its filter gained ${counted(now.value - converged.value, "source")}"
      case Old(days)              => s"last converged over $days days ago"

  /** The priorities of what the selection picks: open MAJORs, never reviewed, stale (a person's enqueue is 10, a
    * chained review 2 — both above).
    */
  val MajorsPriority: Assignable.Priority = Assignable.Priority(1)
  val UnreviewedPriority: Assignable.Priority = Assignable.Priority(0)
  val StalePriority: Assignable.Priority = Assignable.Priority(-1)

  /** The review manager's selection, in its order — articles with open MAJORs (most first), never reviewed ones, then
    * stale ones (converged long ago, or their filter gained sources) — at most `batch` articles. With `inferFilter`, an
    * article that records no coverage filter (`hasFilter`) and never had an infer-filter item (`inferredBefore`) gets
    * one too, at its review's priority: the review waits for it ([[waitsFor]]).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def select(
      majors: List[(Slug, ReviewFacts.OpenMajors)],
      unreviewed: List[Slug],
      stale: List[(Slug, Staleness)],
      batch: ReviewSettings.RefillBatch,
      inferFilter: ReviewSettings.InferFilter,
      hasFilter: Slug => Boolean,
      inferredBefore: Slug => Boolean
  ): List[Pick] =
    val reviews =
      (majors.map((slug, n) =>
        Pick(
          Item.Kind.ArticleReview,
          slug,
          MajorsPriority,
          Pick.Rationale.OpenMajors(n)
        )
      ) ++
        unreviewed.map(slug =>
          Pick(
            Item.Kind.ArticleReview,
            slug,
            UnreviewedPriority,
            Pick.Rationale.NeverReviewed
          )
        ) ++
        stale.map((slug, why) =>
          Pick(
            Item.Kind.ArticleReview,
            slug,
            StalePriority,
            Pick.Rationale.Stale(why)
          )
        ))
        .foldLeft(Vector.empty[Pick])((acc, p) => if acc.exists(_.target == p.target) then acc else acc :+ p)
        .take(math.max(0, batch.value))
        .toList
    reviews.flatMap { p =>
      val infer = Option.when(inferFilter.value && !hasFilter(p.target) && !inferredBefore(p.target))(
        Pick(
          Item.Kind.InferFilter,
          p.target,
          p.priority,
          Pick.Rationale.NoFilter
        )
      )
      infer.toList :+ p
    }

  /** Why a converged article is due again, if it is: its review converged more than `days` days ago, or its filter
    * returns more sources now (`hitsNow`) than when it converged (`hitsThen`). `days` 0 = never.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def staleWhy(
      convergedAt: Instant,
      now: Instant,
      days: ReviewSettings.StaleAfterDays,
      hitsThen: Option[FilterHits],
      hitsNow: Option[FilterHits]
  ): Option[Staleness] =
    if days.value <= 0 then None
    else
      hitsThen
        .zip(hitsNow)
        .collect { case (a, b) if b > a => Staleness.Gained(a, b) }
        .orElse(
          Option.when(!convergedAt.plus(Duration.ofDays(days.value.toLong)).isAfter(now))(Staleness.Old(days))
        )
