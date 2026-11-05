package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FigureLicenceFill(candidates: Int, updated: Int, applied: Boolean, bySource: List[MediaCount] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
