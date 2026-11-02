package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** OpenAlex: search by publication date, `cites:` and `authorships.author.id:` filters. */
final class OpenAlexIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex with CitationAlerts:
  val name = "openalex"
  private def works(filter: String, limit: Int, search: Option[String]): Either[String, List[WorkRef]] =
    http
      .json(
        Url(
          cfg.openalex.url,
          "/works",
          (search.map("search" -> _).toList ++ List(
            "filter" -> filter,
            "per-page" -> math.max(1, math.min(limit, 50)).toString,
            "sort" -> "publication_date:desc",
            "select" -> ("id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location,locations," +
              "cited_by_count,is_retracted,type,abstract_inverted_index"),
            "mailto" -> cfg.mailto
          ))*
        )
      )
      .map(AcquireParse.openalexWorks(_).map(_.wire))

  def search(query: String, since: LocalDate, limit: Int) =
    works(s"from_publication_date:$since,type:article|book|book-chapter", limit, Some(query))

  private def work(doi: String) =
    http.json(
      Url(s"${cfg.openalex.url}/works/https://doi.org/$doi", "", "select" -> "id,authorships", "mailto" -> cfg.mailto)
    )

  def citing(doi: String, since: LocalDate, limit: Int) =
    work(doi)
      .flatMap(j => j.hcursor.get[String]("id").left.map(_.getMessage))
      .map(_.split("/").last)
      .flatMap(id => works(s"cites:$id,from_publication_date:$since", limit, None))

  def authors(doi: String) = work(doi).map(Candidates.openalexAuthorIds)

  def byAuthor(authorId: String, since: LocalDate, limit: Int) =
    works(s"authorships.author.id:$authorId,from_publication_date:$since", limit, None)
