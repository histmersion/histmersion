package com.histmersion
package domain.automation.managers

import com.histmersion.api.automation.Managers.{Hold, ItemState}

import java.time.Instant
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    runId: Option[Run.Id],
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
  object Id extends RefinedType[Long, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Target = Target.T
  object Target extends RefinedType[String, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Priority = Priority.T
  object Priority extends RefinedType[Int, Pure]
  type Attempts = Attempts.T
  object Attempts extends RefinedType[Int, Pure]
  type Failures = Failures.T
  object Failures extends RefinedType[Int, Pure]
  type EvaluatedRun = EvaluatedRun.T
  object EvaluatedRun extends RefinedType[String, Pure]
  type Outcome = Outcome.T
  object Outcome extends RefinedType[String, Pure]
  type Origin = Origin.T
  object Origin extends RefinedType[String, Pure]
