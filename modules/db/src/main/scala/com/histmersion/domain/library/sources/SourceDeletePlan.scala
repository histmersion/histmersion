package com.histmersion.domain.library.sources

import com.histmersion.domain.library.sources.referrers.DeleteReport
import com.histmersion.api.library.Source

/** What deleting a source does: the answer, plus what finding its stored original needs — its digest, and whether
  * another source still holds the same file (then the file stays). `keys` = the citekey and its old citekeys (the
  * acquisition spool is keyed by whichever one the file came in under); `report` is what the referrers said.
  */
final case class SourceDeletePlan(
    deletion: Source.Deletion,
    sha256: Option[SourceDeletePlan.Sha256],
    shaShared: SourceDeletePlan.ShaShared,
    keys: List[SourceDeletePlan.Keys],
    report: DeleteReport
)

object SourceDeletePlan:
  type Sha256 = Sha256.T
  object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShaShared = ShaShared.T
  object ShaShared extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
