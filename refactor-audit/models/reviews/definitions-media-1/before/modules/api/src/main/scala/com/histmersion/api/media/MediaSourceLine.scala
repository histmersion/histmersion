package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaSourceLine(citekey: String, role: String, provider: String, detail: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
