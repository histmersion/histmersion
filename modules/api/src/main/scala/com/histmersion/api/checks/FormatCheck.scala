package com.histmersion.api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FormatCheck(text: String, field: FormatField = FormatField.Body, fix: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object FormatCheck:
  given Schema[FormatCheck] = summon[KindlingsSchema[FormatCheck]].schema
