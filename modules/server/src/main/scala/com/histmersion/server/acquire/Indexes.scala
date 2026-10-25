package com.histmersion.server.acquire

import com.histmersion.pluginhost.{AcquireParse, Url}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** One scholarly index a goal's saved queries are read from (#3761): the works matching a query that are
  * newer than `since` (published, or deposited where the index says so), newest first. A small trait so a
  * test hands fakes. */
trait ScholarlyIndex:
  def name: String
  def search(query: String, since: LocalDate, limit: Int): Either[String, List[WorkRef]]

/** The forward-citation and same-author alerts of a goal's sources (OpenAlex): the works citing one, a
  * work's authors, and an author's newer works. */
trait CitationAlerts:
  def citing(doi: String, since: LocalDate, limit: Int): Either[String, List[WorkRef]]
  def authors(doi: String): Either[String, List[String]]
  def byAuthor(authorId: String, since: LocalDate, limit: Int): Either[String, List[WorkRef]]

/** OpenAlex: search by publication date, `cites:` and `authorships.author.id:` filters. */
final class OpenAlexIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex with CitationAlerts:
  val name = "openalex"
  private def works(filter: String, limit: Int, search: Option[String]): Either[String, List[WorkRef]] =
    http.json(Url(cfg.openalex.url, "/works", (search.map("search" -> _).toList ++ List(
      "filter" -> filter, "per-page" -> math.max(1, math.min(limit, 50)).toString, "sort" -> "publication_date:desc",
      "select" -> ("id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location,locations," +
        "cited_by_count,is_retracted,type,abstract_inverted_index"),
      "mailto" -> cfg.mailto))*)).map(AcquireParse.openalexWorks)

  def search(query: String, since: LocalDate, limit: Int) =
    works(s"from_publication_date:$since,type:article|book|book-chapter", limit, Some(query))

  private def work(doi: String) =
    http.json(Url(s"${cfg.openalex.url}/works/https://doi.org/$doi", "", "select" -> "id,authorships", "mailto" -> cfg.mailto))

  def citing(doi: String, since: LocalDate, limit: Int) =
    work(doi).flatMap(j => j.hcursor.get[String]("id").left.map(_.getMessage)).map(_.split("/").last)
      .flatMap(id => works(s"cites:$id,from_publication_date:$since", limit, None))

  def authors(doi: String) = work(doi).map(Candidates.openalexAuthorIds)

  def byAuthor(authorId: String, since: LocalDate, limit: Int) =
    works(s"authorships.author.id:$authorId,from_publication_date:$since", limit, None)

/** Crossref: what was DEPOSITED since (`from-created-date`), newest first — a watermark on deposits. */
final class CrossrefIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "crossref"
  def search(query: String, since: LocalDate, limit: Int) =
    http.json(Url(cfg.crossref.url, "/works",
      "query" -> query, "rows" -> math.max(1, math.min(limit, 50)).toString, "sort" -> "created", "order" -> "desc",
      "filter" -> s"from-created-date:$since,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
      "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
      "mailto" -> cfg.mailto)).map(AcquireParse.crossrefWorks)

/** Europe PMC: first publication since, newest first. */
final class EuropePmcIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "europepmc"
  def search(query: String, since: LocalDate, limit: Int) =
    http.json(Url(cfg.europepmc.url, "/search",
      "query" -> s"($query) AND FIRST_PDATE:[$since TO 3000-12-31]", "format" -> "json", "resultType" -> "core",
      "pageSize" -> math.max(1, math.min(limit, 100)).toString, "sort" -> "FIRST_PDATE desc")).map(Candidates.europepmcWorks)

/** arXiv: newest submissions first; the Atom feed has no date filter, so what is older than `since`'s
  * year is dropped here (the stored candidates keep a work found twice from counting as new). */
final class ArxivIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "arxiv"
  def search(query: String, since: LocalDate, limit: Int) =
    http.text(Url(cfg.candidates.arxivApiUrl, "",
      "search_query" -> s"all:\"$query\"", "sortBy" -> "submittedDate", "sortOrder" -> "descending",
      "max_results" -> math.max(1, math.min(limit, 100)).toString))
      .map(Candidates.arxivWorks(_).filter(_.year.forall(_ >= since.getYear)))

/** CORE: only with `acquire.core.api-key` (its search refuses anonymous use). */
final class CoreIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "core"
  def search(query: String, since: LocalDate, limit: Int) =
    cfg.core.key match
      case None => Left("no acquire.core.api-key: CORE's search needs one")
      case Some(k) =>
        http.json(Url(cfg.core.url, "/search/works", "q" -> s"($query) AND yearPublished>=${since.getYear}",
          "limit" -> math.max(1, math.min(limit, 100)).toString), Map("Authorization" -> s"Bearer $k")).map(Candidates.coreWorks)

object Indexes:
  /** The indexes `acquire.candidates.indexes` names, in its order (an unknown name is left out). */
  def of(cfg: AcquireConfig, http: Http): (List[ScholarlyIndex], CitationAlerts) =
    val openalex = OpenAlexIndex(cfg, http)
    val all: Map[String, ScholarlyIndex] = Map("openalex" -> openalex, "crossref" -> CrossrefIndex(cfg, http),
      "europepmc" -> EuropePmcIndex(cfg, http), "arxiv" -> ArxivIndex(cfg, http), "core" -> CoreIndex(cfg, http))
    // CORE only with its key (its search refuses anonymous use): without one it is not asked at all
    (cfg.candidates.indexes.map(_.trim.toLowerCase).distinct.filter(n => n != "core" || cfg.core.key.isDefined).flatMap(all.get), openalex)
