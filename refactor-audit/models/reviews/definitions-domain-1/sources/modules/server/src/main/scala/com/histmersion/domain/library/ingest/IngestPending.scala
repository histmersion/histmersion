package com.histmersion.domain.library.ingest

import com.histmersion.api.library.Ingest
import com.histmersion.util.storage.Store

/** The part of a queued job that lives in memory: the stored original and the metadata the uploader supplied. */
final case class IngestPending(params: Ingest.Params, file: Store.Key, sha256: com.histmersion.Sha256, kind: IngestPending.Kind = Ingest.KindIngest)

object IngestPending:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
