package com.histmersion.util.jobs

import java.time.Instant

/** A run's row as [[JobRuns.InMemory]] holds it: what it was created with, what changed since, the order it was made
  * (`created`) and last changed (`updated`) in, and when.
  */
final case class JobRunState(
    id: JobRunState.Id,
    row: RunRow,
    error: Option[JobRunState.Error],
    progress: Option[JobRunState.Progress],
    resumeAt: Option[Instant],
    warnings: List[JobRunState.Warnings],
    heartbeatAt: Option[Instant],
    created: JobRunState.Created,
    updated: JobRunState.Updated,
    createdAt: Instant,
    updatedAt: Instant
)

object JobRunState:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Progress = Progress.T
  object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Created = Created.T
  object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
