package com.histmersion.domain.library.ingest

/** @param created false when the citekey already existed and was enriched rather than replaced */
final case class IngestOutcome(citekey: com.histmersion.domain.library.Citekey, created: IngestOutcome.Created, jobId: Option[com.histmersion.util.jobs.Job.Id], detail: IngestOutcome.Detail)

object IngestOutcome:
  type Created = Created.T
  object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
