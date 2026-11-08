package com.histmersion
package util.jobs

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Id extends RefinedType[String, Pure]
  type Error = Error.T
  object Error extends RefinedType[String, Pure]
  type Progress = Progress.T
  object Progress extends RefinedType[String, Pure]
  type Warnings = Warnings.T
  object Warnings extends RefinedType[String, Pure]
  type Created = Created.T
  object Created extends RefinedType[Long, Pure]
  type Updated = Updated.T
  object Updated extends RefinedType[Long, Pure]
