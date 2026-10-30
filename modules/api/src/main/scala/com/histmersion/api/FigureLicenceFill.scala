package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/media/fill-licences`: a source's figures take the source's licence where the source records one and the
  * figure has none. Without `apply` it only counts.
  */
final case class FigureLicenceFill(candidates: Int, updated: Int, applied: Boolean, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object FigureLicenceFill:
  given Schema[FigureLicenceFill] = summon[KindlingsSchema[FigureLicenceFill]].schema
