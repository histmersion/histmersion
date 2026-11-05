package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class SearchHit(
    citekey: com.histmersion.api.library.Citekey,
    title: com.histmersion.api.library.Title,
    year: Option[com.histmersion.api.catalogue.Year],
    status: com.histmersion.api.library.Source.Status,
    topics: List[SearchHit.Topics],
    regions: List[SearchHit.Regions],
    periodStart: Option[com.histmersion.api.catalogue.Year],
    periodEnd: Option[com.histmersion.api.catalogue.Year],
    summary: Option[SearchHit.Summary],
    snippet: Option[SearchHit.Snippet] = None,
    matchedHeading: Option[SearchHit.MatchedHeading] = None,
    doi: Option[com.histmersion.api.library.Doi] = None,
    authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object SearchHit:
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Snippet = Snippet.T
  object Snippet extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchedHeading = MatchedHeading.T
  object MatchedHeading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
