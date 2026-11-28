package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

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
  object Verdict extends Newtype[String, Pure]
  enum Retracted derives CanEqual:
    case Retracted, Standing
  object Retracted extends TwoCases[Retracted]:
    extension (retracted: Retracted) def retracted: Boolean = retracted == Retracted
  type NoticesJson = NoticesJson.T
  object NoticesJson extends Newtype[String, Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends Newtype[Int, Pure]
  type Influential = Influential.T
  object Influential extends Newtype[Int, Pure]
