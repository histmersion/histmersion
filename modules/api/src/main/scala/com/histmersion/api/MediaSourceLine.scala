package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaSourceLine(citekey: String, role: String, provider: String, detail: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaSourceLine:
  given Schema[MediaSourceLine] = summon[KindlingsSchema[MediaSourceLine]].schema
