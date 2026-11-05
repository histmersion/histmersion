package com.histmersion.domain.library.acquisition

/** A verdict as it is recorded against a stored source: `noticesJson` is the raw JSON of everything that was found, so
  * a later reader can see WHY without asking Crossref again.
  */
final case class AssessmentRecord(
    citekey: com.histmersion.domain.library.Citekey,
    verdict: AssessmentRecord.Verdict,
    retracted: AssessmentRecord.Retracted,
    noticesJson: AssessmentRecord.NoticesJson,
    citedBy: Option[AssessmentRecord.CitedBy],
    influential: Option[AssessmentRecord.Influential]
)

object AssessmentRecord:
  type Verdict = Verdict.T
  object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Retracted = Retracted.T
  object Retracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticesJson = NoticesJson.T
  object NoticesJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedBy = CitedBy.T
  object CitedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Influential = Influential.T
  object Influential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
