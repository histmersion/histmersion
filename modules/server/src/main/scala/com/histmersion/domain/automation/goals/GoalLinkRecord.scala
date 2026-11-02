package com.histmersion.domain.automation.goals

import java.time.Instant
import com.histmersion.domain.automation.Goal

/** One link of a goal as [[GoalService.InMemory]] holds it. */
final case class GoalLinkRecord(
    goal: String,
    kind: Goal.LinkKind,
    ref: String,
    note: Option[String],
    createdAt: Instant
)
