package com.histmersion.domain.library.ingest

import com.histmersion.api.library.Ingest
import com.histmersion.util.jobs.Job
import com.histmersion.util.storage.Store

/** The part of a queued job that lives in memory: the stored original and the metadata the uploader supplied. */
final case class IngestPending(
    params: Ingest.Params,
    citekey: com.histmersion.domain.library.Citekey,
    file: Store.Key,
    sha256: com.histmersion.Sha256,
    kind: Job.Kind = IngestPending.IngestKind
)

object IngestPending:
  val IngestKind: Job.Kind = Job.Kind.applyUnsafe(Ingest.KindIngest)
  val ReocrKind: Job.Kind = Job.Kind.applyUnsafe(Ingest.KindReocr)
