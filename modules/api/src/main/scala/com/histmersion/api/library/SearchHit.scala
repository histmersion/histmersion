package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class SearchHit(
    citekey: Citekey,
    title: Title,
    year: Option[Year],
    status: Source.Status,
    topics: List[SearchHit.Topics],
    regions: List[SearchHit.Regions],
    periodStart: Option[Year],
    periodEnd: Option[Year],
    summary: Option[SearchHit.Summary],
    snippet: Option[SearchHit.Snippet] = None,
    matchedHeading: Option[SearchHit.MatchedHeading] = None,
    doi: Option[Doi] = None,
    authors: List[AuthorName] = (Nil).map(AuthorName.apply)
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object SearchHit:
  export Source.{Regions, Summary, Topics}
  export Search.Snippet
  type MatchedHeading = MatchedHeading.T
  object MatchedHeading extends Newtype[String, Pure]
