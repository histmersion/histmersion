package com.histmersion
package domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A stored candidate link of an event to a source, its passages as JSON. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final private[evidence] case class CandidateRow(
    eventId: Event.Id,
    citekey: Citekey,
    score: CandidateRow.Score,
    status: CandidateRow.Status,
    title: Option[CandidateRow.Title],
    findings: List[CandidateRow.Findings],
    passages: CandidateRow.Passages,
    reasons: List[CandidateRow.Reasons]
) derives DbCodec

object CandidateRow:
  type Score = Score.T
  object Score extends Newtype[Double, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Findings = Findings.T
  object Findings extends Newtype[String, Pure]
  type Passages = Passages.T
  object Passages extends Newtype[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends Newtype[String, Pure]
