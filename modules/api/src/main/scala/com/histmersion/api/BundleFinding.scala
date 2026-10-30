package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.values.Confidence

final case class BundleFinding(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    id: String,
    claim: String,
    region: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    period: Option[BundleRange],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
