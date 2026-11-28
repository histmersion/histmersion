package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.sources.referrers.DeleteReport
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** What deleting a source does: the answer, plus what finding its stored original needs — its digest, and whether
  * another source still holds the same file (then the file stays). `keys` = the citekey and its old citekeys (the
  * acquisition spool is keyed by whichever one the file came in under); `report` is what the referrers said.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class SourceDeletePlan(
    deletion: Source.Deletion,
    sha256: Option[SourceDeletePlan.Sha256],
    shaShared: SourceDeletePlan.ShaShared,
    keys: List[SourceDeletePlan.Keys],
    report: DeleteReport
)

object SourceDeletePlan:
  type Sha256 = Sha256.T
  object Sha256 extends Newtype[String, Pure]
  enum ShaShared derives CanEqual:
    case Shared, Unique
  object ShaShared extends TwoCases[ShaShared]:
    extension (shaShared: ShaShared) def shaShared: Boolean = shaShared == Shared
  type Keys = Keys.T
  object Keys extends Newtype[String, Pure]
