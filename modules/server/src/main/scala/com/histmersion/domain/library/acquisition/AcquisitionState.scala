package com.histmersion.domain.library.acquisition

import com.histmersion.api.{Acquire, SourceCandidate}
import com.histmersion.domain.CoreQuota

/** The acquisition's records the in-memory services share: the Retraction Watch mirror, the recorded assessments (each
  * with its stamp), each goal's candidates and watches, the metered upstreams' budgets and the acquisition list's
  * checks.
  */
final case class AcquisitionState(
    retractions: Map[String, Acquire.RetractionRow],
    assessments: Map[String, (AssessmentRecord, String)],
    candidates: List[SourceCandidate],
    watches: Map[(String, String, String, String), com.histmersion.api.CandidateWatch],
    quotas: Map[String, CoreQuota.State],
    checks: Map[Int, AcquireCheckRow]
)

object AcquisitionState:
  val empty: AcquisitionState = AcquisitionState(Map.empty, Map.empty, Nil, Map.empty, Map.empty, Map.empty)
