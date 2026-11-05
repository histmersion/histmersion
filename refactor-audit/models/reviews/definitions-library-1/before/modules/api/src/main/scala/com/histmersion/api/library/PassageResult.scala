package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PassageResult(
    citekey: String,
    query: String,
    regex: Boolean,
    matches: Int,
    capped: Boolean,
    total: Int,
    passages: List[PassageHit],
    bodyChars: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
