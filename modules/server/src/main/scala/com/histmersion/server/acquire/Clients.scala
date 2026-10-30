package com.histmersion.server.acquire

import com.histmersion.pluginhost.{AcquireParse, FetchChain, OaFetcher, PluginHost, Url, WorkLookup}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import io.circe.Json

/** The upstream clients of the acquisition toolset, each a SMALL trait so tests can hand the handlers a fake that
  * answers from a captured fixture. The implementations below hold no state beyond the config and an [[Http]]; all
  * shaping of the responses happens in [[AcquireParse]], which is pure and tested on its own.
  */

/** OpenAlex + Crossref: search, metadata, deposited reference lists and amendment notices. */
trait DiscoveryClient:
  def search(query: String, since: Option[Int], sort: Option[String], limit: Int): Either[String, List[WorkRef]]
  def meta(doi: String): Either[String, WorkRef]

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: String): Either[String, (String, List[RefRow])]

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: String): Either[String, List[UpdateNotice]]

/** Papers CITING a work, with whatever intent signal the backend has. */
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(work: WorkRef, since: Option[Int], limit: Int): Either[String, (String, List[FollowupRow])]

/** A downloaded file plus where it came from — the plugin SPI's type, so a fetcher plugin's copy is the one ingest
  * stores.
  */
type FetchedFile = com.histmersion.plugin.FetchedFile
val FetchedFile = com.histmersion.plugin.FetchedFile

trait WikiClient:
  def refs(articleUrl: String): Either[String, WikirefsResult]

trait ArchiveClient:
  def search(query: String, rows: Int): Either[String, List[ArchiveItem]]
  def info(identifier: String): Either[String, ArchiveItem]
  def download(identifier: String, file: String): Either[String, FetchedFile]

trait RetractionWatchClient:
  def csv(): Either[String, String]

// ── implementations ───────────────────────────────────────────────────────────────────────────

private object Clients:

  val WorkFields: String = WorkLookup.WorkFields

  def workUrl(cfg: AcquireConfig, doi: String): String = WorkLookup.workUrl(cfg, doi)

