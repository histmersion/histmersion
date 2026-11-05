package com.histmersion.domain.library.acquisition

/** A verdict as it is recorded against a stored source: `noticesJson` is the raw JSON of everything that was found, so
  * a later reader can see WHY without asking Crossref again.
  */
final case class AssessmentRecord(
    citekey: String,
    verdict: String,
    retracted: Boolean,
    noticesJson: String,
    citedBy: Option[Int],
    influential: Option[Int]
)
