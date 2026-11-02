package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PlaceGone(
    placeId: String,
    label: String,
    into: Option[String],
    intoLabel: Option[String],
    findingsBefore: Int,
    findingsAfter: Int,
    unlocated: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceGone:
  given Schema[PlaceGone] = summon[KindlingsSchema[PlaceGone]].schema