final class OpenAlexCrossref(cfg: AcquireConfig, http: Http) extends DiscoveryClient:
  import Clients.*

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
          "select" -> WorkFields,
          mailto
        )
      )
      .map(AcquireParse.openalexWorks)
      .left
      .flatMap { why =>
        // OpenAlex pauses anonymous search under load (HTTP 503); Crossref's search is keyless too
        scribe.warn(s"acquire: OpenAlex search failed ($why) — searching Crossref instead")
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
  def meta(doi: String): Either[String, WorkRef] = WorkLookup.meta(cfg, http.json(_))(doi)

  private def crossrefWork(doi: String): Either[String, Json] = http.json(WorkLookup.crossrefUrl(cfg, doi))

  def references(doi: String): Either[String, (String, List[RefRow])] =
    crossrefWork(doi).map(AcquireParse.crossrefReferences) match
      case Right(refs) if refs.nonEmpty => Right(("crossref", refs))
      case _                            => openalexReferences(doi).map(("openalex", _))

  /** Fallback: resolve OpenAlex `referenced_works` ids to titles, 50 at a time. */
  private def openalexReferences(doi: String): Either[String, List[RefRow]] =
    http
      .json(Url(workUrl(cfg, doi), "", "select" -> "referenced_works", mailto))
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

  def updates(doi: String): Either[String, List[UpdateNotice]] =
    crossrefWork(doi).map(AcquireParse.crossrefUpdates).orElse(Right(Nil))

  /** The raw OpenAlex record, for the citation counts an assessment reports. */
  def raw(doi: String): Either[String, Json] =
    http.json(Url(workUrl(cfg, doi), "", "select" -> WorkFields, mailto))

final class SemanticScholar(cfg: AcquireConfig, http: Http, openalex: OpenAlexCrossref) extends CitationsClient:

  private val Fields =
    "intents,isInfluential,contexts,citingPaper.title,citingPaper.year," +
      "citingPaper.externalIds,citingPaper.citationCount"

  private def headers = cfg.semanticScholar.key.map("x-api-key" -> _).toMap

  /** Semantic Scholar is the only source of citation INTENTS (`background|methodology|result`) and of the
    * `isInfluential` flag, which is what makes a follow-up worth reading. When it is down or rate limited we fall back
    * to OpenAlex's plain citation list — fewer signals, but never nothing.
    */
  def citing(work: WorkRef, since: Option[Int], limit: Int): Either[String, (String, List[FollowupRow])] =
    work.doi match
      case Some(doi) =>
        s2(doi, since, limit) match
          case Right(rows) if rows.nonEmpty => Right(("semantic-scholar", rows))
          case Right(_)                     => openalexCiting(work, since, limit).map(("openalex", _))
          case Left(err)                    =>
            scribe.warn(s"acquire: Semantic Scholar unavailable ($err) — using OpenAlex citations")
            openalexCiting(work, since, limit).map(("openalex", _))
      case None => openalexCiting(work, since, limit).map(("openalex", _))

  private def s2(doi: String, since: Option[Int], limit: Int): Either[String, List[FollowupRow]] =
    def page(offset: Int, acc: List[FollowupRow]): Either[String, List[FollowupRow]] =
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
            val rows = AcquireParse.s2Citations(j)
            val kept = acc ++ rows.filter(r => since.forall(y => r.year.forall(_ >= y)))
            AcquireParse.s2Next(j) match
              case Some(next) if rows.nonEmpty && next > offset => page(next, kept)
              case _                                            => Right(kept)
          }
    page(0, Nil)

  private def openalexCiting(work: WorkRef, since: Option[Int], limit: Int): Either[String, List[FollowupRow]] =
    work.openalexId match
      case None     => Right(Nil)
      case Some(id) =>
        val filter = List(Some(s"cites:$id"), since.map(y => s"from_publication_date:$y-01-01")).flatten.mkString(",")
        http
          .json(
            Url(
              cfg.openalex.url,
              "/works",
              "filter" -> filter,
              "per-page" -> math.max(1, math.min(limit, 50)).toString,
              "sort" -> "cited_by_count:desc",
              "select" -> "id,doi,display_name,publication_year,authorships,cited_by_count,locations",
              "mailto" -> cfg.mailto
            )
          )
          .map(AcquireParse.openalexCitations)

/** The acquisition chain over the BUILT-IN fetchers (archive.org, Unpaywall, CORE, OpenAlex, PMC, arXiv, landing pages,
  * PMC's JATS text) — registered through the plugin host exactly as the server registers them, every one enabled, in
  * `acquire.chain` order. What a test or a one-off caller runs; the server runs the chain of its own plugin generation,
  * which may hold more fetchers or fewer.
  */
object OaFetch:
  def apply(cfg: AcquireConfig, http: Http, openalex: OpenAlexCrossref): FetchChain =
    val generation = PluginHost.build(
      fetchers.BuiltinFetchers.plugins(cfg, () => http, () => openalex, () => ArchiveOrg(cfg, http)),
      org.ekrich.config.ConfigFactory.empty,
      org.ekrich.config.ConfigFactory.empty,
      com.histmersion.server.plugins.Plugins.services(() => HttpPlugin(http), org.ekrich.config.ConfigFactory.empty)
    )
    FetchChain.of(generation.registries.sourceFetchers, cfg.chainIds, cfg.allowUnauthorised)._1

  /** Hex MD5 of a download, to compare with the one a PMC dataset record or a Zenodo record states; `None` when the
    * runtime offers no MD5 (the check is then skipped, never failed).
    */
  def md5(b: Array[Byte]): Option[String] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b).map("%02x".format(_)).mkString).toOption

