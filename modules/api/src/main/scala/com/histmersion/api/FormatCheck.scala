package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/lint/markdown`: check Markdown that is not stored yet (a file an agent is about to `article put`). `fix`
  * returns the repaired text in [[FormatReport.text]].
  */
final case class FormatCheck(text: String, field: FormatField = FormatField.Body, fix: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object FormatCheck:
  given Schema[FormatCheck] = summon[KindlingsSchema[FormatCheck]].schema
