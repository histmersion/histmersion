package com.histmersion.domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given

/** A source that possibly discusses an event, as its bundle line reads it. */
final private[evidence] case class BCandidateRow(
    eventUid: UUID,
    sourceUid: UUID,
    citekey: com.histmersion.domain.library.Citekey,
    score: BCandidateRow.Score,
    status: BCandidateRow.Status,
    findings: List[BCandidateRow.Findings],
    passages: BCandidateRow.Passages,
    reasons: List[BCandidateRow.Reasons],
    computedAt: BCandidateRow.ComputedAt
) derives DbCodec

object BCandidateRow:
  type Score = Score.T
  object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Passages = Passages.T
  object Passages extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reasons = Reasons.T
  object Reasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ComputedAt = ComputedAt.T
  object ComputedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
