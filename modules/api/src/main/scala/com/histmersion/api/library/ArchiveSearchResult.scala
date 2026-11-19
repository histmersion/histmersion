package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ArchiveSearchResult(
    query: ArchiveSearchResult.Query,
    count: ArchiveSearchResult.Count,
    items: List[ArchiveItem]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ArchiveSearchResult:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
