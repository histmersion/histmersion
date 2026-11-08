package com.histmersion
package upstreams.openalex
import com.histmersion.plugin.Problem
import io.circe.Json
import com.histmersion.domain.library.acquisition.{RefRow, UpdateNotice, WorkLookup, WorkRef}
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.json
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Year
import com.histmersion.upstreams.PageSize
import com.histmersion.plugin.Doi
import io.github.iltotore.iron.constraint.any.Pure

/** OpenAlex and Crossref: search, a work's metadata, its deposited reference list, its amendment notices, and
  * OpenAlex's own record of it — over the host's HTTP, for the host's discovery and for the fetchers that read an
  * OpenAlex record.
  */
final class OpenAlexCrossref(cfg: UpstreamSettings, http: Plugin.Http, log: Plugin.Log):

  private def mailto = "mailto" -> cfg.mailto.value

  private def getJson(url: RepositoryLanding.Candidate.Url): Either[Problem, Json] =
    http.json(url.value).left.map(Problem(_))

  def search(
      query: OpenAlexCrossref.SearchQuery,
      since: Option[Year],
      sort: Option[OpenAlexCrossref.SearchSort],
      limit: PageSize
  ): Either[Problem, List[WorkRef]] =
    // relevance keeps results ON TOPIC; sorting purely by citations surfaces mega-cited but tangential
    // work. `cited_by_count` comes back either way, so a caller can re-rank the relevant set.
    val order = if sort.map((_).value.trim.toLowerCase).contains("cited") then "cited_by_count:desc"
    else "relevance_score:desc"
    val from = since.getOrElse(Year(cfg.defaults.minYear.value))
    getJson(
      Url(
        RepositoryLanding.Candidate.Url(cfg.openalex.url.value),
        Url.UrlPath("/works"),
        "search" -> (query).value,
        "per-page" -> math.max(1, math.min((limit).value, 50)).toString,
        "sort" -> order,
        "filter" -> s"from_publication_date:$from-01-01,type:article|book|book-chapter",
        "select" -> WorkLookup.WorkFields.value,
        mailto
      )
    )
      .map(AcquireParse.openalexWorks)
      .left
      .flatMap { why =>
        // OpenAlex pauses anonymous search under load (HTTP 503); Crossref's search is keyless too
        log.warn(Plugin.Log.Line(s"acquire: OpenAlex search failed ($why) — searching Crossref instead"))
        crossrefSearch(query, from, sort, limit).left.map(e => Problem(s"$why; Crossref: $e"))
      }

  /** Crossref `works?query=`: the same kinds of work (articles, books, chapters) from `from` on, by relevance or by
    * citation count. Poorer metadata than OpenAlex (no OA location), but it answers.
    */
  private def crossrefSearch(
      query: OpenAlexCrossref.SearchQuery,
      from: Year,
      sort: Option[OpenAlexCrossref.SearchSort],
      limit: PageSize
  ): Either[Problem, List[WorkRef]] =
    val bySort = if sort.map((_).value.trim.toLowerCase).contains("cited") then
      List("sort" -> "is-referenced-by-count", "order" -> "desc")
    else Nil
    getJson(
      Url(
        RepositoryLanding.Candidate.Url(cfg.crossref.url.value),
        Url.UrlPath("/works"),
        (List(
          "query" -> query.value,
          "rows" -> math.max(1, math.min((limit).value, 50)).toString,
          "filter" -> s"from-pub-date:$from-01-01,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
          "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
          mailto
        ) ++ bySort)*
      )
    )
      .map(AcquireParse.crossrefWorks)

  /** OpenAlex first (richer, keyless), Crossref as the fallback — [[WorkLookup.meta]]. */
  def meta(doi: Doi): Either[Problem, WorkRef] = WorkLookup.meta(cfg, getJson(_), log)(doi)

  private def crossrefWork(doi: Doi): Either[Problem, Json] = getJson(WorkLookup.crossrefUrl(cfg, doi))

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: Doi): Either[Problem, (OpenAlexCrossref.ReferenceSource, List[RefRow])] =
    crossrefWork(doi).map(AcquireParse.crossrefReferences) match
      case Right(refs) if refs.nonEmpty => Right((OpenAlexCrossref.ReferenceSource("crossref"), refs))
      case _ => openalexReferences(doi).map((OpenAlexCrossref.ReferenceSource("openalex"), _))

  /** Fallback: resolve OpenAlex `referenced_works` ids to titles, 50 at a time. */
  private def openalexReferences(doi: Doi): Either[Problem, List[RefRow]] =
    getJson(Url(WorkLookup.workUrl(cfg, doi), Url.UrlPath(""), "select" -> "referenced_works", mailto))
      .map(j => AcquireParse.openalexWork(j).referencedWorks.take(100))
      .map { ids =>
        ids.grouped(50).toList.flatMap { chunk =>
          getJson(
            Url(
              RepositoryLanding.Candidate.Url(cfg.openalex.url.value),
              Url.UrlPath("/works"),
              "filter" -> ("openalex_id:" + chunk.mkString("|")),
              "select" -> "doi,display_name,publication_year",
              "per-page" -> "50",
              mailto
            )
          )
            .map(AcquireParse.openalexWorks)
            .getOrElse(Nil)
            .map(w => RefRow(title = w.title, year = w.year, doi = w.doi))
        }
      }

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: Doi): Either[Problem, List[UpdateNotice]] =
    crossrefWork(doi).map(AcquireParse.crossrefUpdates).orElse(Right(Nil))

  /** The raw OpenAlex record, for the citation counts an assessment reports and the open locations a fetcher reads. */
  def raw(doi: Doi): Either[Problem, Json] =
    getJson(Url(WorkLookup.workUrl(cfg, doi), Url.UrlPath(""), "select" -> WorkLookup.WorkFields.value, mailto))

object OpenAlexCrossref:
  type ReferenceSource = ReferenceSource.T
  object ReferenceSource extends Newtype[String, Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends Newtype[String, Pure]
  type SearchSort = SearchSort.T
  object SearchSort extends Newtype[String, Pure]
