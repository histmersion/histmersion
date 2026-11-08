package com.histmersion
package domain.automation.managers

import com.histmersion.api.automation.Managers.ManagerConfig

import java.time.Instant
import com.histmersion.domain.automation.Manager
import io.github.iltotore.iron.constraint.any.Pure

/** A manager as [[ManagerService.InMemory]] holds it. */
final case class ManagerRecord(
    id: ManagerRecord.Id,
    kind: Manager.Kind,
    goal: Option[ManagerRecord.Goal],
    state: Manager.State,
    pauseRuns: ManagerRecord.PauseRuns,
    config: ManagerConfig,
    statusNote: Option[ManagerRecord.StatusNote],
    cron: Option[ManagerRecord.Cron],
    nextTickAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant
)

object ManagerRecord:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type PauseRuns = PauseRuns.T
  object PauseRuns extends Newtype[Boolean, Pure]
  type StatusNote = StatusNote.T
  object StatusNote extends Newtype[String, Pure]
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
