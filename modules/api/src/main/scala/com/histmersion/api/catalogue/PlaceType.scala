package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PlaceType(
    placeId: String,
    typeLabel: String,
    period: Option[Period] = None,
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceType:
  given Schema[PlaceType] = summon[KindlingsSchema[PlaceType]].schema
