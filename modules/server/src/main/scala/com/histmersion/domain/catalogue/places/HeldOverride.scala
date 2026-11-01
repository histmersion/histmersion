package com.histmersion.domain.catalogue.places

import com.histmersion.api.Period

/** A person's kind and period for a place as the in-memory catalogue holds it, with what the place's source said
  * before: a reset puts that back.
  */
final case class HeldOverride(
    placeId: String,
    kind: Option[String],
    period: Option[(Option[Long], Option[Long])],
    reason: Option[String],
    sourceKind: String,
    sourcePeriod: Option[Period],
    updatedAt: String
)
