package com.histmersion.domain.library.sources

import com.histmersion.api.SourceDeletion
import com.histmersion.domain.library.sources.referrers.DeleteReport

/** What deleting a source does: the answer, plus what finding its stored original needs — its digest, and whether
  * another source still holds the same file (then the file stays). `keys` = the citekey and its old citekeys (the
  * acquisition spool is keyed by whichever one the file came in under); `report` is what the referrers said.
  */
final case class SourceDeletePlan(
    deletion: SourceDeletion,
    sha256: Option[String],
    shaShared: Boolean,
    keys: List[String],
    report: DeleteReport
)
