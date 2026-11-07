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
  def citing(work: WorkRef, since: Option[com.histmersion.Year], limit: com.histmersion.domain.library.acquisition.SemanticScholar.Limit): Either[com.histmersion.domain.library.acquisition.SemanticScholar.Problem, (com.histmersion.domain.library.acquisition.SemanticScholar.CitingResultPart2Part1, List[FollowupRow])] =
    work.doi match
      case Some(doi) =>
        s2(doi.value, since, limit) match
          case Right(rows) if rows.nonEmpty => Right((com.histmersion.domain.library.acquisition.SemanticScholar.("semantic-scholar"), rows))
          case Right(_)                     => openalexCiting(work, since, limit).map((com.histmersion.domain.library.acquisition.SemanticScholar.("openalex"), _))
          case Left(err)                    =>
            scribe.warn(s"acquire: Semantic Scholar unavailable ($err) — using OpenAlex citations")
            openalexCiting(work, since, limit).map((com.histmersion.domain.library.acquisition.SemanticScholar.("openalex"), _))
      case None => openalexCiting(work, since, limit).map((com.histmersion.domain.library.acquisition.SemanticScholar.("openalex"), _))

  private def s2(doi: com.histmersion.domain.library.Doi, since: Option[com.histmersion.Year], limit: com.histmersion.domain.library.acquisition.SemanticScholar.Limit): Either[com.histmersion.domain.library.acquisition.SemanticScholar.Problem, List[FollowupRow]] =
    def page(offset: com.histmersion.Offset, acc: List[FollowupRow]): Either[com.histmersion.domain.library.acquisition.SemanticScholar.Problem, List[FollowupRow]] =
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
    page(com.histmersion.Offset(0), Nil)

  private def openalexCiting(work: WorkRef, since: Option[com.histmersion.Year], limit: com.histmersion.domain.library.acquisition.SemanticScholar.Limit): Either[com.histmersion.domain.library.acquisition.SemanticScholar.Problem, List[FollowupRow]] =
    work.openalexId match
      case None     => Right(Nil)
      case Some(id) =>
        val filter = List(Some(s"cites:$id"), since.map(y => s"from_publication_date:$y-01-01")).flatten.mkString(",")
        http
          .json(
            Url(
              cfg.openalex.url,
              com.histmersion.upstreams.http.Url.UrlPath("/works"),
              "filter" -> filter,
              "per-page" -> math.max(1, math.min(limit, 50)).toString,
              "sort" -> "cited_by_count:desc",
              "select" -> "id,doi,display_name,publication_year,authorships,cited_by_count,locations",
              "mailto" -> cfg.mailto
            )
          )
          .map(AcquireParse.openalexCitations(_).map(_.wire))

object SemanticScholar:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CitingResultPart2Part1 = CitingResultPart2Part1.T
  object CitingResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
