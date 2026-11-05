package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Managers.ManagerConfig

import java.time.Instant

/** A manager as [[ManagerService.InMemory]] holds it. */
final case class ManagerRecord(
    id: ManagerRecord.Id,
    kind: com.histmersion.domain.automation.Manager.Kind,
    goal: Option[ManagerRecord.Goal],
    state: com.histmersion.domain.automation.Manager.State,
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
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PauseRuns = PauseRuns.T
  object PauseRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusNote = StatusNote.T
  object StatusNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cron = Cron.T
  object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
