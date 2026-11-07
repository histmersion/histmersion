package com.histmersion.domain.automation.goals

import java.time.Instant

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
  object Active extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pinned = Pinned.T
  object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Priority = Priority.T
  object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Why = Why.T
  object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeftWhy = LeftWhy.T
  object LeftWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val none: FocusRecord = FocusRecord(com.histmersion.domain.automation.goals.FocusRecord.Active(false), com.histmersion.domain.automation.goals.FocusRecord.Pinned(false), com.histmersion.domain.automation.goals.FocusRecord.Priority(0), None, None, None, None)
