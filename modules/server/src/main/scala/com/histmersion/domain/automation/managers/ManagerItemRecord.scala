package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Managers.{Hold, ItemState}

import java.time.Instant

/** A manager's work item as [[ManagerService.InMemory]] holds it. */
final case class ManagerItemRecord(
    id: ManagerItemRecord.Id,
    managerId: ManagerItemRecord.ManagerId,
    kind: ManagerItemRecord.Kind,
    target: ManagerItemRecord.Target,
    status: ManagerItemRecord.Status,
    priority: ManagerItemRecord.Priority,
    attempts: ManagerItemRecord.Attempts,
    failures: ManagerItemRecord.Failures,
    runId: Option[com.histmersion.domain.automation.Run.Id],
    evaluatedRun: Option[ManagerItemRecord.EvaluatedRun],
    notBefore: Option[Instant],
    hold: Option[Hold],
    state: ItemState,
    outcome: Option[ManagerItemRecord.Outcome],
    origin: ManagerItemRecord.Origin,
    createdAt: Instant,
    updatedAt: Instant
)

object ManagerItemRecord:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Priority = Priority.T
  object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attempts = Attempts.T
  object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failures = Failures.T
  object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedRun = EvaluatedRun.T
  object EvaluatedRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Origin = Origin.T
  object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
