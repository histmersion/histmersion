package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{FollowupRow, WorkRef}
import com.histmersion.upstreams.Offset

final class SemanticScholar(cfg: AcquireConfig, http: Http) extends CitationsClient:

  private val Fields =
    "intents,isInfluential,contexts,citingPaper.title,citingPaper.year," +
      "citingPaper.externalIds,citingPaper.citationCount"

  private def headers =
    cfg.semanticScholar.key.map(k => CoreQuota.HeaderName("x-api-key") -> CoreQuota.HeaderValue(k.value)).toMap

  /** Semantic Scholar is the only source of citation INTENTS (`background|methodology|result`) and of the
    * `isInfluential` flag, which is what makes a follow-up worth reading. When it is down or rate limited we fall back
    * to OpenAlex's plain citation list — fewer signals, but never nothing.
    */
  def citing(
      work: WorkRef,
      since: Option[Year],
      limit: CitationsClient.CitingLimit
  ): Either[CitationsClient.Problem, (CitationsClient.Backend, List[FollowupRow])] =
    val semantic = CitationsClient.Backend("semantic-scholar")
    val openalex = CitationsClient.Backend("openalex")
    val found: Either[String, (CitationsClient.Backend, List[FollowupRow])] = work.doi match
      case Some(doi) =>
        s2(doi.value, since, limit.value) match
          case Right(rows) if rows.nonEmpty => Right((semantic, rows))
          case Right(_)                     => openalexCiting(work, since, limit.value).map((openalex, _))
          case Left(err)                    =>
            scribe.warn(s"acquire: Semantic Scholar unavailable ($err) — using OpenAlex citations")
            openalexCiting(work, since, limit.value).map((openalex, _))
      case None => openalexCiting(work, since, limit.value).map((openalex, _))
    found.left.map(CitationsClient.Problem(_))

  private def s2(doi: String, since: Option[Year], limit: Int): Either[String, List[FollowupRow]] =
    def page(offset: Offset, acc: List[FollowupRow]): Either[String, List[FollowupRow]] =
      if acc.size >= limit then Right(acc)
      else
        http
          .json(
            Url(
              Http.Url(cfg.semanticScholar.url.value),
              Url.UrlPath(s"/paper/DOI:$doi/citations"),
              "fields" -> Fields,
              "limit" -> math.min(100, limit - acc.size).toString,
              "offset" -> offset.value.toString
            ),
            headers
          )
          .left
          .map(_.value)
          .flatMap { j =>
            val rows = AcquireParse.s2Citations(j).map(_.wire)
            val kept = acc ++ rows.filter(r => since.forall(y => r.year.forall(_.value >= y.value)))
            AcquireParse.s2Next(j) match
              case Some(next) if rows.nonEmpty && next > offset => page(next, kept)
              case _                                            => Right(kept)
          }
    page(Offset(0), Nil)

  private def openalexCiting(work: WorkRef, since: Option[Year], limit: Int): Either[String, List[FollowupRow]] =
    work.openalexId match
      case None     => Right(Nil)
      case Some(id) =>
        val filter = List(Some(s"cites:$id"), since.map(y => s"from_publication_date:$y-01-01")).flatten.mkString(",")
        http
          .json(
            Url(
              Http.Url(cfg.openalex.url.value),
              Url.UrlPath("/works"),
              "filter" -> filter,
              "per-page" -> math.max(1, math.min(limit, 50)).toString,
              "sort" -> "cited_by_count:desc",
              "select" -> "id,doi,display_name,publication_year,authorships,cited_by_count,locations",
              "mailto" -> cfg.mailto.value
            )
          )
          .left
          .map(_.value)
          .map(AcquireParse.openalexCitations(_).map(_.wire))
