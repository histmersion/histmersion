package com.histmersion
package domain.automation.goals

import com.histmersion.domain.automation.ScheduleKind

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

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
  object Goal extends Newtype[String, Pure]
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
  enum Enabled derives CanEqual:
    case Enabled, Disabled
  object Enabled extends TwoCases[Enabled]:
    extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
