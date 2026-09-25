package com.histmersion.server.acquire

import com.histmersion.core.*

import io.circe.Json

/** The upstream clients of the acquisition toolset, each a SMALL trait so tests can hand the
  * handlers a fake that answers from a captured fixture. The implementations below hold no state
  * beyond the config and an [[Http]]; all shaping of the responses happens in [[AcquireParse]],
  * which is pure and tested on its own.
  */

/** OpenAlex + Crossref: search, metadata, deposited reference lists and amendment notices. */
trait DiscoveryClient:
  def search(query: String, since: Option[Int], sort: Option[String], limit: Int): Either[String, List[WorkRef]]
  def meta(doi: String): Either[String, WorkRef]

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's
    * `referenced_works` resolved to titles when the publisher deposited none. */
  def references(doi: String): Either[String, (String, List[RefRow])]

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: String): Either[String, List[UpdateNotice]]

/** Papers CITING a work, with whatever intent signal the backend has. */
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(work: WorkRef, since: Option[Int], limit: Int): Either[String, (String, List[FollowupRow])]

/** A downloaded file plus where it came from. */
final case class FetchedFile(source: String, url: Option[String], bytes: Array[Byte], extension: String)

/** Resolve a legal open-access PDF: Unpaywall → CORE → OpenAlex OA locations → arXiv, and the unauthorised mirror
  * only when it is switched on in config. */
trait OaFetcher:
  def fetch(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile]

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

  /** The OpenAlex `select` list — asking for only these fields keeps a search page small. */
  val WorkFields =
    "id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location," +
      "locations,cited_by_count,is_retracted,type,abstract_inverted_index,referenced_works"

  /** OpenAlex resolves a DOI as a path segment: `/works/https://doi.org/10.1234/x`. */
  def workUrl(cfg: AcquireConfig, doi: String) =
    s"${cfg.openalex.url}/works/https://doi.org/$doi"

final class OpenAlexCrossref(cfg: AcquireConfig, http: Http) extends DiscoveryClient:
  import Clients.*

  private def mailto = "mailto" -> cfg.mailto

  def search(query: String, since: Option[Int], sort: Option[String], limit: Int)
      : Either[String, List[WorkRef]] =
    // relevance keeps results ON TOPIC; sorting purely by citations surfaces mega-cited but tangential
    // work. `cited_by_count` comes back either way, so a caller can re-rank the relevant set.
    val order = if sort.map(_.trim.toLowerCase).contains("cited") then "cited_by_count:desc"
                else "relevance_score:desc"
    val from  = since.getOrElse(cfg.defaults.minYear)
    http.json(Url(cfg.openalex.url, "/works",
      "search" -> query,
      "per-page" -> math.max(1, math.min(limit, 50)).toString,
      "sort" -> order,
      "filter" -> s"from_publication_date:$from-01-01,type:article|book|book-chapter",
      "select" -> WorkFields,
      mailto)).map(AcquireParse.openalexWorks)

  /** OpenAlex first (richer, keyless), Crossref as the fallback — the order `discover.py meta` used. */
  def meta(doi: String): Either[String, WorkRef] =
    http.json(Url(workUrl(cfg, doi), "", "select" -> WorkFields, mailto))
      .map(AcquireParse.openalexWork)
      .orElse {
        scribe.info(s"acquire: OpenAlex has no record of $doi — falling back to Crossref")
        crossrefWork(doi).map(AcquireParse.crossrefWork)
      }

  private def crossrefWork(doi: String): Either[String, Json] =
    http.json(Url(cfg.crossref.url, s"/works/$doi", mailto))

  def references(doi: String): Either[String, (String, List[RefRow])] =
    crossrefWork(doi).map(AcquireParse.crossrefReferences) match
      case Right(refs) if refs.nonEmpty => Right(("crossref", refs))
      case _                            => openalexReferences(doi).map(("openalex", _))

  /** Fallback: resolve OpenAlex `referenced_works` ids to titles, 50 at a time. */
  private def openalexReferences(doi: String): Either[String, List[RefRow]] =
    http.json(Url(workUrl(cfg, doi), "", "select" -> "referenced_works", mailto))
      .map(j => AcquireParse.openalexWork(j).referencedWorks.take(100))
      .map { ids =>
        ids.grouped(50).toList.flatMap { chunk =>
          http.json(Url(cfg.openalex.url, "/works",
            "filter" -> ("openalex_id:" + chunk.mkString("|")),
            "select" -> "doi,display_name,publication_year",
            "per-page" -> "50", mailto))
            .map(AcquireParse.openalexWorks).getOrElse(Nil)
            .map(w => RefRow(title = w.title, year = w.year, doi = w.doi))
        }
      }

  def updates(doi: String): Either[String, List[UpdateNotice]] =
    crossrefWork(doi).map(AcquireParse.crossrefUpdates).orElse(Right(Nil))

  /** The raw OpenAlex record, for the citation counts an assessment reports. */
  def raw(doi: String): Either[String, Json] =
    http.json(Url(workUrl(cfg, doi), "", "select" -> WorkFields, mailto))

