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

  def search(query: String, since: Option[Int], sort: Option[String], limit: Int): Either[String, List[WorkRef]] =
    // relevance keeps results ON TOPIC; sorting purely by citations surfaces mega-cited but tangential
    // work. `cited_by_count` comes back either way, so a caller can re-rank the relevant set.
    val order = if sort.map(_.trim.toLowerCase).contains("cited") then "cited_by_count:desc"
    else "relevance_score:desc"
    val from = since.getOrElse(cfg.defaults.minYear)
    http
      .json(
        Url(
          cfg.openalex.url,
          "/works",
          "search" -> query,
          "per-page" -> math.max(1, math.min(limit, 50)).toString,
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
      query: String,
      from: Int,
      sort: Option[String],
      limit: Int
  ): Either[String, List[WorkRef]] =
    val bySort = if sort.map(_.trim.toLowerCase).contains("cited") then
      List("sort" -> "is-referenced-by-count", "order" -> "desc")
    else Nil
    http
      .json(
        Url(
          cfg.crossref.url,
          "/works",
          (List(
            "query" -> query,
            "rows" -> math.max(1, math.min(limit, 50)).toString,
            "filter" -> s"from-pub-date:$from-01-01,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
            "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
            mailto
          ) ++ bySort)*
        )
      )
      .map(AcquireParse.crossrefWorks)

  /** OpenAlex first (richer, keyless), Crossref as the fallback — [[WorkLookup.meta]]. */
  def meta(doi: String): Either[String, WorkRef] = WorkLookup.meta(cfg, http.json(_), log)(doi)

  private def crossrefWork(doi: String): Either[String, Json] = http.json(WorkLookup.crossrefUrl(cfg, doi))

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: String): Either[String, (String, List[RefRow])] =
    crossrefWork(doi).map(AcquireParse.crossrefReferences) match
      case Right(refs) if refs.nonEmpty => Right(("crossref", refs))
      case _                            => openalexReferences(doi).map(("openalex", _))

  /** Fallback: resolve OpenAlex `referenced_works` ids to titles, 50 at a time. */
  private def openalexReferences(doi: String): Either[String, List[RefRow]] =
    http
      .json(Url(WorkLookup.workUrl(cfg, doi), "", "select" -> "referenced_works", mailto))
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
  def updates(doi: String): Either[String, List[UpdateNotice]] =
    crossrefWork(doi).map(AcquireParse.crossrefUpdates).orElse(Right(Nil))

  /** The raw OpenAlex record, for the citation counts an assessment reports and the open locations a fetcher reads. */
  def raw(doi: String): Either[String, Json] =
    http.json(Url(WorkLookup.workUrl(cfg, doi), "", "select" -> WorkLookup.WorkFields, mailto))
