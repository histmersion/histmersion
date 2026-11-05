package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{FollowupRow, WorkRef}

final class SemanticScholar(cfg: AcquireConfig, http: Http) extends CitationsClient:

  private val Fields =
    "intents,isInfluential,contexts,citingPaper.title,citingPaper.year," +
      "citingPaper.externalIds,citingPaper.citationCount"

  private def headers = cfg.semanticScholar.key.map("x-api-key" -> _).toMap

  /** Semantic Scholar is the only source of citation INTENTS (`background|methodology|result`) and of the
    * `isInfluential` flag, which is what makes a follow-up worth reading. When it is down or rate limited we fall back
    * to OpenAlex's plain citation list — fewer signals, but never nothing.
    */
  def citing(work: WorkRef, since: Option[com.histmersion.domain.library.acquisition.SemanticScholar.CitingSince], limit: com.histmersion.domain.library.acquisition.SemanticScholar.CitingLimit): Either[com.histmersion.domain.library.acquisition.SemanticScholar.CitingResultPart1, (com.histmersion.domain.library.acquisition.SemanticScholar.CitingResultPart2Part1, List[FollowupRow])] =
    work.doi match
      case Some(doi) =>
        s2(doi.value, since, limit) match
          case Right(rows) if rows.nonEmpty => Right(("semantic-scholar", rows))
          case Right(_)                     => openalexCiting(work, since, limit).map(("openalex", _))
          case Left(err)                    =>
            scribe.warn(s"acquire: Semantic Scholar unavailable ($err) — using OpenAlex citations")
            openalexCiting(work, since, limit).map(("openalex", _))
      case None => openalexCiting(work, since, limit).map(("openalex", _))

  private def s2(doi: com.histmersion.domain.library.Doi, since: Option[com.histmersion.domain.library.acquisition.SemanticScholar.S2Since], limit: com.histmersion.domain.library.acquisition.SemanticScholar.S2Limit): Either[com.histmersion.domain.library.acquisition.SemanticScholar.S2ResultPart1, List[FollowupRow]] =
    def page(offset: Int, acc: List[FollowupRow]): Either[String, List[FollowupRow]] =
      if acc.size >= limit then Right(acc)
      else
        http
          .json(
            Url(
              cfg.semanticScholar.url,
              s"/paper/DOI:$doi/citations",
              "fields" -> Fields,
              "limit" -> math.min(100, limit - acc.size).toString,
              "offset" -> offset.toString
            ),
            headers
          )
          .flatMap { j =>
            val rows = AcquireParse.s2Citations(j).map(_.wire)
            val kept = acc ++ rows.filter(r => since.forall(y => r.year.forall(_ >= y)))
            AcquireParse.s2Next(j) match
              case Some(next) if rows.nonEmpty && next > offset => page(next, kept)
              case _                                            => Right(kept)
          }
    page(0, Nil)

  private def openalexCiting(work: WorkRef, since: Option[com.histmersion.domain.library.acquisition.SemanticScholar.OpenalexCitingSince], limit: com.histmersion.domain.library.acquisition.SemanticScholar.OpenalexCitingLimit): Either[com.histmersion.domain.library.acquisition.SemanticScholar.OpenalexCitingResultPart1, List[FollowupRow]] =
    work.openalexId match
      case None     => Right(Nil)
      case Some(id) =>
        val filter = List(Some(s"cites:$id"), since.map(y => s"from_publication_date:$y-01-01")).flatten.mkString(",")
        http
          .json(
            Url(
              cfg.openalex.url,
              "/works",
              "filter" -> filter,
              "per-page" -> math.max(1, math.min(limit, 50)).toString,
              "sort" -> "cited_by_count:desc",
              "select" -> "id,doi,display_name,publication_year,authorships,cited_by_count,locations",
              "mailto" -> cfg.mailto
            )
          )
          .map(AcquireParse.openalexCitations(_).map(_.wire))

object SemanticScholar:
  type CitingResultPart1 = CitingResultPart1.T
  object CitingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingResultPart2Part1 = CitingResultPart2Part1.T
  object CitingResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingSince = CitingSince.T
  object CitingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type S2ResultPart1 = S2ResultPart1.T
  object S2ResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type S2Since = S2Since.T
  object S2Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type S2Limit = S2Limit.T
  object S2Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexCitingResultPart1 = OpenalexCitingResultPart1.T
  object OpenalexCitingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexCitingSince = OpenalexCitingSince.T
  object OpenalexCitingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexCitingLimit = OpenalexCitingLimit.T
  object OpenalexCitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
