package com.histmersion
package domain.managerpolicy

import com.histmersion.api.given
import com.histmersion.domain.automation.Manager
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.managers.ManagerConfig
import com.histmersion.domain.ManagerDefaults
import com.histmersion.domain.ManagerPolicy.dailyCap
import com.histmersion.domain.ManagerRunDefaults
import java.time.Duration
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import java.time.Instant
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.domain.ManagerPolicy.counted
import com.histmersion.api.automation.managers.ItemState
import com.histmersion.domain.ManagerPolicy.StepLabel

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
    staleAfterDays: ReviewSettings.StaleAfterDays = ReviewSettings.StaleAfterDays(0),
    inferFilter: ReviewSettings.InferFilter = ReviewSettings.InferFilter(false)
)

object ReviewSettings:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
  type MaxOpen = MaxOpen.T
  object MaxOpen extends Newtype[Int, Pure]
  type Rounds = Rounds.T
  object Rounds extends Newtype[Int, Pure]
  type MaxRunsPerArticle = MaxRunsPerArticle.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object MaxRunsPerArticle extends Newtype[Int, Pure]
  type NoProgressRuns = NoProgressRuns.T
  object NoProgressRuns extends Newtype[Int, Pure]
  type ReopenLimit = ReopenLimit.T
  object ReopenLimit extends Newtype[Int, Pure]
  type CooldownMinutes = CooldownMinutes.T
  object CooldownMinutes extends Newtype[Int, Pure]
  type LowWater = LowWater.T
  object LowWater extends Newtype[Int, Pure]
  type RefillBatch = RefillBatch.T
  object RefillBatch extends Newtype[Int, Pure]
  type MaxFailures = MaxFailures.T
  object MaxFailures extends Newtype[Int, Pure]
  type BackoffMinutes = BackoffMinutes.T
  object BackoffMinutes extends Newtype[Int, Pure]
  type MaxBackoffMinutes = MaxBackoffMinutes.T
  object MaxBackoffMinutes extends Newtype[Int, Pure]
  type StaleAfterDays = StaleAfterDays.T
  object StaleAfterDays extends Newtype[Int, Pure]
  type InferFilter = InferFilter.T
  object InferFilter extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def forKind(k: Manager.Kind, c: ManagerConfig, m: ManagerDefaults): ReviewSettings =
    val maxRuns = math.max(0, c.maxRuns.fold(simple(k, m).maxRuns.value)(_.value))
    ReviewSettings(
      MaxRuns(maxRuns),
      MaxOpen(math.max(maxRuns, c.maxOpen.fold(2 * maxRuns)(_.value))),
      Rounds(1),
      MaxRunsPerArticle(1),
      NoProgressRuns(1),
      ReopenLimit(1),
      CooldownMinutes(math.max(0, c.cooldownMinutes.fold(0)(_.value))),
      LowWater(0),
      RefillBatch(0),
      MaxFailures(math.max(1, c.maxFailures.fold(m.maxFailures.value)(_.value))),
      BackoffMinutes(math.max(1, m.backoffMinutes.value)),
      MaxBackoffMinutes(math.max(1, m.maxBackoffMinutes.value))
    )

  private def simple(k: Manager.Kind, m: ManagerDefaults): ManagerRunDefaults = k match
    case Manager.Kind.Review => m.review
    case Manager.Kind.Enrich => m.enrich
    case Manager.Kind.Watch  => m.watch
    case _                   => m.seed

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def defaultsFor(k: Manager.Kind, m: ManagerDefaults): ManagerConfig =
    val s = simple(k, m)
    ManagerConfig(
      maxRuns = Some(ManagerConfig.MaxRuns(s.maxRuns.value)),
      maxOpen = Some(ManagerConfig.MaxOpen(2 * s.maxRuns.value)),
      maxFailures = Some(ManagerConfig.MaxFailures(m.maxFailures.value)),
      dailyUsd = dailyCap(ManagerConfig(), m)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def backoff(
      baseMinutes: BackoffMinutes,
      maxMinutes: MaxBackoffMinutes,
      failures: Next.Retry.Failures
  ): Duration =
    val exp = math.min(30, math.max(0, failures.value - 1))
    val mins = math.min(maxMinutes.value.toLong, baseMinutes.value.toLong * (1L << exp))
    Duration.ofMinutes(math.max(1L, mins))

  extension (s: ReviewSettings)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def evaluateStep(
        label: StepLabel,
        target: Assignable.Target,
        failures: Next.Retry.Failures,
        state: ItemState,
        run: Run.Id,
        status: Run.State,
        error: Option[Problem],
        facts: => StepFacts,
        now: Instant
    ): Next =
      status match
        case Run.State.Done =>
          facts match
            case StepFacts.Finished(o)      => Next.Done(o, state)
            case StepFacts.Waiting(id, w)   => Next.Wait(id, w)
            case StepFacts.NotDone(o, task) => Next.Stop(o, task.map(t => Item.Task(t.value.take(2000))), state)
            case StepFacts.Redo(o)          =>
              val n = failures.value + 1
              if n >= s.maxFailures.value then
                Next.Stop(
                  Item.Outcome(s"stopped: $o (${counted(n, "attempt")})"),
                  Some(
                    Item.Task(
                      s"$label manager item stopped [[$target]]: $o, ${counted(n, "attempt")} (agent run ${run.value.take(8)})"
                        .take(2000)
                    )
                  ),
                  state
                )
              else
                Next.Retry(
                  now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, Next.Retry.Failures(n))),
                  Next.Retry.Failures(n),
                  Item.Note(s"$o ($n of ${s.maxFailures}); again")
                )
        case Run.State.Cancelled => Next.Stop(Item.Outcome("stopped: its run was cancelled"), None, state)
        case _                   =>
          val n = failures.value + 1
          val why = error.map(_.sentence).filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
          if n >= s.maxFailures.value then
            Next.Stop(
              Item.Outcome(s"stopped: ${counted(n, "failed run")}$why"),
              Some(
                Item.Task(
                  s"$label manager item stopped [[$target]] after ${counted(n, "failed run")}$why (agent run ${run.value.take(8)})"
                )
              ),
              state
            )
          else
            Next.Retry(
              now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, Next.Retry.Failures(n))),
              Next.Retry.Failures(n),
              Item.Note(s"run failed ($n of ${s.maxFailures})$why")
            )
