package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A source that possibly discusses an event, as its bundle line reads it. */
final private[evidence] case class BCandidateRow(
    eventUid: UUID,
    sourceUid: UUID,
    citekey: Citekey,
    score: BCandidateRow.Score,
    status: BCandidateRow.Status,
    findings: List[BCandidateRow.Findings],
    passages: BCandidateRow.Passages,
    reasons: List[BCandidateRow.Reasons],
    computedAt: BCandidateRow.ComputedAt
) derives DbCodec

object BCandidateRow:
  type Score = Score.T
  object Score extends RefinedType[Double, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Findings = Findings.T
  object Findings extends RefinedType[String, Pure]
  type Passages = Passages.T
  object Passages extends RefinedType[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends RefinedType[String, Pure]
  type ComputedAt = ComputedAt.T
  object ComputedAt extends RefinedType[String, Pure]
