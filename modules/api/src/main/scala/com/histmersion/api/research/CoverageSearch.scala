package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CoverageSearch(query: CoverageSearch.Query, total: CoverageSearch.Total, cited: List[com.histmersion.api.library.Citekey])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CoverageSearch:
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
