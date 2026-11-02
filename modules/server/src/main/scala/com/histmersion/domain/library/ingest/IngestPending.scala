package com.histmersion.domain.library.ingest

import com.histmersion.api.{Ingest, IngestParams}
import com.histmersion.util.storage.StoreKey

/** The part of a queued job that lives in memory: the stored original and the metadata the uploader supplied. */
final case class IngestPending(params: IngestParams, file: StoreKey, sha256: String, kind: String = Ingest.KindIngest)
