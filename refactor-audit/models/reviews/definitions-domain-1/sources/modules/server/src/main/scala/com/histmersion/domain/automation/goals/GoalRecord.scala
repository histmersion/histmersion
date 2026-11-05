package com.histmersion.domain.automation.goals

import com.histmersion.api.automation.Goal

import java.time.Instant

/** A goal as [[GoalService.InMemory]] holds it: what a person put, its approved plan and run cap, and when it was made
  * and last changed.
  */
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
  object Plan extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
