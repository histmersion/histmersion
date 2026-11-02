package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ManualPlaceName(placeId: String, label: String, name: String, lang: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ManualPlaceName:
  given Schema[ManualPlaceName] = summon[KindlingsSchema[ManualPlaceName]].schema
