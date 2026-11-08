package com.histmersion
package domain.library.sources

import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** A source's assessment as the bundle reads it, with the source's uid. */
final private[sources] case class BAssessmentRow(
    sourceUid: UUID,
    checkedAt: BAssessmentRow.CheckedAt,
    retracted: BAssessmentRow.Retracted,
    notices: BAssessmentRow.Notices,
    citedBy: Option[BAssessmentRow.CitedBy],
    influential: Option[BAssessmentRow.Influential],
    verdict: BAssessmentRow.Verdict
) derives DbCodec

object BAssessmentRow:
  type CheckedAt = CheckedAt.T
  object CheckedAt extends Newtype[String, Pure]
  type Retracted = Retracted.T
  object Retracted extends Newtype[Boolean, Pure]
  type Notices = Notices.T
  object Notices extends Newtype[String, Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends Newtype[Int, Pure]
  type Influential = Influential.T
  object Influential extends Newtype[Int, Pure]
  type Verdict = Verdict.T
  object Verdict extends Newtype[String, Pure]
