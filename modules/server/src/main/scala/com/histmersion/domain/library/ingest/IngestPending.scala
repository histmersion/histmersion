package com.histmersion
package domain.library.ingest

import com.histmersion.api.library.Ingest
import com.histmersion.util.jobs.Job
import com.histmersion.util.storage.Store
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The part of a queued job that lives in memory: the stored original and the metadata the uploader supplied. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class IngestPending(
    params: Ingest.Params,
    citekey: Citekey,
    file: Store.Key,
    sha256: Sha256,
    creator: Creator.New,
    kind: Job.Kind = IngestPending.IngestKind
)

object IngestPending:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val IngestKind: Job.Kind = Job.Kind.applyUnsafe(Ingest.KindIngest)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val ReocrKind: Job.Kind = Job.Kind.applyUnsafe(Ingest.KindReocr)
