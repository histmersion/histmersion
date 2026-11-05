package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.Acquire
import com.histmersion.domain.CoreQuota
import com.histmersion.api.library.Source

/** The acquisition's records the in-memory services share: the Retraction Watch mirror, the recorded assessments (each
  * with its stamp), each goal's candidates and watches, the metered upstreams' budgets and the acquisition list's
  * checks.
  */
final case class AcquisitionState(
    retractions: Map[AcquisitionState.RetractionsKey, Acquire.RetractionRow],
    assessments: Map[AcquisitionState.AssessmentsKey, (AssessmentRecord, AcquisitionState.AssessmentsValuePart2)],
    candidates: List[Source.Candidate],
    watches: Map[(AcquisitionState.WatchesKeyPart1, AcquisitionState.WatchesKeyPart2, AcquisitionState.WatchesKeyPart3, AcquisitionState.WatchesKeyPart4), com.histmersion.api.library.CandidateWatch],
    quotas: Map[AcquisitionState.QuotasKey, CoreQuota.State],
    checks: Map[AcquisitionState.ChecksKey, AcquireCheckRow]
)

object AcquisitionState:
  type RetractionsKey = RetractionsKey.T
  object RetractionsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssessmentsKey = AssessmentsKey.T
  object AssessmentsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssessmentsValuePart2 = AssessmentsValuePart2.T
  object AssessmentsValuePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchesKeyPart1 = WatchesKeyPart1.T
  object WatchesKeyPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchesKeyPart2 = WatchesKeyPart2.T
  object WatchesKeyPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchesKeyPart3 = WatchesKeyPart3.T
  object WatchesKeyPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WatchesKeyPart4 = WatchesKeyPart4.T
  object WatchesKeyPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotasKey = QuotasKey.T
  object QuotasKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChecksKey = ChecksKey.T
  object ChecksKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: AcquisitionState = AcquisitionState(Map.empty, Map.empty, Nil, Map.empty, Map.empty, Map.empty)
