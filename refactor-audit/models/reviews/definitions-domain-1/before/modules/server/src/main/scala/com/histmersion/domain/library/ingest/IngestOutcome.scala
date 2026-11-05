package com.histmersion.domain.library.ingest

/** @param created false when the citekey already existed and was enriched rather than replaced */
final case class IngestOutcome(citekey: String, created: Boolean, jobId: Option[String], detail: String)
