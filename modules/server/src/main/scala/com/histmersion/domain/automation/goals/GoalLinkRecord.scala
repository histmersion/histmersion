package com.histmersion
package domain.automation.goals

import java.time.Instant
import com.histmersion.domain.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure

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
  object Goal extends Newtype[String, Pure]
  type Ref = Ref.T
  object Ref extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
