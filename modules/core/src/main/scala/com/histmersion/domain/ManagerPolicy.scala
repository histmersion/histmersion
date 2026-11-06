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

  /** A manager's settings in effect (the saved config over `agents.managers.<kind>`). The review manager uses them all;
    * the enrich and seeding managers their run limits, failures and backoff (their items run once each: no rounds, no
    * refill).
    */
  type RoundRobinGoQsPart2 = RoundRobinGoQsPart2.T
  object RoundRobinGoQsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundRobinGoLeft = RoundRobinGoLeft.T
  object RoundRobinGoLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesEqA = MatchesEqA.T
  object MatchesEqA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesEqB = MatchesEqB.T
  object MatchesEqB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CheckCronResultPart1 = CheckCronResultPart1.T
  object CheckCronResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckCronText = CheckCronText.T
  object CheckCronText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckCronMinIntervalMinutes = CheckCronMinIntervalMinutes.T
  object CheckCronMinIntervalMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyCapResult = DailyCapResult.T
  object DailyCapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyBlockResult = DailyBlockResult.T
  object DailyBlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyBlockCap = DailyBlockCap.T
  object DailyBlockCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffBaseMinutes = BackoffBaseMinutes.T
  object BackoffBaseMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMaxMinutes = BackoffMaxMinutes.T
  object BackoffMaxMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffFailures = BackoffFailures.T
  object BackoffFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepLabel = EvaluateStepLabel.T
  object EvaluateStepLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepTarget = EvaluateStepTarget.T
  object EvaluateStepTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepFailures = EvaluateStepFailures.T
  object EvaluateStepFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepRun = EvaluateStepRun.T
  object EvaluateStepRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepError = EvaluateStepError.T
  object EvaluateStepError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecidedResult = DecidedResult.T
  object DecidedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecidedState = DecidedState.T
  object DecidedState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitsForResult = WaitsForResult.T
  object WaitsForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitsForKind = WaitsForKind.T
  object WaitsForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitsForTarget = WaitsForTarget.T
  object WaitsForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitsForOpenPart1 = WaitsForOpenPart1.T
  object WaitsForOpenPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitsForOpenPart2 = WaitsForOpenPart2.T
  object WaitsForOpenPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKinds = GoalKindsValue
  type GoalKindsValue = GoalKindsValue.T
  object GoalKindsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksResult = BlocksResult.T
  object BlocksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksKind = BlocksKind.T
  object BlocksKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksPipeline = BlocksPipeline.T
  object BlocksPipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksUnsettled = BlocksUnsettled.T
  object BlocksUnsettled extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueResult = DueResult.T
  object DueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityResult = CapacityResult.T
  object CapacityResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityMaxConcurrent = CapacityMaxConcurrent.T
  object CapacityMaxConcurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityActiveAll = CapacityActiveAll.T
  object CapacityActiveAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityMaxRuns = CapacityMaxRuns.T
  object CapacityMaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityMineRunning = CapacityMineRunning.T
  object CapacityMineRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityMaxOpen = CapacityMaxOpen.T
  object CapacityMaxOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityMineOpen = CapacityMineOpen.T
  object CapacityMineOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapacityPoolCap = CapacityPoolCap.T
  object CapacityPoolCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundRobinQueuesPart2 = RoundRobinQueuesPart2.T
  object RoundRobinQueuesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundRobinRoom = RoundRobinRoom.T
  object RoundRobinRoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleasedResult = ReleasedResult.T
  object ReleasedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleasedSourceExistsArg1 = ReleasedSourceExistsArg1.T
  object ReleasedSourceExistsArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleasedSourceExistsResult = ReleasedSourceExistsResult.T
  object ReleasedSourceExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseInstantS = ParseInstantS.T
  object ParseInstantS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNoteResult = StatusNoteResult.T
  object StatusNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNoteStarved = StatusNoteStarved.T
  object StatusNoteStarved extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNoteCapacity = StatusNoteCapacity.T
  object StatusNoteCapacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNotePending = StatusNotePending.T
  object StatusNotePending extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNoteDue = StatusNoteDue.T
  object StatusNoteDue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNoteInFlight = StatusNoteInFlight.T
  object StatusNoteInFlight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNoteBudget = StatusNoteBudget.T
  object StatusNoteBudget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResult = MatchesResult.T
  object MatchesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesTarget = MatchesTarget.T
  object MatchesTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesTags = MatchesTags.T
  object MatchesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesTopics = MatchesTopics.T
  object MatchesTopics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesGoals = MatchesGoals.T
  object MatchesGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesForItemId = NotesForItemId.T
  object NotesForItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      staleAfterDays: ReviewSettings.StaleAfterDays = 0,
      inferFilter: ReviewSettings.InferFilter = false
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
        1,
        1,
        1,
        1,
        math.max(0, c.cooldownMinutes.getOrElse(0)),
        0,
        0,
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
  def checkCron(text: com.histmersion.domain.ManagerPolicy.CheckCronText, now: Instant, minIntervalMinutes: com.histmersion.domain.ManagerPolicy.CheckCronMinIntervalMinutes): Either[com.histmersion.domain.ManagerPolicy.CheckCronResultPart1, Cron] =
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
  def dailyCap(c: ManagerConfig, m: ManagerDefaults): Option[com.histmersion.domain.ManagerPolicy.DailyCapResult] =
    c.dailyUsd.orElse(Option(m.dailyUsd)).filter(_ > 0)

  /** Why a manager starts nothing more today, if it does not: its runs' metered spend this UTC day reached its daily
    * cap. Plan-billed calls cost 0 USD, so this caps metered spend only.
    */
  def dailyBlock(cap: Option[com.histmersion.domain.ManagerPolicy.DailyBlockCap], spentToday: BigDecimal): Option[com.histmersion.domain.ManagerPolicy.DailyBlockResult] =
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
  def backoff(baseMinutes: com.histmersion.domain.ManagerPolicy.BackoffBaseMinutes, maxMinutes: com.histmersion.domain.ManagerPolicy.BackoffMaxMinutes, failures: com.histmersion.domain.ManagerPolicy.BackoffFailures): Duration =
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
      label: com.histmersion.domain.ManagerPolicy.EvaluateStepLabel,
      target: com.histmersion.domain.ManagerPolicy.EvaluateStepTarget,
      failures: com.histmersion.domain.ManagerPolicy.EvaluateStepFailures,
      state: ItemState,
      run: com.histmersion.domain.ManagerPolicy.EvaluateStepRun,
      status: Run.State,
      error: Option[com.histmersion.domain.ManagerPolicy.EvaluateStepError],
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
                s"stopped: $o (${Plural.count(n, "attempt")})",
                Some(
                  s"$label manager item stopped [[$target]]: $o, ${Plural.count(n, "attempt")} (agent run ${run.take(8)})"
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
      case Run.State.Cancelled => Next.Stop("stopped: its run was cancelled", None, state)
      case _                   =>
        val n = failures + 1
        val why = error.filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
        if n >= s.maxFailures then
          Next.Stop(
            s"stopped: ${Plural.count(n, "failed run")}$why",
            Some(
              s"$label manager item stopped [[$target]] after ${Plural.count(n, "failed run")}$why (agent run ${run.take(8)})"
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
  def decided(state: com.histmersion.domain.ManagerPolicy.DecidedState, result: Option[com.histmersion.domain.ManagerPolicy.DecidedResult]): Option[com.histmersion.domain.ManagerPolicy.DecidedResult] =
    Approval.State.parse(state).filter(_ != Approval.State.Open).map { st =>
      st.wire + result.filter(_.nonEmpty).fold("")(r => s": ${r.take(300)}")
    }

  /** Must an item of `kind` on `target` wait — an article's review while an infer-filter item for the same article is
    * still open (pending, held or active), a goal's article proposal while its plan-goal item is? `open` = the
    * manager's open items (kind, target).
    */
  def waitsFor(kind: com.histmersion.domain.ManagerPolicy.WaitsForKind, target: com.histmersion.domain.ManagerPolicy.WaitsForTarget, open: List[(com.histmersion.domain.ManagerPolicy.WaitsForOpenPart1, com.histmersion.domain.ManagerPolicy.WaitsForOpenPart2)]): com.histmersion.domain.ManagerPolicy.WaitsForResult =
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
  def blocks(kind: com.histmersion.domain.ManagerPolicy.BlocksKind, pipeline: com.histmersion.domain.ManagerPolicy.BlocksPipeline, unsettled: Seq[com.histmersion.domain.ManagerPolicy.BlocksUnsettled]): com.histmersion.domain.ManagerPolicy.BlocksResult =
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
  def due(c: Candidate, now: Instant): com.histmersion.domain.ManagerPolicy.DueResult = c.notBefore.forall(!_.isAfter(now))

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
      maxConcurrent: com.histmersion.domain.ManagerPolicy.CapacityMaxConcurrent,
      activeAll: com.histmersion.domain.ManagerPolicy.CapacityActiveAll,
      maxRuns: com.histmersion.domain.ManagerPolicy.CapacityMaxRuns,
      mineRunning: com.histmersion.domain.ManagerPolicy.CapacityMineRunning,
      maxOpen: com.histmersion.domain.ManagerPolicy.CapacityMaxOpen,
      mineOpen: com.histmersion.domain.ManagerPolicy.CapacityMineOpen,
      poolCap: Option[com.histmersion.domain.ManagerPolicy.CapacityPoolCap]
  ): com.histmersion.domain.ManagerPolicy.CapacityResult =
    List(
      maxConcurrent - activeAll,
      maxRuns - mineRunning,
      maxOpen - mineOpen,
      poolCap.fold(Int.MaxValue)(_ - mineRunning)
    ).min.max(0)

  /** Several managers sharing the global room: one item from each in turn (in the order given), each within its own
    * `cap`, until `room` is used or nothing is left.
    */
  def roundRobin[M, A](queues: List[(M, com.histmersion.domain.ManagerPolicy.RoundRobinQueuesPart2, List[A])], room: com.histmersion.domain.ManagerPolicy.RoundRobinRoom): List[(M, A)] =
    @annotation.tailrec
    def go(qs: List[(M, com.histmersion.domain.ManagerPolicy.RoundRobinGoQsPart2, List[A])], left: com.histmersion.domain.ManagerPolicy.RoundRobinGoLeft, acc: Vector[(M, A)]): Vector[(M, A)] =
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
  def released(h: Hold, now: Instant, sourceExists: com.histmersion.domain.ManagerPolicy.ReleasedSourceExistsArg1 => com.histmersion.domain.ManagerPolicy.ReleasedSourceExistsResult): com.histmersion.domain.ManagerPolicy.ReleasedResult =
    h.until
      .flatMap(parseInstant)
      .exists(!_.isAfter(now)) || h.untilSource.map(_.trim).filter(_.nonEmpty).exists(sourceExists)

  /** `2026-11-01` (UTC midnight) or an ISO instant. */
  def parseInstant(s: com.histmersion.domain.ManagerPolicy.ParseInstantS): Option[Instant] =
    val t = s.trim
    scala.util
      .Try(Instant.parse(t))
      .toOption
      .orElse(scala.util.Try(java.time.LocalDate.parse(t).atStartOfDay(java.time.ZoneOffset.UTC).toInstant).toOption)

  /** The status line a manager shows. */
  def statusNote(
      state: Manager.State,
      starved: Option[com.histmersion.domain.ManagerPolicy.StatusNoteStarved],
      capacity: com.histmersion.domain.ManagerPolicy.StatusNoteCapacity,
      pending: com.histmersion.domain.ManagerPolicy.StatusNotePending,
      due: com.histmersion.domain.ManagerPolicy.StatusNoteDue,
      inFlight: com.histmersion.domain.ManagerPolicy.StatusNoteInFlight,
      budget: Option[com.histmersion.domain.ManagerPolicy.StatusNoteBudget]
  ): com.histmersion.domain.ManagerPolicy.StatusNoteResult =
    if state == Manager.State.Stopped then "stopped"
    else if state == Manager.State.Paused then
      (if inFlight > 0 then s"paused (${Plural.count(inFlight, "run")} in flight)" else "paused"
    )
    else
      budget.orElse(starved.map(s => s"starved: $s")).getOrElse {
        if pending == 0 && inFlight == 0 then "idle"
        else if due > 0 && capacity == 0 then
          s"at capacity (${Plural.count(inFlight, "run")} in flight, $pending pending)"
        else if pending > 0 then
          s"$pending pending" + (if inFlight > 0 then s", ${Plural.count(inFlight, "run")} in flight" else "")
        else s"${Plural.count(inFlight, "run")} in flight"
      }

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  /** Does a structured hint effect name this target (by slug, or by one of its tags/topics/goals)? */
  def matches(e: HintEffect, target: com.histmersion.domain.ManagerPolicy.MatchesTarget, tags: List[com.histmersion.domain.ManagerPolicy.MatchesTags], topics: List[com.histmersion.domain.ManagerPolicy.MatchesTopics], goals: List[com.histmersion.domain.ManagerPolicy.MatchesGoals]): com.histmersion.domain.ManagerPolicy.MatchesResult =
    def eq(a: com.histmersion.domain.ManagerPolicy.MatchesEqA, b: com.histmersion.domain.ManagerPolicy.MatchesEqB) = a.trim.equalsIgnoreCase(b.trim)
    e.targets.exists(eq(_, target)) || e.tag.exists(t => tags.exists(eq(_, t))) ||
    e.topic.exists(t => topics.exists(eq(_, t))) || e.goal.exists(g => goals.exists(eq(_, g)))

  /** The notes a run is given: the manager's live standing hints and unconsumed once-hints, manager-wide or about this
    * item, oldest first.
    */
  def notesFor(hints: List[ManagerHint], itemId: com.histmersion.domain.ManagerPolicy.NotesForItemId): List[ManagerHint] =
    hints.filter(h => h.retiredAt.isEmpty && h.itemId.forall(_ == itemId) && h.text.trim.nonEmpty).sortBy(_.id)
