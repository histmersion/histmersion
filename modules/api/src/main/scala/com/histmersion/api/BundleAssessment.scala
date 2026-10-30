package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID

final case class BundleAssessment(
    sourceUid: UUID,
    checkedAt: String,
    retracted: Boolean,
    notices: Json,
    citedBy: Option[Int],
    influential: Option[Int],
    verdict: String
) derives KindlingsCodecAsObject
