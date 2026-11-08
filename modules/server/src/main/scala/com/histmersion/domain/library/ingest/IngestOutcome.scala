package com.histmersion
package domain.library.ingest

import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** @param created false when the citekey already existed and was enriched rather than replaced */
final case class IngestOutcome(citekey: Citekey, created: IngestOutcome.Created, jobId: Option[Job.Id], detail: IngestOutcome.Detail)

object IngestOutcome:
  type Created = Created.T
  object Created extends RefinedType[Boolean, Pure]
  type Detail = Detail.T
  object Detail extends RefinedType[String, Pure]
