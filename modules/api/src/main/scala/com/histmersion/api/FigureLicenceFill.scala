package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class FigureLicenceFill(candidates: Int, updated: Int, applied: Boolean, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object FigureLicenceFill:
  given Schema[FigureLicenceFill] = summon[KindlingsSchema[FigureLicenceFill]].schema
