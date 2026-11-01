package com.histmersion.domain.automation.goals

import com.histmersion.values.ScheduleKind

import java.time.Instant

/** A goal schedule not moved into its watcher yet, as [[GoalService.InMemory]] holds it. */
final case class GoalScheduleRecord(
    goal: String,
    kind: ScheduleKind,
    cron: String,
    enabled: Boolean,
    budgetUsd: Option[BigDecimal],
    nextRunAt: Option[Instant]
)
