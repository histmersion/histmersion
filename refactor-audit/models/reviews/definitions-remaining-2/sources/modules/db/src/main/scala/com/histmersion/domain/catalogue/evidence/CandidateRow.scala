package com.histmersion.domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.given_DbCodec_List

/** A stored candidate link of an event to a source, its passages as JSON. */
final private[evidence] case class CandidateRow(
    eventId: com.histmersion.domain.catalogue.Event.Id,
    citekey: com.histmersion.domain.library.Citekey,
    score: CandidateRow.Score,
    status: CandidateRow.Status,
    title: Option[CandidateRow.Title],
    findings: List[CandidateRow.Findings],
    passages: CandidateRow.Passages,
    reasons: List[CandidateRow.Reasons]
) derives DbCodec

object CandidateRow:
  type Score = Score.T
  object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Passages = Passages.T
  object Passages extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reasons = Reasons.T
  object Reasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
