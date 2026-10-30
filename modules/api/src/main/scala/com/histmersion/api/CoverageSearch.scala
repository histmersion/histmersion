package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One of the writer's further searches: how many sources it returns, and which of them the article cites. */
final case class CoverageSearch(query: String, total: Int, cited: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageSearch:
  given Schema[CoverageSearch] = summon[KindlingsSchema[CoverageSearch]].schema
