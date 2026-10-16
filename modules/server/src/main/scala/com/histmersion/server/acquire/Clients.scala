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

/** Resolve a legal open-access PDF: Unpaywall → CORE → OpenAlex OA locations → Europe PMC / PMC →
  * arXiv → repository landing pages resolved one step (Zenodo's API, an institutional repository's
  * `citation_pdf_url`; #1580) → PMC's JATS full text (when there is no PDF anywhere), and the unauthorised mirror
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
      .left.flatMap { why =>
        // OpenAlex pauses anonymous search under load (HTTP 503); Crossref's search is keyless too
        scribe.warn(s"acquire: OpenAlex search failed ($why) — searching Crossref instead")
        crossrefSearch(query, from, sort, limit).left.map(e => s"$why; Crossref: $e")
      }

  /** Crossref `works?query=`: the same kinds of work (articles, books, chapters) from `from` on,
    * by relevance or by citation count. Poorer metadata than OpenAlex (no OA location), but it answers. */
  private def crossrefSearch(query: String, from: Int, sort: Option[String], limit: Int)
      : Either[String, List[WorkRef]] =
    val bySort = if sort.map(_.trim.toLowerCase).contains("cited") then List("sort" -> "is-referenced-by-count", "order" -> "desc")
                 else Nil
    http.json(Url(cfg.crossref.url, "/works", (List(
      "query" -> query,
      "rows" -> math.max(1, math.min(limit, 50)).toString,
      "filter" -> s"from-pub-date:$from-01-01,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
      "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
      mailto) ++ bySort)*)).map(AcquireParse.crossrefWorks)

  /** OpenAlex first (richer, keyless), Crossref as the fallback — the order `discover.py meta` used. */
  def meta(doi: String): Either[String, WorkRef] =
    http.json(Url(workUrl(cfg, doi), "", "select" -> WorkFields, mailto))
      .map(AcquireParse.openalexWork)
      .left.flatMap { why =>
        // a miss (404) and a refusal (429/503) both land here; the reason says which
        scribe.info(s"acquire: OpenAlex did not answer for $doi ($why) — falling back to Crossref")
        crossrefWork(doi).map(AcquireParse.crossrefWork).map(withUnpaywall(doi, _))
      }

  /** Crossref records no open-access status at all, so a work that came back through the Crossref
    * fallback (OpenAlex down, rate-limited or missing the DOI) used to read "no OA copy known" even
    * for a gold-OA article (#599). Ask Unpaywall — where OpenAlex's own OA data comes from — and
    * leave the status unknown (`oaStatus = None`) when it cannot answer: Unpaywall refuses a request
    * without a contact e-mail. */
  private def withUnpaywall(doi: String, w: WorkRef): WorkRef =
    cfg.contact.flatMap(email => http.json(Url(cfg.unpaywall.url, s"/$doi", "email" -> email)).toOption)
      .map(AcquireParse.unpaywallAccess)
      .fold(w)(a => w.copy(isOa = a.isOa, oaStatus = a.status, oaUrl = a.url))

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

  private def tryUrl(source: String, url: String, headers: Map[String, String] = Map.empty): Option[FetchedFile] =
    http.bytes(url, headers) match
      case Right(b) if looksLikePdf(b) => Some(FetchedFile(source, Some(url), b, "pdf"))
      case Right(b) =>
        scribe.info(s"acquire: $source: $url returned ${b.length} bytes that are not a PDF — skipping")
        None
      case Left(e) =>
        scribe.info(s"acquire: $source fetch failed ($e)")
        None

  def fetch(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile] =
    // one Europe PMC search and one dataset record per fetch, shared by the PDF and the text steps;
    // one Unpaywall and one OpenAlex record, shared by the direct-PDF and the landing-page steps
    val pmc = doi.map(d => PmcLookup(d))
    val oa  = doi.map(d => OaRecords(d))
    val attempts: LazyList[Option[FetchedFile]] =
      LazyList.from(
        oa.toList.flatMap(r => List(() => unpaywall(r), () => core(r.doi), () => openalexOa(r))) ++
          pmc.toList.map(p => () => p.pdf) ++
          arxiv.toList.map(a => () => arxivPdf(a)) ++
          oa.toList.map(r => () => landing(r)) ++
          pmc.toList.map(p => () => p.fullText) ++
          doi.toList.map(d => () => unlicensed(d))
      ).map(_())
    attempts.collectFirst { case Some(f) => f }
      .toRight("no legal open-access PDF found")

  /** The OA indexes' records of one DOI, asked lazily and at most once per fetch. */
  private final class OaRecords(val doi: String):
    // Unpaywall answers 422 to a request without an e-mail, so without a contact it is not asked
    lazy val unpaywall: Option[Json] =
      cfg.contact.flatMap(email => http.json(Url(cfg.unpaywall.url, s"/$doi", "email" -> email)).toOption)
    lazy val openalex: Option[Json] = OaFetch.this.openalex.raw(doi).toOption
    /** Where DataCite says the DOI resolves — `None` for a DOI it does not register (a Crossref one). */
    lazy val datacite: Option[String] = dataciteUrl(doi)

  private def unpaywall(r: OaRecords): Option[FetchedFile] =
    r.unpaywall.toList.map(AcquireParse.unpaywall)
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

  private def openalexOa(r: OaRecords): Option[FetchedFile] =
    r.openalex.toList
      .flatMap(AcquireParse.openalexPdfUrls)
      .flatMap(u => tryUrl("openalex", u)).headOption

  // ── repository landing pages (#1580) ────────────────────────────────────────────────────────

  private def dataciteUrl(doi: String): Option[String] =
    http.json(Url(cfg.datacite.url, s"/dois/$doi")) match
      case Right(j) => RepositoryLanding.dataciteUrl(j)
      case Left(e) =>
        scribe.info(s"acquire: datacite: no record for $doi ($e)"); None

  /** The landing pages of the work, resolved ONE step each, at most `acquire.landing.max-pages`: the
    * Zenodo record of a Zenodo DOI, the OA indexes' repository and landing locations, then the page a
    * DataCite DOI resolves to. A `doi.org` link is never followed blind (it may land on a publisher):
    * a Zenodo DOI goes to Zenodo's API, any other is replaced by where DataCite says it resolves, and
    * a DOI DataCite does not know is skipped. */
  private def landing(r: OaRecords): Option[FetchedFile] =
    if !cfg.landing.enabled then None
    else
      val rules = cfg.landing.rules
      val doiUrl = s"https://doi.org/${r.doi}"
      val indexed = RepositoryLanding.distinct(
        RepositoryLanding.zenodoRecordId(r.doi).map(_ => RepositoryLanding.Candidate(doiUrl, true)).toList ++
          r.unpaywall.toList.flatMap(RepositoryLanding.unpaywallCandidates) ++
          r.openalex.toList.flatMap(RepositoryLanding.openalexCandidates))
      def viaDoi(c: RepositoryLanding.Candidate): LazyList[RepositoryLanding.Candidate] =
        val isDoi = RepositoryLanding.host(c.url).exists(h => h == "doi.org" || h == "dx.doi.org")
        if !isDoi || RepositoryLanding.zenodoRecordId(c.url).isDefined then LazyList(c)
        else
          val d = c.url.replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
          LazyList.from((if d.equalsIgnoreCase(r.doi) then r.datacite else dataciteUrl(d))
            .map(RepositoryLanding.Candidate(_, c.repository, c.publisher)))
      // the DOI's own DataCite landing page last, unless a location already named it
      val pages = (LazyList.from(indexed).flatMap(viaDoi) ++
        LazyList(()).flatMap(_ => LazyList.from(r.datacite.map(RepositoryLanding.Candidate(_, false)))))
        .scanLeft((Set.empty[String], Option.empty[RepositoryLanding.Candidate])) { case ((seen, _), c) =>
          if seen(c.url) then (seen, None) else (seen + c.url, Some(c))
        }.flatMap(_._2)
        // a publisher's page is never read, and does not use up the pages a work may read
        .filter(c => RepositoryLanding.zenodoRecordId(c.url).isDefined || !publisherPage(c, rules))
        .take(math.max(0, cfg.landing.maxPages))
      pages.map(resolvePage(_, rules)).collectFirst { case Some(f) => f }

  /** An OA index calls it a publisher's location, or its host is denied (`acquire.landing.deny-*`). */
  private def publisherPage(c: RepositoryLanding.Candidate, rules: RepositoryLanding.Rules): Boolean =
    val no = c.publisher || RepositoryLanding.host(c.url).exists(rules.denied)
    if no then scribe.info(s"acquire: landing: ${c.url} is a publisher's page — not read")
    no

  /** One landing page, one step: a Zenodo record by its API; anything else is read, and a repository's
    * page gives its `citation_pdf_url`s (a page that turns out to be the PDF is taken as it is). */
  private def resolvePage(c: RepositoryLanding.Candidate, rules: RepositoryLanding.Rules): Option[FetchedFile] =
    RepositoryLanding.zenodoRecordId(c.url) match
      case Some(id) => zenodo(id)
      case None if publisherPage(c, rules) => None
      case None =>
        http.bytes(c.url) match
          case Left(e) =>
            scribe.info(s"acquire: landing: ${c.url} failed ($e)"); None
          case Right(b) if looksLikePdf(b) => Some(FetchedFile("repository", Some(c.url), b, "pdf"))
          case Right(b) if !RepositoryLanding.looksLikeHtml(b) =>
            scribe.info(s"acquire: landing: ${c.url} is neither a PDF nor a page — skipping"); None
          case Right(b) =>
            val html = new String(b, "UTF-8")
            val pdfs = RepositoryLanding.pdfsOf(c.url, c.repository, html, rules)
            if pdfs.isEmpty then
              val why = if RepositoryLanding.isRepository(c.url, c.repository, html, rules) then "names no citation_pdf_url"
                        else "is not a repository's page"
              scribe.info(s"acquire: landing: ${c.url} $why — skipping")
            pdfs.iterator.map(u => tryUrl("repository", u)).collectFirst { case Some(f) => f }

  /** An open Zenodo record's PDF, by its REST API, checked against the record's MD5. */
  private def zenodo(id: String): Option[FetchedFile] =
    http.json(Url(cfg.zenodo.url, s"/api/records/$id")) match
      case Left(e) =>
        scribe.info(s"acquire: zenodo: record $id failed ($e)"); None
      case Right(j) =>
        val files = RepositoryLanding.zenodoFiles(j)
        if files.isEmpty then scribe.info(s"acquire: zenodo: record $id has no open PDF")
        files.iterator.map { f =>
          tryUrl("zenodo", f.url).filter { got =>
            val ok = f.md5.forall(m => OaFetch.md5(got.bytes).forall(_ == m))
            if !ok then scribe.warn(s"acquire: zenodo: ${f.url} does not match the MD5 its record gives — skipping")
            ok
          }
        }.collectFirst { case Some(f) => f }

  // ── Europe PMC and NCBI's PMC Article Datasets (#1523) ──────────────────────────────────────

  /** The PMC hosts get the project's public identity — no contact e-mail, no `email` parameter. */
  private def pmcHeaders = Map(Http.UserAgent -> cfg.publicUserAgent)

  /** Everything PMC can offer for one DOI, asked lazily and at most once.
    *
    * 1. Europe PMC's REST search names the PMCID and the PDF links it lists as free (`OA`/`F`); those
    *    are tried first (europepmc.org may answer with a bot-protection challenge, which is no copy).
    * 2. NCBI's PMC Article Datasets record for that PMCID names the PDF it publishes (the open-access
    *    subset and most author manuscripts), verified against the record's MD5.
    * 3. With no PDF anywhere, the record's JATS XML — the article's own full text — is the copy; the
    *    ingest job renders it to Markdown ([[com.histmersion.server.JatsConverter]]) instead of OCR. */
  private final class PmcLookup(doi: String):
    lazy val hit: Option[EuropePmcHit] =
      http.json(Url(cfg.europepmc.url, "/search",
        "query" -> s"""DOI:"$doi"""", "resultType" -> "core", "format" -> "json", "pageSize" -> "5"),
        pmcHeaders) match
        case Right(j) => AcquireParse.europepmcHit(j, doi)
        case Left(e) =>
          scribe.info(s"acquire: europepmc search failed ($e)")
          None

    lazy val record: Option[PmcRecord] =
      hit.flatMap(_.pmcid).flatMap { id =>
        http.text(Url(cfg.pmcDataset.url, "/", "list-type" -> "2", "prefix" -> s"metadata/$id."), pmcHeaders)
          .left.map(e => scribe.info(s"acquire: pmc dataset listing failed ($e)")).toOption
          .flatMap(AcquireParse.pmcLatestVersion(_, id))
          .flatMap(v => http.json(s"${cfg.pmcDataset.url}/metadata/$id.$v.json", pmcHeaders)
            .left.map(e => scribe.info(s"acquire: pmc dataset record failed ($e)")).toOption)
          .flatMap(AcquireParse.pmcRecord)
      }

    def pdf: Option[FetchedFile] =
      hit.toList.flatMap(_.pdfUrls).iterator.map(u => tryUrl("europepmc", u, pmcHeaders))
        .collectFirst { case Some(f) => f }
        .orElse(record.flatMap(_.pdf).flatMap(o => dataset(o, "pdf")(looksLikePdf)))

    def fullText: Option[FetchedFile] =
      record.flatMap(_.xml).flatMap(o => dataset(o, "xml")(b => Jats.isJats(new String(b, "UTF-8"))))

    private def dataset(o: PmcObject, ext: String)(ok: Array[Byte] => Boolean): Option[FetchedFile] =
      val url = s"${cfg.pmcDataset.url}/${o.key}"
      http.bytes(url, pmcHeaders) match
        case Left(e) =>
          scribe.info(s"acquire: pmc fetch failed ($e)"); None
        case Right(b) if !ok(b) =>
          scribe.info(s"acquire: pmc: $url returned ${b.length} bytes that are not a $ext — skipping"); None
        case Right(b) if o.md5.exists(m => OaFetch.md5(b).exists(_ != m)) =>
          scribe.warn(s"acquire: pmc: $url does not match the MD5 its record gives — skipping"); None
        case Right(b) => Some(FetchedFile("pmc", Some(url), b, ext))

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

object OaFetch:
  /** Hex MD5 of a download, to compare with the one a PMC dataset record states; `None` when the
    * runtime offers no MD5 (the check is then skipped, never failed). */
  def md5(b: Array[Byte]): Option[String] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b).map("%02x".format(_)).mkString).toOption

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
