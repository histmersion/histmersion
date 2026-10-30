package com.histmersion.api

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
