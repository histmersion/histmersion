package com.histmersion.domain

import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.given

import com.histmersion.domain.library.Doi
import com.histmersion.domain.automation.Run

/** The pure parts of a goal's schedules (docs/research-embedded-agents.md §14): whether a due schedule may start a run
  * now (the previous run, the daily/monthly cost caps, `max-concurrent-runs`), its cost cap, and the DOIs an
  * acquisition item names (how the work lists are deduplicated). The sweeps' own rules are the library's
  * (`AcquisitionSweepRules`, `CurrencyCheckRules`).
  */
object Scheduling:
  type ActiveCount = ActiveCount.T
  object ActiveCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Budget = Budget.T
  object Budget extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyBudget = DailyBudget.T
  object DailyBudget extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxConcurrent = MaxConcurrent.T
  object MaxConcurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MonthlyBudget = MonthlyBudget.T
  object MonthlyBudget extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What the scheduler does with a due schedule. `Skip` moves the schedule on to its next time; `Defer` leaves it due,
    * for the next tick.
    */

  enum Decision:
    case Start
    case Skip(reason: Decision.Skip.Reason)
    case Defer(reason: Decision.Defer.Reason)

  object Decision:
    object Skip:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Defer:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      dailyUsd: com.histmersion.domain.Scheduling.DailyBudget,
      monthlyUsd: com.histmersion.domain.Scheduling.MonthlyBudget,
      active: com.histmersion.domain.Scheduling.ActiveCount,
      maxConcurrent: com.histmersion.domain.Scheduling.MaxConcurrent
  ): Decision =
    previous.filter(unsettled) match
      case Some(s) => Decision.Skip(Decision.Skip.Reason(s"its previous run is still ${s.wire}"))
      case None    =>
        if spentDay >= BigDecimal(dailyUsd.value) then
          Decision.Skip(
            Decision.Skip.Reason(s"today's agent spend (${AgentsMarkdown.usd(spentDay.toDouble)} USD) reached agents.budgets.daily-usd")
          )
        else if spentMonth >= BigDecimal(monthlyUsd.value) then
          Decision.Skip(
            Decision.Skip.Reason(s"this month's agent spend (${AgentsMarkdown.usd(spentMonth.toDouble)} USD) reached agents.budgets.monthly-usd")
          )
        else if active.value >= math.max(1, maxConcurrent.value) then
          Decision.Defer(Decision.Defer.Reason(s"$active run(s) already queued or running (agents.max-concurrent-runs)"))
        else Decision.Start

  /** The cost cap of a scheduled run: the schedule's own, else the goal's, else the server default. */
  def budget(schedule: Option[com.histmersion.domain.Scheduling.Budget], goal: Option[com.histmersion.domain.Scheduling.Budget], default: com.histmersion.domain.Scheduling.Budget): com.histmersion.domain.Scheduling.Budget =
    schedule.orElse(goal).filter(_.value > 0).getOrElse(default)

  // ── the acquisition items ───────────────────────────────────────────────────────────────────────

  private val DoiIn = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** Every DOI written in a text (an acquisition item), normalised — how the queue is deduplicated. */
  def dois(text: String): Set[com.histmersion.domain.library.Doi] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(Doi.normalize).toSet
