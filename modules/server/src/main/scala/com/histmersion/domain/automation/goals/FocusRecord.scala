package com.histmersion
package domain.automation.goals

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What the focus recorded about one goal, as [[FocusService.InMemory]] holds it. */
final case class FocusRecord(
    active: FocusRecord.Active,
    pinned: FocusRecord.Pinned,
    priority: FocusRecord.Priority,
    since: Option[Instant],
    why: Option[FocusRecord.Why],
    leftAt: Option[Instant],
    leftWhy: Option[FocusRecord.LeftWhy]
)

object FocusRecord:
  type Active = Active.T
  object Active extends RefinedType[Boolean, Pure]
  type Pinned = Pinned.T
  object Pinned extends RefinedType[Boolean, Pure]
  type Priority = Priority.T
  object Priority extends RefinedType[Int, Pure]
  type Why = Why.T
  object Why extends RefinedType[String, Pure]
  type LeftWhy = LeftWhy.T
  object LeftWhy extends RefinedType[String, Pure]

  val none: FocusRecord = FocusRecord(Active(false), Pinned(false), Priority(0), None, None, None, None)
