package com.histmersion.api

/** What a lead is computed from: the event, its tier, the catalogue's magnitude / VEI, the corpus's findings near it
  * (within `km` and `years`), its proposed (unconfirmed) sources and attesting ones.
  */
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
