package com.histmersion.domain

import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.given

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Run

/** The pure half of the process managers (docs/research-process-managers.md §3.4): what to do with an item after its
  * run settled, the order items are dispatched in, how many runs a manager may start, the backoff, and the hold
  * releases. No I/O: the supervisor reads the facts and writes the result.
  */
object ManagerPolicy:
  type ActiveAll = ActiveAll.T
  object ActiveAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockReason = BlockReason.T
  object BlockReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Budget = Budget.T
  object Budget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Capacity = Capacity.T
  object Capacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CronText = CronText.T
  object CronText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyCap = DailyCap.T
  object DailyCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueCount = DueCount.T
  object DueCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InFlightCount = InFlightCount.T
  object InFlightCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InstantText = InstantText.T
  object InstantText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemId = ItemId.T
  object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxConcurrent = MaxConcurrent.T
  object MaxConcurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinIntervalMinutes = MinIntervalMinutes.T
  object MinIntervalMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MineOpen = MineOpen.T
  object MineOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MineRunning = MineRunning.T
  object MineRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingCount = PendingCount.T
  object PendingCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolCap = PoolCap.T
  object PoolCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quota = Quota.T
  object Quota extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Room = Room.T
  object Room extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNote = StatusNote.T
  object StatusNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepLabel = StepLabel.T
  object StepLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tag = Tag.T
  object Tag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topic = Topic.T
  object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unsettled = Unsettled.T
  object Unsettled extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A manager's settings in effect (the saved config over `agents.managers.<kind>`). The review manager uses them all;
    * the enrich and seeding managers their run limits, failures and backoff (their items run once each: no rounds, no
    * refill).
    */

  type GoalKinds = GoalKindsValue
  type GoalKindsValue = GoalKindsValue.T
  object GoalKindsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class ReviewSettings(
      maxRuns: ReviewSettings.MaxRuns,
      maxOpen: ReviewSettings.MaxOpen,
      rounds: ReviewSettings.Rounds,
      maxRunsPerArticle: ReviewSettings.MaxRunsPerArticle,
      noProgressRuns: ReviewSettings.NoProgressRuns,
      reopenLimit: ReviewSettings.ReopenLimit,
      cooldownMinutes: ReviewSettings.CooldownMinutes,
      lowWater: ReviewSettings.LowWater,
      refillBatch: ReviewSettings.RefillBatch,
      maxFailures: ReviewSettings.MaxFailures,
      backoffMinutes: ReviewSettings.BackoffMinutes,
      maxBackoffMinutes: ReviewSettings.MaxBackoffMinutes,
      staleAfterDays: ReviewSettings.StaleAfterDays = com.histmersion.domain.ManagerPolicy.ReviewSettings.StaleAfterDays(0),
      inferFilter: ReviewSettings.InferFilter = com.histmersion.domain.ManagerPolicy.ReviewSettings.InferFilter(false)
  )

  object ReviewSettings:
    /** The settings of a manager of kind `k`. */
    type MaxRuns = MaxRuns.T
    object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxOpen = MaxOpen.T
    object MaxOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rounds = Rounds.T
    object Rounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxRunsPerArticle = MaxRunsPerArticle.T
    object MaxRunsPerArticle extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoProgressRuns = NoProgressRuns.T
    object NoProgressRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReopenLimit = ReopenLimit.T
    object ReopenLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CooldownMinutes = CooldownMinutes.T
    object CooldownMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LowWater = LowWater.T
    object LowWater extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefillBatch = RefillBatch.T
    object RefillBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxFailures = MaxFailures.T
    object MaxFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BackoffMinutes = BackoffMinutes.T
    object BackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxBackoffMinutes = MaxBackoffMinutes.T
    object MaxBackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StaleAfterDays = StaleAfterDays.T
    object StaleAfterDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InferFilter = InferFilter.T
    object InferFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def forKind(k: Manager.Kind, c: ManagerConfig, m: ManagerDefaults): ReviewSettings =
      val maxRuns = math.max(0, c.maxRuns.getOrElse(simple(k, m).maxRuns))
      ReviewSettings(
        maxRuns,
        math.max(maxRuns, c.maxOpen.getOrElse(2 * maxRuns)),
        com.histmersion.domain.ManagerPolicy.ReviewSettings.Rounds(1),
        com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxRunsPerArticle(1),
        com.histmersion.domain.ManagerPolicy.ReviewSettings.NoProgressRuns(1),
        com.histmersion.domain.ManagerPolicy.ReviewSettings.ReopenLimit(1),
        math.max(0, c.cooldownMinutes.getOrElse(0)),
        com.histmersion.domain.ManagerPolicy.ReviewSettings.LowWater(0),
        com.histmersion.domain.ManagerPolicy.ReviewSettings.RefillBatch(0),
        math.max(1, c.maxFailures.getOrElse(m.maxFailures)),
        math.max(1, m.backoffMinutes),
        math.max(1, m.maxBackoffMinutes)
      )

    private def simple(k: Manager.Kind, m: ManagerDefaults): ManagerRunDefaults = k match
      case Manager.Kind.Review => m.review
      case Manager.Kind.Enrich => m.enrich
      case Manager.Kind.Watch  => m.watch
      case _                   => m.seed

    /** The defaults of a manager of kind `k` (a kind whose defaults say more builds them itself). */
    def defaultsFor(k: Manager.Kind, m: ManagerDefaults): ManagerConfig =
      val s = simple(k, m)
      ManagerConfig(
        maxRuns = Some(s.maxRuns),
        maxOpen = Some(2 * s.maxRuns),
        maxFailures = Some(m.maxFailures),
        dailyUsd = dailyCap(ManagerConfig(), m)
      )

  // ── the cron (phase 3) ─────────────────────────────────────────────────────────────────────────

  /** What a manager's cron does at a tick. */
  enum CronStep:
    /** Nothing: no cron, or not due yet. */
    case Idle

    /** Not scheduled yet: record its first firing, fire nothing. */
    case Schedule(next: Option[Instant])

    /** Due: fire now, then next at `next` (None: it never fires again). */
    case Fire(next: Option[Instant])

  /** An active manager's cron at `now`, given when it was due (`nextTick`). A firing missed while the manager was
    * paused (or the server down) fires ONCE when it is next looked at, never once per missed time.
    */
  def cronStep(cron: Option[Cron], nextTick: Option[Instant], now: Instant): CronStep =
    cron match
      case None    => CronStep.Idle
      case Some(c) =>
        nextTick match
          case None                       => CronStep.Schedule(c.next(now))
          case Some(t) if !t.isAfter(now) => CronStep.Fire(c.next(now))
          case _                          => CronStep.Idle

  /** A cron a person sets: it parses, and it fires no more often than every `minIntervalMinutes`
    * (`agents.scheduler.min-interval-minutes`).
    */
  def checkCron(text: com.histmersion.domain.ManagerPolicy.CronText, now: Instant, minIntervalMinutes: com.histmersion.domain.ManagerPolicy.MinIntervalMinutes): Either[com.histmersion.domain.ManagerPolicy.Problem, Cron] =
    Cron.parse(text).flatMap { c =>
      c.shortestGapMinutes(now).filter(_ < minIntervalMinutes) match
        case Some(gap) =>
          Left(
            s"'$text' would fire every $gap minute(s); the least is $minIntervalMinutes " +
              "(agents.scheduler.min-interval-minutes)"
          )
        case None => Right(c)
    }

  /** A manager's own daily cap in effect: its `dailyUsd`, else `agents.managers.daily-usd`; 0 = none. */
  def dailyCap(c: ManagerConfig, m: ManagerDefaults): Option[com.histmersion.domain.ManagerPolicy.DailyCap] =
    c.dailyUsd.orElse(Option(m.dailyUsd)).filter(_ > 0)

  /** Why a manager starts nothing more today, if it does not: its runs' metered spend this UTC day reached its daily
    * cap. Plan-billed calls cost 0 USD, so this caps metered spend only.
    */
  def dailyBlock(cap: Option[com.histmersion.domain.ManagerPolicy.DailyCap], spentToday: BigDecimal): Option[com.histmersion.domain.ManagerPolicy.BlockReason] =
    cap
      .filter(c => spentToday >= BigDecimal(c))
      .map(c =>
        s"budget: its spend today (${ManagersMarkdown.usd(spentToday.toDouble)} USD, metered calls only) reached its daily-usd " +
          s"(${ManagersMarkdown.usd(c)}); it starts again tomorrow (UTC)"
      )

  /** The start of `now`'s UTC day (what a daily cap counts from). */
  def dayStart(now: Instant): Instant = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)

  /** What the next step of an item is, once its run settled. */
  enum Next:
    /** Run it again (not before `notBefore`). */
    case Again(notBefore: Option[Instant], state: ItemState, note: Next.Again.Note)

    /** It reached its end. */
    case Done(outcome: Next.Done.Outcome, state: ItemState)

    /** It stops here; `task` is the backlog line a person acts on (None: nothing to act on). */
    case Stop(outcome: Next.Stop.Outcome, task: Option[Next.Stop.Task], state: ItemState)

    /** Its run failed: try again after the backoff (`failures` counted). */
    case Retry(notBefore: Instant, failures: Next.Retry.Failures, note: Next.Retry.Note)

    /** Its run left a proposal for a person (approval `approval`): the item is held until it is decided, then settled
      * with the decision — never run again for it.
      */
    case Wait(approval: Next.Wait.Approval, note: Next.Wait.Note)

  object Next:
    object Again:
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Done:
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Stop:
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Task = Task.T
      object Task extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Retry:
      type Failures = Failures.T
      object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Wait:
      type Approval = Approval.T
      object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `base · 2^(failures-1)` minutes, at most `max`. */
  def backoff(baseMinutes: com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes, maxMinutes: com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes, failures: com.histmersion.domain.ManagerPolicy.Next.Retry.Failures): Duration =
    val exp = math.min(30, math.max(0, failures - 1))
    val mins = math.min(maxMinutes.toLong, baseMinutes.toLong * (1L << exp))
    Duration.ofMinutes(math.max(1L, mins))

  // ── the one-run items (infer-filter, extend, enrich, propose, compile) ───────────────────────────

  /** What a DONE run of a one-run item left, as the supervisor reads it: its work is finished (with the outcome), it
    * waits for a person's decision on approval `id`, or it ended without doing its work (`task`: what a person should
    * look at, if anything).
    */
  enum StepFacts:
    case Finished(outcome: StepFacts.Finished.Outcome)
    case Waiting(approval: StepFacts.Waiting.Approval, what: StepFacts.Waiting.What)
    case NotDone(outcome: StepFacts.NotDone.Outcome, task: Option[StepFacts.NotDone.Task])

    /** Its work was refused by a check that a new run may pass (a draft the autopilot's checks rejected): run it again
      * after the backoff, counted as a failure, stopped with a task after `maxFailures`.
      */
    case Redo(outcome: StepFacts.Redo.Outcome)

  object StepFacts:
    object Finished:
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Waiting:
      type Approval = Approval.T
      object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type What = What.T
      object What extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object NotDone:
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Task = Task.T
      object Task extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Redo:
      type Outcome = Outcome.T
      object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Evaluate a one-run item (`label` = its kind, for the texts) after its run settled `status`: done → finished, held
    * for an approval, or stopped; failed → retried after the backoff, stopped with a task after `maxFailures`;
    * cancelled → stopped. `facts` are read only for a done run.
    */
  def evaluateStep(
      s: ReviewSettings,
      label: com.histmersion.domain.ManagerPolicy.StepLabel,
      target: com.histmersion.domain.ManagerPolicy.Candidate.Target,
      failures: com.histmersion.domain.ManagerPolicy.Next.Retry.Failures,
      state: ItemState,
      run: com.histmersion.domain.automation.Run.Id,
      status: Run.State,
      error: Option[com.histmersion.domain.ManagerPolicy.Problem],
      facts: => StepFacts,
      now: Instant
  ): Next =
    status match
      case Run.State.Done =>
        facts match
          case StepFacts.Finished(o)      => Next.Done(o, state)
          case StepFacts.Waiting(id, w)   => Next.Wait(id, w)
          case StepFacts.NotDone(o, task) => Next.Stop(o, task.map(_.take(2000)), state)
          case StepFacts.Redo(o)          =>
            val n = failures + 1
            if n >= s.maxFailures then
              Next.Stop(
                s"stopped: $o (${Plural.count(n, com.histmersion.api.Plural.CountSingular("attempt"))})",
                Some(
                  s"$label manager item stopped [[$target]]: $o, ${Plural.count(n, com.histmersion.api.Plural.CountSingular("attempt"))} (agent run ${run.take(8)})"
                    .take(2000)
                ),
                state
              )
            else
              Next.Retry(
                now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, n)),
                n,
                s"$o ($n of ${s.maxFailures}); again"
              )
      case Run.State.Cancelled => Next.Stop(com.histmersion.domain.ManagerPolicy.Next.Stop.Outcome("stopped: its run was cancelled"), None, state)
      case _                   =>
        val n = failures + 1
        val why = error.filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
        if n >= s.maxFailures then
          Next.Stop(
            s"stopped: ${Plural.count(n, com.histmersion.api.Plural.CountSingular("failed run"))}$why",
            Some(
              s"$label manager item stopped [[$target]] after ${Plural.count(n, com.histmersion.api.Plural.CountSingular("failed run"))}$why (agent run ${run.take(8)})"
            ),
            state
          )
        else
          Next.Retry(
            now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, n)),
            n,
            s"run failed ($n of ${s.maxFailures})$why"
          )

  /** How an approval a held item waits for was decided, as its outcome (None: still open). */
  def decided(state: com.histmersion.domain.automation.Approval.State.Wire, result: Option[com.histmersion.domain.automation.Approval.Outcome]): Option[com.histmersion.domain.automation.Approval.Outcome] =
    Approval.State.parse(state).filter(_ != Approval.State.Open).map { st =>
      st.wire + result.filter(_.nonEmpty).fold("")(r => s": ${r.take(300)}")
    }

  /** Must an item of `kind` on `target` wait — an article's review while an infer-filter item for the same article is
    * still open (pending, held or active), a goal's article proposal while its plan-goal item is? `open` = the
    * manager's open items (kind, target).
    */
  def waitsFor(kind: com.histmersion.domain.automation.Approval.Kind.Wire, target: com.histmersion.domain.ManagerPolicy.Candidate.Target, open: List[(com.histmersion.domain.automation.Approval.Kind.Wire, com.histmersion.domain.ManagerPolicy.Candidate.Target)]): Boolean =
    (kind == ArticleReview && open.contains((InferFilter, target))) ||
      (kind == ProposeArticles && open.contains((PlanGoalItem, target)))

  /** The item kinds whose target is a goal. */
  val GoalKinds: Set[com.histmersion.domain.ManagerPolicy.GoalKinds] = Set(PlanGoalItem, ProposeArticles, AcquisitionSweep, CurrencyCheck)

  /** Must an item of `kind` (whose pipeline is `pipeline`) wait for the unsettled runs on its target (their pipelines)?
    * On an article or a source: any of them (one writer at a time). On a goal — its plan, its article proposal, its
    * sweeps — only a run of the same pipeline: they write different things (the plan and queries, an approval, sources
    * and acquisition items), so a plan waiting days for a person does not stop its watcher, nor a sweep the proposal
    * (which waits for the plan through [[waitsFor]]).
    */
  def blocks(kind: com.histmersion.domain.automation.Approval.Kind.Wire, pipeline: com.histmersion.domain.automation.Pipeline.Wire, unsettled: Seq[com.histmersion.domain.ManagerPolicy.Unsettled]): Boolean =
    if GoalKinds(kind) then unsettled.contains(pipeline) else unsettled.nonEmpty

  // ── dispatch ───────────────────────────────────────────────────────────────────────────────────

  /** What dispatch needs of an item. */
  final case class Candidate(id: Candidate.Id, target: Candidate.Target, priority: Candidate.Priority, notBefore: Option[Instant])

  object Candidate:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** May it start now? */
  def due(c: Candidate, now: Instant): Boolean = c.notBefore.forall(!_.isAfter(now))

  /** The dispatch order within one manager: priority (highest first), then the earliest `notBefore` (none first), then
    * the oldest.
    */
  def order(cs: List[Candidate]): List[Candidate] =
    cs.sortBy(c => (-c.priority, c.notBefore.map(_.toEpochMilli).getOrElse(Long.MinValue), c.id))

  /** How many runs a manager may start now: the global room (`maxConcurrent` minus every agent run queued or running, a
    * person's too), its own (`maxRuns` minus its queued/running; `maxOpen` minus every unsettled one, paused and
    * waiting included), and its pools' (`poolCap`, the calls its models' plan pools take at once). Never negative.
    */
  def capacity(
      maxConcurrent: com.histmersion.domain.ManagerPolicy.MaxConcurrent,
      activeAll: com.histmersion.domain.ManagerPolicy.ActiveAll,
      maxRuns: com.histmersion.domain.ManagerRunDefaults.MaxRuns,
      mineRunning: com.histmersion.domain.ManagerPolicy.MineRunning,
      maxOpen: com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxOpen,
      mineOpen: com.histmersion.domain.ManagerPolicy.MineOpen,
      poolCap: Option[com.histmersion.domain.ManagerPolicy.PoolCap]
  ): com.histmersion.domain.ManagerPolicy.Capacity =
    List(
      maxConcurrent - activeAll,
      maxRuns - mineRunning,
      maxOpen - mineOpen,
      poolCap.fold(Int.MaxValue)(_ - mineRunning)
    ).min.max(0)

  /** Several managers sharing the global room: one item from each in turn (in the order given), each within its own
    * `cap`, until `room` is used or nothing is left.
    */
  def roundRobin[M, A](queues: List[(M, com.histmersion.domain.ManagerPolicy.Quota, List[A])], room: com.histmersion.domain.ManagerPolicy.Room): List[(M, A)] =
    @annotation.tailrec
    def go(qs: List[(M, com.histmersion.domain.ManagerPolicy.Quota, List[A])], left: Int, acc: Vector[(M, A)]): Vector[(M, A)] =
      val live = qs.filter((_, cap, as) => cap > 0 && as.nonEmpty)
      if left <= 0 || live.isEmpty then acc
      else
        val take = live.take(left)
        val picked = take.map((m, _, as) => (m, as.head))
        val rest = qs.map { case q @ (m, cap, as) =>
          if take.exists(_._1 == m) then (m, cap - 1, as.tail) else q
        }
        go(rest, left - take.size, acc ++ picked)
    go(queues, math.max(0, room), Vector.empty).toList

  /** Is a hold over: its date passed, or the source it waits for exists. A hold with neither waits for a person's
    * release.
    */
  def released(h: Hold, now: Instant, sourceExists: com.histmersion.domain.library.Citekey => Boolean): Boolean =
    h.until
      .flatMap(parseInstant)
      .exists(!_.isAfter(now)) || h.untilSource.map(_.trim).filter(_.nonEmpty).exists(sourceExists)

  /** `2026-11-01` (UTC midnight) or an ISO instant. */
  def parseInstant(s: com.histmersion.domain.ManagerPolicy.InstantText): Option[Instant] =
    val t = s.trim
    scala.util
      .Try(Instant.parse(t))
      .toOption
      .orElse(scala.util.Try(java.time.LocalDate.parse(t).atStartOfDay(java.time.ZoneOffset.UTC).toInstant).toOption)

  /** The status line a manager shows. */
  def statusNote(
      state: Manager.State,
      starved: Option[com.histmersion.domain.ManagerPolicy.StatusNote],
      capacity: com.histmersion.domain.ManagerPolicy.Capacity,
      pending: com.histmersion.domain.ManagerPolicy.PendingCount,
      due: com.histmersion.domain.ManagerPolicy.DueCount,
      inFlight: com.histmersion.domain.ManagerPolicy.InFlightCount,
      budget: Option[com.histmersion.domain.ManagerPolicy.Budget]
  ): com.histmersion.domain.ManagerPolicy.StatusNote =
    if state == Manager.State.Stopped then com.histmersion.domain.ManagerPolicy.StatusNote("stopped")
    else if state == Manager.State.Paused then
      (if inFlight > 0 then s"paused (${Plural.count(inFlight, com.histmersion.api.Plural.CountSingular("run"))} in flight)" else com.histmersion.domain.ManagerPolicy.StatusNote("paused")
    )
    else
      budget.orElse(starved.map(s => s"starved: $s")).getOrElse {
        if pending == 0 && inFlight == 0 then "idle"
        else if due > 0 && capacity == 0 then
          s"at capacity (${Plural.count(inFlight, com.histmersion.api.Plural.CountSingular("run"))} in flight, $pending pending)"
        else if pending > 0 then
          s"$pending pending" + (if inFlight > 0 then s", ${Plural.count(inFlight, com.histmersion.api.Plural.CountSingular("run"))} in flight" else "")
        else s"${Plural.count(inFlight, com.histmersion.api.Plural.CountSingular("run"))} in flight"
      }

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  /** Does a structured hint effect name this target (by slug, or by one of its tags/topics/goals)? */
  def matches(e: HintEffect, target: com.histmersion.domain.ManagerPolicy.Candidate.Target, tags: List[com.histmersion.domain.ManagerPolicy.Tag], topics: List[com.histmersion.domain.ManagerPolicy.Topic], goals: List[com.histmersion.domain.automation.Goal.Slug]): Boolean =
    def eq(a: String, b: String) = a.trim.equalsIgnoreCase(b.trim)
    e.targets.exists(eq(_, target)) || e.tag.exists(t => tags.exists(eq(_, t))) ||
    e.topic.exists(t => topics.exists(eq(_, t))) || e.goal.exists(g => goals.exists(eq(_, g)))

  /** The notes a run is given: the manager's live standing hints and unconsumed once-hints, manager-wide or about this
    * item, oldest first.
    */
  def notesFor(hints: List[ManagerHint], itemId: com.histmersion.domain.ManagerPolicy.ItemId): List[ManagerHint] =
    hints.filter(h => h.retiredAt.isEmpty && h.itemId.forall(_ == itemId) && h.text.trim.nonEmpty).sortBy(_.id)
