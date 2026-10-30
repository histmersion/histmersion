package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CoverageSearch(query: String, total: Int, cited: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageSearch:
  given Schema[CoverageSearch] = summon[KindlingsSchema[CoverageSearch]].schema
