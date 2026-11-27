package com.histmersion
package domain

import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.given

import com.histmersion.domain.library.Doi
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The pure parts of a goal's schedules (docs/research-embedded-agents.md §14): whether a due schedule may start a run
  * now (the previous run, the daily/monthly cost caps, `max-concurrent-runs`), its cost cap, and the DOIs an
  * acquisition item names (how the work lists are deduplicated). The sweeps' own rules are the library's
  * (`AcquisitionSweepRules`, `CurrencyCheckRules`).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationScheduler)
object Scheduling:
  type ActiveCount = ActiveCount.T
  object ActiveCount extends Newtype[Int, Pure]
  type Budget = Budget.T
  object Budget extends Newtype[Double, Pure]
  type DailyBudget = DailyBudget.T
  object DailyBudget extends Newtype[Double, Pure]
  type MaxConcurrent = MaxConcurrent.T
  object MaxConcurrent extends Newtype[Int, Pure]
  type MonthlyBudget = MonthlyBudget.T
  object MonthlyBudget extends Newtype[Double, Pure]

  /** What the scheduler does with a due schedule. `Skip` moves the schedule on to its next time; `Defer` leaves it due,
    * for the next tick.
    */

  enum Decision:
    case Start
    case Skip(reason: Rationale)
    case Defer(reason: Rationale)

  /** Why a due schedule starts no run now: the account of the scheduler's decision, not a failure. */
  enum Rationale:
    /** Its previous run has not settled: it is `state`. */
    case Unsettled(state: Run.State)

    /** Today's agent spend, `spent` USD, reached `agents.budgets.daily-usd`. */
    case DailySpent(spent: BigDecimal)

    /** This month's agent spend, `spent` USD, reached `agents.budgets.monthly-usd`. */
    case MonthlySpent(spent: BigDecimal)

    /** `active` runs are queued or running already (`agents.max-concurrent-runs`). */
    case Busy(active: ActiveCount)

    /** The account as a person reads it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case Unsettled(state)  => s"its previous run is still ${state.wire}"
      case DailySpent(spent) =>
        s"today's agent spend (${AgentsMarkdown.usd(spent.toDouble)} USD) reached agents.budgets.daily-usd"
      case MonthlySpent(spent) =>
        s"this month's agent spend (${AgentsMarkdown.usd(spent.toDouble)} USD) reached agents.budgets.monthly-usd"
      case Busy(active) => s"$active run(s) already queued or running (agents.max-concurrent-runs)"

  /** A run that has not settled: a schedule does not start another beside it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def unsettled(s: Run.State): Boolean = !s.isTerminal

  /** Before a scheduled run starts: its previous run must have settled, today's and this month's agent spend must be
    * under the caps (checked BEFORE the start, so a scheduled run never begins past a cap), and fewer than
    * `maxConcurrent` runs may be queued or running.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decide(
      previous: Option[Run.State],
      spentDay: BigDecimal,
      spentMonth: BigDecimal,
      dailyUsd: DailyBudget,
      monthlyUsd: MonthlyBudget,
      active: ActiveCount,
      maxConcurrent: MaxConcurrent
  ): Decision =
    previous.filter(unsettled) match
      case Some(s) => Decision.Skip(Rationale.Unsettled(s))
      case None    =>
        if spentDay >= BigDecimal(dailyUsd.value) then Decision.Skip(Rationale.DailySpent(spentDay))
        else if spentMonth >= BigDecimal(monthlyUsd.value) then Decision.Skip(Rationale.MonthlySpent(spentMonth))
        else if active.value >= math.max(1, maxConcurrent.value) then Decision.Defer(Rationale.Busy(active))
        else Decision.Start

  /** The cost cap of a scheduled run: the schedule's own, else the goal's, else the server default. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def budget(schedule: Option[Budget], goal: Option[Budget], default: Budget): Budget =
    schedule.orElse(goal).filter(_.value > 0).getOrElse(default)

  // ── the acquisition items ───────────────────────────────────────────────────────────────────────

  private val DoiIn = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** Every DOI written in a text (an acquisition item), normalised — how the queue is deduplicated. */
  def dois(text: String): Set[Doi] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(Doi.normalize).toSet
