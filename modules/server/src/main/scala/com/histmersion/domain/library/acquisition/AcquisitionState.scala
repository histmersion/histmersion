package com.histmersion
package domain.library.acquisition

import com.histmersion.api.library.Acquire
import com.histmersion.domain.CoreQuota
import com.histmersion.api.library.Source
import com.histmersion.api.library.CandidateWatch
import io.github.iltotore.iron.constraint.any.Pure

/** The acquisition's records the in-memory services share: the Retraction Watch mirror, the recorded assessments (each
  * with its stamp), each goal's candidates and watches, the metered upstreams' budgets and the acquisition list's
  * checks.
  */
final case class AcquisitionState(
    retractions: Map[AcquisitionState.RetractionsKey, Acquire.RetractionRow],
    assessments: Map[AcquisitionState.AssessmentsKey, (AssessmentRecord, AssessmentService.CheckedAt)],
    candidates: List[Source.Candidate],
    watches: Map[
      (CandidateWatch.Goal, CandidateWatch.Kind, CandidateWatch.Subject, CandidateWatch.Index),
      CandidateWatch
    ],
    quotas: Map[AcquisitionState.QuotasKey, CoreQuota.State],
    checks: Map[AcquisitionState.ChecksKey, AcquireCheckRow]
)

object AcquisitionState:
  type RetractionsKey = RetractionsKey.T
  object RetractionsKey extends Newtype[String, Pure]
  type AssessmentsKey = AssessmentsKey.T
  object AssessmentsKey extends Newtype[String, Pure]
  type QuotasKey = QuotasKey.T
  object QuotasKey extends Newtype[String, Pure]
  type ChecksKey = ChecksKey.T
  object ChecksKey extends Newtype[Int, Pure]

  val empty: AcquisitionState = AcquisitionState(Map.empty, Map.empty, Nil, Map.empty, Map.empty, Map.empty)
