package com.histmersion
package domain.automation.managers

import com.histmersion.api.automation.managers.Hold
import com.histmersion.api.automation.managers.ItemState

import java.time.Instant
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A manager's work item as [[ManagerService.InMemory]] holds it. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  object Id extends Newtype[Long, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type Attempts = Attempts.T
  object Attempts extends Newtype[Int, Pure]
  type Failures = Failures.T
  object Failures extends Newtype[Int, Pure]
  type EvaluatedRun = EvaluatedRun.T
  object EvaluatedRun extends Newtype[String, Pure]
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]
  type Origin = Origin.T
  object Origin extends Newtype[String, Pure]
