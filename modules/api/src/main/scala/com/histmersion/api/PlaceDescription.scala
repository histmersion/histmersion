package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One description of a place, in one language (`""` unknown), from one source (`wikidata`, `lpf:<dataset>`).
  */
final case class PlaceDescription(text: String, lang: String = "", source: String = "")
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceDescription:
  given Schema[PlaceDescription] = summon[KindlingsSchema[PlaceDescription]].schema
