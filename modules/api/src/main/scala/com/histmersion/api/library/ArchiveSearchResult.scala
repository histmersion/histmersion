package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ArchiveSearchResult(query: String, count: Int, items: List[ArchiveItem])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
