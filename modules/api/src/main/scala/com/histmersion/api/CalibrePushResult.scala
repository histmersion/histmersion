package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CalibrePushResult(citekey: String, calibreId: String, ok: Boolean)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CalibrePushResult:
  given Schema[CalibrePushResult] = summon[KindlingsSchema[CalibrePushResult]].schema
