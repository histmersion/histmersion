package com.histmersion
package domain.automation.goals

import com.histmersion.domain.automation.ScheduleKind

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Goal extends RefinedType[String, Pure]
  type Cron = Cron.T
  object Cron extends RefinedType[String, Pure]
  type Enabled = Enabled.T
  object Enabled extends RefinedType[Boolean, Pure]
