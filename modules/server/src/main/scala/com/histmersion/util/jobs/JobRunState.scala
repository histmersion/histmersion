package com.histmersion
package util.jobs

import com.histmersion.api.Problem
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

/** A run's row as [[JobRuns.InMemory]] holds it: what it was created with, what changed since, the order it was made
  * (`created`) and last changed (`updated`) in, and when.
  */
final case class JobRunState(
    id: JobRunState.Id,
    row: RunRow,
    error: Option[Problem],
    progress: Option[JobRunState.Progress],
    resumeAt: Option[Instant],
    warnings: List[JobRunState.Warnings],
    heartbeatAt: Option[Instant],
    created: JobRunState.Created,
    updated: JobRunState.Updated,
    createdAt: Instant,
    updatedAt: Instant
) derives CanEqual

object JobRunState:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Progress = Progress.T
  object Progress extends Newtype[String, Pure]
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
  type Created = Created.T
  object Created extends Newtype[Long, Pure]
  type Updated = Updated.T
  object Updated extends Newtype[Long, Pure]