final class SemanticScholar(cfg: AcquireConfig, http: Http, openalex: OpenAlexCrossref)
    extends CitationsClient:

  private val Fields =
    "intents,isInfluential,contexts,citingPaper.title,citingPaper.year," +
      "citingPaper.externalIds,citingPaper.citationCount"

  private def headers = cfg.semanticScholar.key.map("x-api-key" -> _).toMap

  /** Semantic Scholar is the only source of citation INTENTS (`background|methodology|result`) and of
    * the `isInfluential` flag, which is what makes a follow-up worth reading. When it is down or rate
    * limited we fall back to OpenAlex's plain citation list — fewer signals, but never nothing. */
  def citing(work: WorkRef, since: Option[Int], limit: Int): Either[String, (String, List[FollowupRow])] =
    work.doi match
      case Some(doi) =>
        s2(doi, since, limit) match
          case Right(rows) if rows.nonEmpty => Right(("semantic-scholar", rows))
          case Right(_)                     => openalexCiting(work, since, limit).map(("openalex", _))
          case Left(err) =>
            scribe.warn(s"acquire: Semantic Scholar unavailable ($err) — using OpenAlex citations")
            openalexCiting(work, since, limit).map(("openalex", _))
      case None => openalexCiting(work, since, limit).map(("openalex", _))

  private def s2(doi: String, since: Option[Int], limit: Int): Either[String, List[FollowupRow]] =
    def page(offset: Int, acc: List[FollowupRow]): Either[String, List[FollowupRow]] =
      if acc.size >= limit then Right(acc)
      else
        http.json(Url(cfg.semanticScholar.url, s"/paper/DOI:$doi/citations",
          "fields" -> Fields,
          "limit" -> math.min(100, limit - acc.size).toString,
          "offset" -> offset.toString), headers).flatMap { j =>
          val rows = AcquireParse.s2Citations(j)
          val kept = acc ++ rows.filter(r => since.forall(y => r.year.forall(_ >= y)))
          AcquireParse.s2Next(j) match
            case Some(next) if rows.nonEmpty && next > offset => page(next, kept)
            case _                                            => Right(kept)
        }
    page(0, Nil)

  private def openalexCiting(work: WorkRef, since: Option[Int], limit: Int)
      : Either[String, List[FollowupRow]] =
    work.openalexId match
      case None => Right(Nil)
      case Some(id) =>
        val filter = List(Some(s"cites:$id"), since.map(y => s"from_publication_date:$y-01-01"))
          .flatten.mkString(",")
        http.json(Url(cfg.openalex.url, "/works",
          "filter" -> filter,
          "per-page" -> math.max(1, math.min(limit, 50)).toString,
          "sort" -> "cited_by_count:desc",
          "select" -> "id,doi,display_name,publication_year,authorships,cited_by_count,locations",
          "mailto" -> cfg.mailto)).map(AcquireParse.openalexCitations)

/** Legal open access first, always. The unauthorised mirror exists in this class only because the workspace config can
  * switch it on; it is OFF by default, tried last, and every use is logged. */
