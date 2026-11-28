package com.histmersion
package domain.automation.goals
import com.histmersion.domain.GoalFocus

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** What the focus recorded about one goal, as [[FocusService.InMemory]] holds it. */
final case class FocusRecord(
    active: FocusRecord.Active,
    pinned: FocusRecord.Pinned,
    priority: FocusRecord.Priority,
    since: Option[Instant],
    why: Option[GoalFocus.Rationale],
    leftAt: Option[Instant],
    leftWhy: Option[GoalFocus.Rationale]
)

object FocusRecord:
  enum Active derives CanEqual:
    case Active, Inactive
  object Active extends TwoCases[Active]:
    extension (active: Active) def active: Boolean = active == Active
  enum Pinned derives CanEqual:
    case Pinned, Unpinned
  object Pinned extends TwoCases[Pinned]:
    extension (pinned: Pinned) def pinned: Boolean = pinned == Pinned
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]

  val none: FocusRecord = FocusRecord(Active.Inactive, Pinned.Unpinned, Priority(0), None, None, None, None)
