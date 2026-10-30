package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One override — `GET /api/places/overrides`, `histmersion places overrides`: what a person set
  * (`None` = the source's), why, and what the source says (`sourceKind`, `sourcePeriod*`; both `None`
  * = undated; `sourceLat`/`sourceLon` = the source's point when a point is set and the source gave one).
  */
final case class PlaceOverride(
    placeId: String,
    label: String,
    kind: Option[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodSet: Boolean,
    reason: Option[String],
    sourceKind: String,
    sourcePeriodStart: Option[Long],
    sourcePeriodEnd: Option[Long],
    updatedAt: String,
    lat: Option[Double] = None,
    lon: Option[Double] = None,
    sourceLat: Option[Double] = None,
    sourceLon: Option[Double] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceOverride:
  given Schema[PlaceOverride] = summon[KindlingsSchema[PlaceOverride]].schema
