package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── temporal geography (V016) ───────────────────────────────────────────────────────────────

/** A dated geometry for a place — one place can have many footprints with different roles, periods and provenances.
  * Follows the Pleiades Place/Location split. `role` describes what the shape represents: `extent` (the full
  * territory), `core` (heartland), `maximal` (greatest extent), `frontier` (a border zone), `seat` (a point where power
  * sits), `snapshot` (one moment in time).
  */
final case class PlaceFootprint(
    id: Int,
    placeId: String,
    role: String = "extent",
    period: Option[Period] = None,
    certainty: Option[String] = None,
    accuracyM: Option[Float] = None,
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceFootprint:
  given Schema[PlaceFootprint] = summon[KindlingsSchema[PlaceFootprint]].schema
  val Roles: List[String] = List("extent", "core", "maximal", "frontier", "seat", "snapshot")
  val Certainties: List[String] = List("definite", "approximate", "uncertain")
