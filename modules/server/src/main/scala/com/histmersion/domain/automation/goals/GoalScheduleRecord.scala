package com.histmersion.domain.automation.goals

import com.histmersion.domain.automation.ScheduleKind

import java.time.Instant

/** A goal schedule not moved into its watcher yet, as [[GoalService.InMemory]] holds it. */
final case class GoalScheduleRecord(
    goal: GoalScheduleRecord.Goal,
    kind: ScheduleKind,
    cron: GoalScheduleRecord.Cron,
    enabled: GoalScheduleRecord.Enabled,
    budgetUsd: Option[BigDecimal],
    nextRunAt: Option[Instant]
)

object GoalScheduleRecord:
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cron = Cron.T
  object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Enabled = Enabled.T
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
