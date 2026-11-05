package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*
import java.util.UUID

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
  object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Retracted = Retracted.T
  object Retracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notices = Notices.T
  object Notices extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Influential = Influential.T
  object Influential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Verdict = Verdict.T
  object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
