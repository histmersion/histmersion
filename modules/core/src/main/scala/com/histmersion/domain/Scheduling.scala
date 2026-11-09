package com.histmersion
package domain

import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.given

import com.histmersion.domain.library.Doi
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure

/** The pure parts of a goal's schedules (docs/research-embedded-agents.md §14): whether a due schedule may start a run
  * now (the previous run, the daily/monthly cost caps, `max-concurrent-runs`), its cost cap, and the DOIs an
  * acquisition item names (how the work lists are deduplicated). The sweeps' own rules are the library's
  * (`AcquisitionSweepRules`, `CurrencyCheckRules`).
  */
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

  enum Decision derives CanEqual:
    case Start
    case Skip(reason: Problem)
    case Defer(reason: Problem)

  /** A run that has not settled: a schedule does not start another beside it. */
  def unsettled(s: Run.State): Boolean = !s.isTerminal

  /** Before a scheduled run starts: its previous run must have settled, today's and this month's agent spend must be
    * under the caps (checked BEFORE the start, so a scheduled run never begins past a cap), and fewer than
    * `maxConcurrent` runs may be queued or running.
    */
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
      case Some(s) => Decision.Skip(Problem(s"its previous run is still ${s.wire}"))
      case None    =>
        if spentDay >= BigDecimal(dailyUsd.value) then
          Decision.Skip(
            Problem(
              s"today's agent spend (${AgentsMarkdown.usd(spentDay.toDouble)} USD) reached agents.budgets.daily-usd"
            )
          )
        else if spentMonth >= BigDecimal(monthlyUsd.value) then
          Decision.Skip(
            Problem(
              s"this month's agent spend (${AgentsMarkdown.usd(spentMonth.toDouble)} USD) reached agents.budgets.monthly-usd"
            )
          )
        else if active.value >= math.max(1, maxConcurrent.value) then
          Decision.Defer(
            Problem(s"$active run(s) already queued or running (agents.max-concurrent-runs)")
          )
        else Decision.Start

  /** The cost cap of a scheduled run: the schedule's own, else the goal's, else the server default. */
  def budget(schedule: Option[Budget], goal: Option[Budget], default: Budget): Budget =
    schedule.orElse(goal).filter(_.value > 0).getOrElse(default)

  // ── the acquisition items ───────────────────────────────────────────────────────────────────────

  private val DoiIn = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** Every DOI written in a text (an acquisition item), normalised — how the queue is deduplicated. */
  def dois(text: String): Set[Doi] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(Doi.normalize).toSet
