package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MatchSpan(start: Int, end: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
