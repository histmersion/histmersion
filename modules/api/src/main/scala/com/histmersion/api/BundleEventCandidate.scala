package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID

/** A source that POSSIBLY discusses an event (`event_link_candidates`, V085), with its evidence and the person's
  * decision. An import keeps the newer evidence (`computedAt`), and a decision (`confirmed`, `rejected`) over a
  * proposal — never a proposal over a decision ([[BundleEventSets.candidateStatus]]).
  */
final case class BundleEventCandidate(
    eventUid: UUID,
    sourceUid: UUID,
    citekey: String,
    score: Double,
    status: String,
    findings: List[String],
    passages: Json,
    reasons: List[String],
    computedAt: String
) derives KindlingsCodecAsObject
