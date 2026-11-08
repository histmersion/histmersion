package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ArchiveSearchResult(query: ArchiveSearchResult.Query, count: ArchiveSearchResult.Count, items: List[ArchiveItem])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ArchiveSearchResult:
  type Query = Query.T
  object Query extends RefinedType[String, Pure]
  type Count = Count.T
  object Count extends RefinedType[Int, Pure]
