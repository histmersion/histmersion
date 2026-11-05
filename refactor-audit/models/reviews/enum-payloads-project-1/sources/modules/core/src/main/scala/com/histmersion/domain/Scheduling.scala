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

  /** What the scheduler does with a due schedule. `Skip` moves the schedule on to its next time; `Defer` leaves it due,
    * for the next tick.
    */
  type UnsettledResult = UnsettledResult.T
  object UnsettledResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideDailyUsd = DecideDailyUsd.T
  object DecideDailyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideMonthlyUsd = DecideMonthlyUsd.T
  object DecideMonthlyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideActive = DecideActive.T
  object DecideActive extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideMaxConcurrent = DecideMaxConcurrent.T
  object DecideMaxConcurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetResult = BudgetResult.T
  object BudgetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetSchedule = BudgetSchedule.T
  object BudgetSchedule extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetGoal = BudgetGoal.T
  object BudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetDefault = BudgetDefault.T
  object BudgetDefault extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoisResult = DoisResult.T
  object DoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoisText = DoisText.T
  object DoisText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Decision:
    case Start
    case Skip(reason: Skip.Reason)
    case Defer(reason: Defer.Reason)

  object Decision:
    object Skip:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Defer:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A run that has not settled: a schedule does not start another beside it. */
  def unsettled(s: Run.State): com.histmersion.domain.Scheduling.UnsettledResult = !s.isTerminal

  /** Before a scheduled run starts: its previous run must have settled, today's and this month's agent spend must be
    * under the caps (checked BEFORE the start, so a scheduled run never begins past a cap), and fewer than
    * `maxConcurrent` runs may be queued or running.
    */
  def decide(
      previous: Option[Run.State],
      spentDay: BigDecimal,
      spentMonth: BigDecimal,
      dailyUsd: com.histmersion.domain.Scheduling.DecideDailyUsd,
      monthlyUsd: com.histmersion.domain.Scheduling.DecideMonthlyUsd,
      active: com.histmersion.domain.Scheduling.DecideActive,
      maxConcurrent: com.histmersion.domain.Scheduling.DecideMaxConcurrent
  ): Decision =
    previous.filter(unsettled) match
      case Some(s) => Decision.Skip(s"its previous run is still ${s.wire}")
      case None    =>
        if spentDay >= BigDecimal(dailyUsd) then
          Decision.Skip(
            s"today's agent spend (${AgentsMarkdown.usd(spentDay.toDouble)} USD) reached agents.budgets.daily-usd"
          )
        else if spentMonth >= BigDecimal(monthlyUsd) then
          Decision.Skip(
            s"this month's agent spend (${AgentsMarkdown.usd(spentMonth.toDouble)} USD) reached agents.budgets.monthly-usd"
          )
        else if active >= math.max(1, maxConcurrent) then
          Decision.Defer(s"$active run(s) already queued or running (agents.max-concurrent-runs)")
        else Decision.Start

  /** The cost cap of a scheduled run: the schedule's own, else the goal's, else the server default. */
  def budget(schedule: Option[com.histmersion.domain.Scheduling.BudgetSchedule], goal: Option[com.histmersion.domain.Scheduling.BudgetGoal], default: com.histmersion.domain.Scheduling.BudgetDefault): com.histmersion.domain.Scheduling.BudgetResult =
    schedule.orElse(goal).filter(_ > 0).getOrElse(default)

  // ── the acquisition items ───────────────────────────────────────────────────────────────────────

  private val DoiIn = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** Every DOI written in a text (an acquisition item), normalised — how the queue is deduplicated. */
  def dois(text: com.histmersion.domain.Scheduling.DoisText): Set[com.histmersion.domain.Scheduling.DoisResult] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(Doi.normalize).toSet
