package com.histmersion.api

import com.histmersion.values.Confidence

/** A geo-located, time-scoped claim mined from a source. */
final case class Finding(
    id: String,
    citekey: String,
    claim: String,
    region: Option[String],
    geo: Option[GeoPoint],
    period: Option[Period],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String] = None
)
