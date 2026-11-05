package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.{Candidates, WorkRef}

/** OpenAlex: search by publication date, `cites:` and `authorships.author.id:` filters. */
final class OpenAlexIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex with CitationAlerts:
  val name = "openalex"
  private def works(filter: com.histmersion.domain.library.acquisition.OpenAlexIndex.WorksFilter, limit: com.histmersion.domain.library.acquisition.OpenAlexIndex.WorksLimit, search: Option[com.histmersion.domain.library.acquisition.OpenAlexIndex.WorksSearch]): Either[com.histmersion.domain.library.acquisition.OpenAlexIndex.WorksResultPart1, List[WorkRef]] =
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

  def search(query: com.histmersion.domain.library.acquisition.OpenAlexIndex.SearchQuery, since: LocalDate, limit: com.histmersion.domain.library.acquisition.OpenAlexIndex.SearchLimit) =
    works(s"from_publication_date:$since,type:article|book|book-chapter", limit, Some(query))

  private def work(doi: com.histmersion.domain.library.Doi) =
    http.json(
      Url(s"${cfg.openalex.url}/works/https://doi.org/$doi", "", "select" -> "id,authorships", "mailto" -> cfg.mailto)
    )

  def citing(doi: com.histmersion.domain.library.Doi, since: LocalDate, limit: com.histmersion.domain.library.acquisition.OpenAlexIndex.CitingLimit) =
    work(doi)
      .flatMap(j => j.hcursor.get[String]("id").left.map(_.getMessage))
      .map(_.split("/").last)
      .flatMap(id => works(s"cites:$id,from_publication_date:$since", limit, None))

  def authors(doi: com.histmersion.domain.library.Doi) = work(doi).map(Candidates.openalexAuthorIds)

  def byAuthor(authorId: com.histmersion.domain.library.acquisition.OpenAlexIndex.ByAuthorAuthorId, since: LocalDate, limit: com.histmersion.domain.library.acquisition.OpenAlexIndex.ByAuthorLimit) =
    works(s"authorships.author.id:$authorId,from_publication_date:$since", limit, None)

object OpenAlexIndex:
  type WorksResultPart1 = WorksResultPart1.T
  object WorksResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorksFilter = WorksFilter.T
  object WorksFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorksLimit = WorksLimit.T
  object WorksLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorksSearch = WorksSearch.T
  object WorksSearch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorAuthorId = ByAuthorAuthorId.T
  object ByAuthorAuthorId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorLimit = ByAuthorLimit.T
  object ByAuthorLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
