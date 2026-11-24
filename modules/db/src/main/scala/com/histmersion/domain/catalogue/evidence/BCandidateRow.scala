package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A source that possibly discusses an event, as its bundle line reads it. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
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
  object Score extends Newtype[Double, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Findings = Findings.T
  object Findings extends Newtype[String, Pure]
  type Passages = Passages.T
  object Passages extends Newtype[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends Newtype[String, Pure]
  type ComputedAt = ComputedAt.T
  object ComputedAt extends Newtype[String, Pure]
