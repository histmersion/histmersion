package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A dated type for a place — a settlement that becomes a port, a village that becomes a capital. */
final case class PlaceType(
    placeId: String,
    typeLabel: String,
    period: Option[Period] = None,
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceType:
  given Schema[PlaceType] = summon[KindlingsSchema[PlaceType]].schema
