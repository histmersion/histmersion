package com.histmersion.domain.library.ingest

import com.histmersion.api.library.Ingest
import com.histmersion.util.storage.StoreKey

/** The part of a queued job that lives in memory: the stored original and the metadata the uploader supplied. */
final case class IngestPending(params: Ingest.Params, file: StoreKey, sha256: String, kind: String = Ingest.KindIngest)
