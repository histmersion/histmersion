package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID
import com.histmersion.api.given

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