final class OaFetch(cfg: AcquireConfig, http: Http, openalex: OpenAlexCrossref) extends OaFetcher:

  /** A PDF that is really a PDF. We cannot read page text without a PDF library, so this is the
    * cheap structural check `fetch_oa.py` did first: the magic bytes and a plausible size. Publisher
    * "you do not have access" interstitials are HTML and fail the magic-byte test. */
  private def looksLikePdf(b: Array[Byte]): Boolean =
    b.length > 1024 && new String(b.take(1024), "ISO-8859-1").contains("%PDF")

  private def tryUrl(source: String, url: String): Option[FetchedFile] =
    http.bytes(url) match
      case Right(b) if looksLikePdf(b) => Some(FetchedFile(source, Some(url), b, "pdf"))
      case Right(b) =>
        scribe.info(s"acquire: $url returned ${b.length} bytes that are not a PDF — skipping")
        None
      case Left(e) =>
        scribe.info(s"acquire: $source fetch failed ($e)")
        None

  def fetch(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile] =
    val attempts: LazyList[Option[FetchedFile]] =
      LazyList.from(
        doi.toList.flatMap(d => List(() => unpaywall(d), () => core(d), () => openalexOa(d))) ++
          arxiv.toList.map(a => () => arxivPdf(a)) ++
          doi.toList.map(d => () => unlicensed(d))
      ).map(_())
    attempts.collectFirst { case Some(f) => f }
      .toRight("no legal open-access PDF found")

  private def unpaywall(doi: String): Option[FetchedFile] =
    http.json(Url(cfg.unpaywall.url, s"/$doi", "email" -> cfg.mailto)).toOption
      .map(AcquireParse.unpaywall).toList
      .flatMap(_._1)
      .flatMap(u => tryUrl("unpaywall", u))
      .headOption

  private def core(doi: String): Option[FetchedFile] =
    cfg.core.key.flatMap { key =>
      http.json(Url(cfg.core.url, "/search/works", "q" -> s"""doi:"$doi"""", "limit" -> "1"),
        Map("Authorization" -> s"Bearer $key")).toOption
        .toList.flatMap(AcquireParse.coreDownloadUrls)
        .flatMap(u => tryUrl("core", u)).headOption
    }

  private def openalexOa(doi: String): Option[FetchedFile] =
    openalex.raw(doi).toOption.toList
      .flatMap(AcquireParse.openalexPdfUrls)
      .flatMap(u => tryUrl("openalex", u)).headOption

  private def arxivPdf(id: String): Option[FetchedFile] =
    tryUrl("arxiv", s"${cfg.arxiv.url}/pdf/$id")

  private val PdfHref = """(?i)(?:src|href)\s*=\s*["']([^"']+\.pdf[^"']*)["']""".r

  /** Last resort, OFF by default (`acquire.unlicensed.enabled = false`).
    *
    * The unauthorised mirror distributes copyrighted papers without publisher authorisation and its legality varies by
    * jurisdiction, so it is never reached unless a deployment deliberately enables it, and every use
    * is logged at WARN with the DOI. It is tried only after Unpaywall, CORE, OpenAlex and arXiv have
    * all failed.
    */
  private def unlicensed(doi: String): Option[FetchedFile] =
    if !cfg.unlicensed.enabled then None
    else
      scribe.warn(s"acquire: all legal OA sources failed for $doi — falling back to the unauthorised mirror " +
        s"(${cfg.unlicensed.mirror}); this distributes copyrighted work without authorisation and is " +
        "enabled only because acquire.unlicensed.enabled = true")
      http.text(s"${cfg.unlicensed.mirror}/$doi").toOption.flatMap { html =>
        PdfHref.findFirstMatchIn(html).map(_.group(1)).map { href =>
          if href.startsWith("//") then "https:" + href
          else if href.startsWith("http") then href
          else cfg.unlicensed.mirror + (if href.startsWith("/") then href else "/" + href)
        }.flatMap(u => tryUrl("unlicensed", u))
      }

final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  def refs(articleUrl: String): Either[String, WikirefsResult] =
    AcquireParse.wikiPageTitle(articleUrl)
      .toRight(s"'$articleUrl' is not a Wikipedia article URL")
      .flatMap { page =>
        http.json(Url(cfg.wikipedia.url, "",
          "action" -> "parse", "page" -> page, "prop" -> "wikitext",
          "format" -> "json", "redirects" -> "1")).flatMap { j =>
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
    http.json(Url(cfg.archiveOrg.url, "/advancedsearch.php",
      "q" -> s"($query) AND mediatype:texts",
      "fl[]" -> "identifier", "rows" -> rows.toString, "output" -> "json",
      "sort[]" -> "downloads desc")).map(AcquireParse.archiveSearch)
      // the search index returns only identifiers reliably; the item metadata (title, creator,
      // restriction) comes from /metadata, one call per hit
      .map(_.map(i => info(i.identifier).getOrElse(i)))

  def info(id: String): Either[String, ArchiveItem] =
    val ident = identifier(id)
    http.json(Url(cfg.archiveOrg.url, s"/metadata/$ident")).map(AcquireParse.archiveItem(ident, _))

  def download(id: String, file: String): Either[String, FetchedFile] =
    val ident = identifier(id)
    val url   = Url(cfg.archiveOrg.url, s"/download/$ident/" + java.net.URLEncoder.encode(file, "UTF-8")
      .replace("+", "%20"))
    http.bytes(url).flatMap { b =>
      if b.length < 10240 then Left(s"archive.org returned only ${b.length} bytes for $file")
      else Right(FetchedFile("archive_org", Some(url), b,
        file.toLowerCase.split("\\.").lastOption.getOrElse("pdf")))
    }

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[String, String] = http.text(cfg.retractionWatch.csvUrl)
