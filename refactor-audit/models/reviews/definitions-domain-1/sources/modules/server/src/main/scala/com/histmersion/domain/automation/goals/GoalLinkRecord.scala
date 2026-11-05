package com.histmersion.domain.automation.goals

import java.time.Instant
import com.histmersion.domain.automation.Goal

/** One link of a goal as [[GoalService.InMemory]] holds it. */
final case class GoalLinkRecord(
    goal: GoalLinkRecord.Goal,
    kind: Goal.LinkKind,
    ref: GoalLinkRecord.Ref,
    note: Option[GoalLinkRecord.Note],
    createdAt: Instant
)

object GoalLinkRecord:
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ref = Ref.T
  object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
