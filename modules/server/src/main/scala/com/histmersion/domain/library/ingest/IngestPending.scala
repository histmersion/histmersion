package com.histmersion
package domain.library.ingest

import com.histmersion.api.library.Ingest
import com.histmersion.util.jobs.Job
import com.histmersion.util.storage.Store
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Creator

/** The part of a queued job that lives in memory: the stored original and the metadata the uploader supplied. */
final case class IngestPending(
    params: Ingest.Params,
    citekey: Citekey,
    file: Store.Key,
    sha256: Sha256,
    creator: Creator.New,
    kind: Job.Kind = IngestPending.IngestKind
)

object IngestPending:
  val IngestKind: Job.Kind = Job.Kind.applyUnsafe(Ingest.KindIngest)
  val ReocrKind: Job.Kind = Job.Kind.applyUnsafe(Ingest.KindReocr)
