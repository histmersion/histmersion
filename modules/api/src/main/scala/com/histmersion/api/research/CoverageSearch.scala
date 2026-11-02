package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CoverageSearch(query: String, total: Int, cited: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageSearch:
  given Schema[CoverageSearch] = summon[KindlingsSchema[CoverageSearch]].schema
