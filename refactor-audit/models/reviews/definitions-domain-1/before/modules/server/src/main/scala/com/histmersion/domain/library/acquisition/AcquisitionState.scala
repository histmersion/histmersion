package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.Acquire
import com.histmersion.domain.CoreQuota
import com.histmersion.api.library.Source

/** The acquisition's records the in-memory services share: the Retraction Watch mirror, the recorded assessments (each
  * with its stamp), each goal's candidates and watches, the metered upstreams' budgets and the acquisition list's
  * checks.
  */
final case class AcquisitionState(
    retractions: Map[String, Acquire.RetractionRow],
    assessments: Map[String, (AssessmentRecord, String)],
    candidates: List[Source.Candidate],
    watches: Map[(String, String, String, String), com.histmersion.api.library.CandidateWatch],
    quotas: Map[String, CoreQuota.State],
    checks: Map[Int, AcquireCheckRow]
)

object AcquisitionState:
  val empty: AcquisitionState = AcquisitionState(Map.empty, Map.empty, Nil, Map.empty, Map.empty, Map.empty)
