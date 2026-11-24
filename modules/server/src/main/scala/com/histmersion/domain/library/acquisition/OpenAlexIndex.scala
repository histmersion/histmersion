package com.histmersion
package domain.library.acquisition
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.{Candidates, WorkRef}
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{LintRule, tolerated}

/** OpenAlex: search by publication date, `cites:` and `authorships.author.id:` filters. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class OpenAlexIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex with CitationAlerts:
  val name = ScholarlyIndex.Name("openalex")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def works(filter: String, limit: Int, search: Option[Search.Query]): Either[Problem, List[WorkRef]] =
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
      .map(AcquireParse.openalexWorks(_).map(_.wire))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    works(s"from_publication_date:$since,type:article|book|book-chapter", limit.value, Some(query))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def workUrl(doi: Doi): Http.Url = Http.Url(s"${cfg.openalex.url}/works/https://doi.org/$doi")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def work(doi: Doi): Either[Problem, Json] =
    http.json(Url(workUrl(doi), Url.UrlPath(""), "select" -> "id,authorships", "mailto" -> cfg.mailto.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citing(doi: Doi, since: LocalDate, limit: CitationAlerts.CitingLimit) =
    work(doi)
      .flatMap(j =>
        j.cursor
          .get[String]("id")
          .left
          .map(e =>
            Problem.Upstream.BadReply.here(
              Problem.Upstream.Name("openalex"),
              Problem.Upstream.Url(s"${workUrl(doi)}"),
              Problem.Upstream.Shape.Json,
              External.Reported.of(e)
            )
          )
      )
      .map(_.split("/").last)
      .flatMap(id => works(s"cites:$id,from_publication_date:$since", limit.value, None))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def authors(doi: Doi) =
    work(doi).map(j => Candidates.openalexAuthorIds(j).map(id => AuthorName(id.value)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def byAuthor(authorId: CitationAlerts.ByAuthorAuthorId, since: LocalDate, limit: CitationAlerts.ByAuthorLimit) =
    works(s"authorships.author.id:$authorId,from_publication_date:$since", limit.value, None)
