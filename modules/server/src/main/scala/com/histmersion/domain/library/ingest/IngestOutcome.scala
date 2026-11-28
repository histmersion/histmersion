package com.histmersion
package domain.library.ingest

import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** @param created false when the citekey already existed and was enriched rather than replaced */
final case class IngestOutcome(
    citekey: Citekey,
    created: IngestOutcome.Created,
    jobId: Option[Job.Id],
    detail: IngestOutcome.Detail
)

object IngestOutcome:
  enum Created derives CanEqual:
    case Created, Existing
  object Created extends TwoCases[Created]:
    extension (created: Created) def created: Boolean = created == Created
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
