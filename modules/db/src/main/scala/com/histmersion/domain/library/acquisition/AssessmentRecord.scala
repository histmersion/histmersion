package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A verdict as it is recorded against a stored source: `noticesJson` is the raw JSON of everything that was found, so
  * a later reader can see WHY without asking Crossref again.
  */
final case class AssessmentRecord(
    citekey: Citekey,
    verdict: AssessmentRecord.Verdict,
    retracted: AssessmentRecord.Retracted,
    noticesJson: AssessmentRecord.NoticesJson,
    citedBy: Option[AssessmentRecord.CitedBy],
    influential: Option[AssessmentRecord.Influential]
)

object AssessmentRecord:
  type Verdict = Verdict.T
  object Verdict extends RefinedType[String, Pure]
  type Retracted = Retracted.T
  object Retracted extends RefinedType[Boolean, Pure]
  type NoticesJson = NoticesJson.T
  object NoticesJson extends RefinedType[String, Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends RefinedType[Int, Pure]
  type Influential = Influential.T
  object Influential extends RefinedType[Int, Pure]
