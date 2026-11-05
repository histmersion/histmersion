package com.histmersion.api.catalogue

import com.histmersion.api.TimeSpan
import com.histmersion.api.given
final case class LeadFacts(
    eventId: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    tier: String,
    vei: Option[Int],
    magnitude: Option[Double],
    findingsNear: Int,
    km: Double,
    years: Long,
    proposed: Int,
    attesting: Int
)
