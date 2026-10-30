package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ArchiveSearchResult(query: String, count: Int, items: List[ArchiveItem])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveSearchResult:
  given Schema[ArchiveSearchResult] = summon[KindlingsSchema[ArchiveSearchResult]].schema
