package com.histmersion.util.jobs

import java.time.Instant

/** A run's row as [[JobRuns.InMemory]] holds it: what it was created with, what changed since, the order it was made
  * (`created`) and last changed (`updated`) in, and when.
  */
final case class JobRunState(
    id: String,
    row: RunRow,
    error: Option[String],
    progress: Option[String],
    resumeAt: Option[Instant],
    warnings: List[String],
    heartbeatAt: Option[Instant],
    created: Long,
    updated: Long,
    createdAt: Instant,
    updatedAt: Instant
)