/** The host's [[Http]] as the plugin SPI's client: plugins get bytes, and parse what they asked for. */
final class HttpPlugin(http: Http) extends com.histmersion.plugin.PluginHttp:
  def get(url: String, headers: Map[String, String]) = http.bytes(url, headers)

final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  def refs(articleUrl: String): Either[String, WikirefsResult] =
    AcquireParse
      .wikiPageTitle(articleUrl)
      .toRight(s"'$articleUrl' is not a Wikipedia article URL")
      .flatMap { page =>
        http
          .json(
            Url(
              cfg.wikipedia.url,
              "",
              "action" -> "parse",
              "page" -> page,
              "prop" -> "wikitext",
              "format" -> "json",
              "redirects" -> "1"
            )
          )
          .flatMap { j =>
            AcquireParse.wikiText(j).toRight(s"Wikipedia returned no wikitext for '$page'").map { text =>
              val (dois, arxiv) = AcquireParse.wikiRefs(text)
              WikirefsResult(AcquireParse.wikiTitle(j).getOrElse(page), articleUrl, dois.size, dois, arxiv)
            }
          }
      }

final class ArchiveOrg(cfg: AcquireConfig, http: Http) extends ArchiveClient:

  /** `https://archive.org/details/<id>` or a bare identifier. */
  private def identifier(s: String): String =
    val t = s.trim
    (if t.contains("/details/") then t.split("/details/").last else t).split("[/?#]").head

  def search(query: String, rows: Int): Either[String, List[ArchiveItem]] =
    http
      .json(
        Url(
          cfg.archiveOrg.url,
          "/advancedsearch.php",
          "q" -> s"($query) AND mediatype:texts",
          "fl[]" -> "identifier",
          "rows" -> rows.toString,
          "output" -> "json",
          "sort[]" -> "downloads desc"
        )
      )
      .map(AcquireParse.archiveSearch)
      // the search index returns only identifiers reliably; the item metadata (title, creator,
      // restriction) comes from /metadata, one call per hit
      .map(_.map(i => info(i.identifier).getOrElse(i)))

  def info(id: String): Either[String, ArchiveItem] =
    val ident = identifier(id)
    http.json(Url(cfg.archiveOrg.url, s"/metadata/$ident")).map(AcquireParse.archiveItem(ident, _))

  def download(id: String, file: String): Either[String, FetchedFile] =
    val ident = identifier(id)
    val url = Url(
      cfg.archiveOrg.url,
      s"/download/$ident/" + java.net.URLEncoder
        .encode(file, "UTF-8")
        .replace("+", "%20")
    )
    http.bytes(url).flatMap { b =>
      if b.length < 10240 then Left(s"archive.org returned only ${b.length} bytes for $file")
      else Right(FetchedFile("archive_org", Some(url), b, file.toLowerCase.split("\\.").lastOption.getOrElse("pdf")))
    }

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[String, String] = http.text(cfg.retractionWatch.csvUrl)

/** The acquisition clients of one config and one plugin generation (the server's `acquisition` hot node): rebuilt
  * together when `acquire.*` or the plugins change.
  */
final case class AcquireClients(
    discovery: DiscoveryClient,
    citations: CitationsClient,
    oa: OaFetcher,
    wiki: WikiClient,
    archive: ArchiveClient,
    retractionWatch: RetractionWatchClient,
    /** The indexes a goal's watches read, and the citation/author alerts (#3761). */
    indexes: List[ScholarlyIndex] = Nil,
    alerts: CitationAlerts = NoAlerts,
    /** The legal chain's generation ([[FetcherGeneration]]): what makes the acquisition list due again. */
    generation: String = ""
)

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  private val off = Left("the citation alerts are not connected")
  def citing(doi: String, since: java.time.LocalDate, limit: Int) = off
  def authors(doi: String) = off
  def byAuthor(authorId: String, since: java.time.LocalDate, limit: Int) = off
