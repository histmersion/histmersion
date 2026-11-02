package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PlaceDescription(text: String, lang: String = "", source: String = "")
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceDescription:
  given Schema[PlaceDescription] = summon[KindlingsSchema[PlaceDescription]].schema
