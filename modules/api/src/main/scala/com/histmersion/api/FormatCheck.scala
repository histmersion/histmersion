package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class FormatCheck(text: String, field: FormatField = FormatField.Body, fix: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object FormatCheck:
  given Schema[FormatCheck] = summon[KindlingsSchema[FormatCheck]].schema
