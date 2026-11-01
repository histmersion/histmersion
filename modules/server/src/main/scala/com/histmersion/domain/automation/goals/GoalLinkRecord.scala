package com.histmersion.domain.automation.goals

import com.histmersion.values.GoalLinkKind

import java.time.Instant

/** One link of a goal as [[GoalService.InMemory]] holds it. */
final case class GoalLinkRecord(goal: String, kind: GoalLinkKind, ref: String, note: Option[String], createdAt: Instant)
