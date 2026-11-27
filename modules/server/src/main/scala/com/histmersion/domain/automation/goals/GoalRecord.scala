package com.histmersion
package domain.automation.goals

import com.histmersion.api.automation.Goal

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A goal as [[GoalService.InMemory]] holds it: what a person put, its approved plan and run cap, and when it was made
  * and last changed.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class GoalRecord(
    goal: Goal,
    plan: Option[GoalRecord.Plan],
    planApprovedAt: Option[Instant],
    budgetUsd: Option[BigDecimal],
    createdAt: Instant,
    updatedAt: Instant
)

object GoalRecord:
  type Plan = Plan.T
  object Plan extends Newtype[String, Pure]
