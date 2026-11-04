package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssessRequest(id: String, apply: Boolean = false, forceStatus: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
