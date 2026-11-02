package com.histmersion.api.library
import com.histmersion.domain.library.Confidence
import com.histmersion.api.GeoPoint
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given

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
