package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** Follows the Pleiades Place/Location split: one place, many dated footprints. */
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
