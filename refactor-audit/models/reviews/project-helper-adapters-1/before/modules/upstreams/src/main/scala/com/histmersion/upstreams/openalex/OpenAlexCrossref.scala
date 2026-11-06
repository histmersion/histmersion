package com.histmersion.upstreams.openalex
import io.circe.Json
import com.histmersion.domain.library.acquisition.{RefRow, UpdateNotice, WorkLookup, WorkRef}
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.json
import com.histmersion.plugin.Plugin

/** OpenAlex and Crossref: search, a work's metadata, its deposited reference list, its amendment notices, and
  * OpenAlex's own record of it — over the host's HTTP, for the host's discovery and for the fetchers that read an
  * OpenAlex record.
  */
final class OpenAlexCrossref(cfg: UpstreamSettings, http: Plugin.Http, log: Plugin.Log):

  private def mailto = "mailto" -> cfg.mailto

  def search(query: com.histmersion.upstreams.openalex.OpenAlexCrossref.SearchQuery, since: Option[com.histmersion.upstreams.openalex.OpenAlexCrossref.SearchSince], sort: Option[com.histmersion.upstreams.openalex.OpenAlexCrossref.SearchSort], limit: com.histmersion.upstreams.openalex.OpenAlexCrossref.SearchLimit): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.SearchResultPart1, List[WorkRef]] =
    // relevance keeps results ON TOPIC; sorting purely by citations surfaces mega-cited but tangential
    // work. `cited_by_count` comes back either way, so a caller can re-rank the relevant set.
    val order = if sort.map((_).value.trim.toLowerCase).contains("cited") then "cited_by_count:desc"
    else "relevance_score:desc"
    val from = since.getOrElse(cfg.defaults.minYear)
    http
      .json(
        Url(
          cfg.openalex.url,
          _root_.com.histmersion.upstreams.http.Url.ApplyPath("/works"),
          "search" -> (query).value,
          "per-page" -> math.max(1, math.min((limit).value, 50)).toString,
          "sort" -> order,
          "filter" -> s"from_publication_date:$from-01-01,type:article|book|book-chapter",
          "select" -> WorkLookup.WorkFields,
          mailto
        )
      )
      .map(AcquireParse.openalexWorks)
      .left
      .flatMap { why =>
        // OpenAlex pauses anonymous search under load (HTTP 503); Crossref's search is keyless too
        log.warn(s"acquire: OpenAlex search failed ($why) — searching Crossref instead")
        crossrefSearch(query, from, sort, limit).left.map(e => s"$why; Crossref: $e")
      }

  /** Crossref `works?query=`: the same kinds of work (articles, books, chapters) from `from` on, by relevance or by
    * citation count. Poorer metadata than OpenAlex (no OA location), but it answers.
    */
  private def crossrefSearch(
      query: com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefSearchQuery,
      from: com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefSearchFrom,
      sort: Option[com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefSearchSort],
      limit: com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefSearchLimit
  ): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefSearchResultPart1, List[WorkRef]] =
    val bySort = if sort.map((_).value.trim.toLowerCase).contains("cited") then
      List("sort" -> "is-referenced-by-count", "order" -> "desc")
    else Nil
    http
      .json(
        Url(
          cfg.crossref.url,
          _root_.com.histmersion.upstreams.http.Url.ApplyPath("/works"),
          (List(
            "query" -> query,
            "rows" -> math.max(1, math.min((limit).value, 50)).toString,
            "filter" -> s"from-pub-date:$from-01-01,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
            "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
            mailto
          ) ++ bySort)*
        )
      )
      .map(AcquireParse.crossrefWorks)

  /** OpenAlex first (richer, keyless), Crossref as the fallback — [[WorkLookup.meta]]. */
  def meta(doi: com.histmersion.upstreams.openalex.OpenAlexCrossref.MetaDoi): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.MetaResultPart1, WorkRef] = WorkLookup.meta(cfg, http.json(_), log)(doi)

  private def crossrefWork(doi: com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefWorkDoi): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.CrossrefWorkResultPart1, Json] = http.json(WorkLookup.crossrefUrl(cfg, doi))

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: com.histmersion.upstreams.openalex.OpenAlexCrossref.ReferencesDoi): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.ReferencesResultPart1, (com.histmersion.upstreams.openalex.OpenAlexCrossref.ReferencesResultPart2Part1, List[RefRow])] =
    crossrefWork(doi).map(AcquireParse.crossrefReferences) match
      case Right(refs) if refs.nonEmpty => Right((_root_.com.histmersion.upstreams.openalex.OpenAlexCrossref.ReferencesResultPart2Part1("crossref"), refs))
      case _                            => openalexReferences(doi).map(("openalex", _))

  /** Fallback: resolve OpenAlex `referenced_works` ids to titles, 50 at a time. */
  private def openalexReferences(doi: com.histmersion.upstreams.openalex.OpenAlexCrossref.OpenalexReferencesDoi): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.OpenalexReferencesResultPart1, List[RefRow]] =
    http
      .json(Url(WorkLookup.workUrl(cfg, doi), _root_.com.histmersion.upstreams.http.Url.ApplyPath(""), "select" -> "referenced_works", mailto))
      .map(j => AcquireParse.openalexWork(j).referencedWorks.take(100))
      .map { ids =>
        ids.grouped(50).toList.flatMap { chunk =>
          http
            .json(
              Url(
                cfg.openalex.url,
                "/works",
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
  def updates(doi: com.histmersion.upstreams.openalex.OpenAlexCrossref.UpdatesDoi): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.UpdatesResultPart1, List[UpdateNotice]] =
    crossrefWork(doi).map(AcquireParse.crossrefUpdates).orElse(Right(Nil))

  /** The raw OpenAlex record, for the citation counts an assessment reports and the open locations a fetcher reads. */
  def raw(doi: com.histmersion.upstreams.openalex.OpenAlexCrossref.RawDoi): Either[com.histmersion.upstreams.openalex.OpenAlexCrossref.RawResultPart1, Json] =
    http.json(Url(WorkLookup.workUrl(cfg, doi), _root_.com.histmersion.upstreams.http.Url.ApplyPath(""), "select" -> WorkLookup.WorkFields, mailto))

object OpenAlexCrossref:
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchSince = SearchSince.T
  object SearchSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchSort = SearchSort.T
  object SearchSort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefSearchResultPart1 = CrossrefSearchResultPart1.T
  object CrossrefSearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefSearchQuery = CrossrefSearchQuery.T
  object CrossrefSearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefSearchFrom = CrossrefSearchFrom.T
  object CrossrefSearchFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefSearchSort = CrossrefSearchSort.T
  object CrossrefSearchSort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefSearchLimit = CrossrefSearchLimit.T
  object CrossrefSearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaResultPart1 = MetaResultPart1.T
  object MetaResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaDoi = MetaDoi.T
  object MetaDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefWorkResultPart1 = CrossrefWorkResultPart1.T
  object CrossrefWorkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefWorkDoi = CrossrefWorkDoi.T
  object CrossrefWorkDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesResultPart1 = ReferencesResultPart1.T
  object ReferencesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesResultPart2Part1 = ReferencesResultPart2Part1.T
  object ReferencesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesDoi = ReferencesDoi.T
  object ReferencesDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexReferencesResultPart1 = OpenalexReferencesResultPart1.T
  object OpenalexReferencesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexReferencesDoi = OpenalexReferencesDoi.T
  object OpenalexReferencesDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatesResultPart1 = UpdatesResultPart1.T
  object UpdatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatesDoi = UpdatesDoi.T
  object UpdatesDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RawResultPart1 = RawResultPart1.T
  object RawResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RawDoi = RawDoi.T
  object RawDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
