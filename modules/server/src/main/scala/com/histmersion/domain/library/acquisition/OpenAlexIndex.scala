package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.Problem
import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.{Candidates, WorkRef}
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.Doi

/** OpenAlex: search by publication date, `cites:` and `authorships.author.id:` filters. */
final class OpenAlexIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex with CitationAlerts:
  val name = ScholarlyIndex.Name("openalex")
  private def works(filter: String, limit: Int, search: Option[Search.Query]): Either[String, List[WorkRef]] =
    http
      .json(
        Url(
          Http.Url(cfg.openalex.url.value),
          Url.UrlPath("/works"),
          (search.map(q => "search" -> q.value).toList ++ List(
            "filter" -> filter,
            "per-page" -> math.max(1, math.min(limit, 50)).toString,
            "sort" -> "publication_date:desc",
            "select" -> ("id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location,locations," +
              "cited_by_count,is_retracted,type,abstract_inverted_index"),
            "mailto" -> cfg.mailto.value
          ))*
        )
      )
      .left
      .map(_.problem.value)
      .map(AcquireParse.openalexWorks(_).map(_.wire))

  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    works(s"from_publication_date:$since,type:article|book|book-chapter", limit.value, Some(query)).left
      .map(domain.Problem(_))

  private def work(doi: Doi): Either[String, io.circe.Json] =
    http
      .json(
        Url(
          Http.Url(s"${cfg.openalex.url}/works/https://doi.org/$doi"),
          Url.UrlPath(""),
          "select" -> "id,authorships",
          "mailto" -> cfg.mailto.value
        )
      )
      .left
      .map(_.problem.value)

  def citing(doi: Doi, since: LocalDate, limit: CitationAlerts.CitingLimit) =
    work(doi)
      .flatMap(j => j.hcursor.get[String]("id").left.map(_.getMessage))
      .map(_.split("/").last)
      .flatMap(id => works(s"cites:$id,from_publication_date:$since", limit.value, None))
      .left
      .map(domain.Problem(_))

  def authors(doi: Doi) =
    work(doi)
      .map(j => Candidates.openalexAuthorIds(j).map(id => AuthorName(id.value)))
      .left
      .map(domain.Problem(_))

  def byAuthor(authorId: CitationAlerts.ByAuthorAuthorId, since: LocalDate, limit: CitationAlerts.ByAuthorLimit) =
    works(s"authorships.author.id:$authorId,from_publication_date:$since", limit.value, None).left
      .map(domain.Problem(_))
