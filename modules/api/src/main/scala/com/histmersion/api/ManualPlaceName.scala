package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One name a person added — `GET /api/places/names`, `histmersion places name list`. */
final case class ManualPlaceName(placeId: String, label: String, name: String, lang: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ManualPlaceName:
  given Schema[ManualPlaceName] = summon[KindlingsSchema[ManualPlaceName]].schema
