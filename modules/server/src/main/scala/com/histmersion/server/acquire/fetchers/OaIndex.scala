package com.histmersion.server.acquire.fetchers

import com.histmersion.core.{AcquireConfig, AcquireParse, RepositoryLanding}
import com.histmersion.plugin.{Fetch, FetchContext, SourceFetcher, WorkKey}
import com.histmersion.server.acquire.{FetchedFile, Http, OaFetch, OpenAlexCrossref, Url}

import io.circe.Json

/** The fetchers of the four open-access index plugins (D4), all by DOI: Unpaywall's best OA location
  * (`oa-unpaywall`), CORE's download URLs with a key (`oa-core`), OpenAlex's OA locations (`oa-openalex`),
  * and the repository landing pages those indexes and DataCite name (`oa-landing`, #1580). One instance
  * per plugin generation, shared by the four plugins: the Unpaywall and OpenAlex records are asked once
  * per work and shared by the direct steps and the landing step, through the fetch's memo (keys
  * `oa:<what>:<doi>` — no one plugin owns them). */
final class OaIndex(cfg: AcquireConfig, http: () => Http, openalexClient: () => OpenAlexCrossref):
  import Fetching.*

  /** The OA indexes' records of one DOI, asked lazily and at most once per fetch. */
  private final class OaRecords(val doi: String):
    lazy val openalex: Option[Json] = openalexClient().raw(doi).toOption
    /** Where DataCite says the DOI resolves — `None` for a DOI it does not register (a Crossref one). */
    lazy val datacite: Option[String] = dataciteUrl(doi)

  private def records(ctx: FetchContext, doi: String): OaRecords =
    ctx.memo(s"oa:records:$doi")(OaRecords(doi))

  /** Unpaywall's record of `doi`, asked with `email` at most once per fetch. Unpaywall answers 422 to a
    * request without an e-mail, so without one it is not asked. The `unpaywall` step and the landing
    * step share it when they use the same contact (the default: both Histmersion's `acquire.contact-email`). */
  private def unpaywallRecord(ctx: FetchContext, doi: String, email: Option[String]): Option[Json] =
    email.flatMap(e => ctx.memo(s"oa:unpaywall:$e:$doi")(http().json(Url(cfg.unpaywall.url, s"/$doi", "email" -> e)).toOption))

  private def byDoi(step: String)(f: (FetchContext, String) => Option[FetchedFile]): SourceFetcher =
    open((work: WorkKey, ctx: FetchContext) =>
      work.doi.fold[Fetch](Fetch.Miss(s"$step needs a DOI"))(d => answer(f(ctx, d), s"$step has no copy of $d")))

  /** `unpaywall`, asking with `email` (the plugin's `contact-email`, else Histmersion's). */
  def unpaywall(email: Option[String]): SourceFetcher =
    byDoi("unpaywall")((ctx, d) => unpaywallPdf(unpaywallRecord(ctx, d, email)))
  /** `core`, with CORE's API key; without one it is never asked (a miss). */
  def core(key: Option[String]): SourceFetcher = byDoi("core")((_, d) => corePdf(d, key))
  val openalex: SourceFetcher = byDoi("openalex")((ctx, d) => openalexOa(records(ctx, d)))
  val landing: SourceFetcher  = byDoi("landing")((ctx, d) => landingPdf(records(ctx, d), unpaywallRecord(ctx, d, cfg.contact)))

  private def unpaywallPdf(record: Option[Json]): Option[FetchedFile] =
    record.toList.map(AcquireParse.unpaywall)
      .flatMap(_._1)
      .flatMap(u => tryUrl(http(), "unpaywall", u))
      .headOption

  private def corePdf(doi: String, key: Option[String]): Option[FetchedFile] =
    key.flatMap { key =>
      http().json(Url(cfg.core.url, "/search/works", "q" -> s"""doi:"$doi"""", "limit" -> "1"),
        Map("Authorization" -> s"Bearer $key")).toOption
        .toList.flatMap(AcquireParse.coreDownloadUrls)
        .flatMap(u => tryUrl(http(), "core", u)).headOption
    }

  private def openalexOa(r: OaRecords): Option[FetchedFile] =
    r.openalex.toList
      .flatMap(AcquireParse.openalexPdfUrls)
      .flatMap(u => tryUrl(http(), "openalex", u)).headOption

  // ── repository landing pages (#1580) ────────────────────────────────────────────────────────

  private def dataciteUrl(doi: String): Option[String] =
    http().json(Url(cfg.datacite.url, s"/dois/$doi")) match
      case Right(j) => RepositoryLanding.dataciteUrl(j)
      case Left(e) =>
        scribe.info(s"acquire: datacite: no record for $doi ($e)"); None

  /** The landing pages of the work, resolved ONE step each, at most `acquire.landing.max-pages`: the
    * Zenodo record of a Zenodo DOI, the OA indexes' repository and landing locations, then the page a
    * DataCite DOI resolves to. A `doi.org` link is never followed blind (it may land on a publisher):
    * a Zenodo DOI goes to Zenodo's API, any other is replaced by where DataCite says it resolves, and
    * a DOI DataCite does not know is skipped. */
  private def landingPdf(r: OaRecords, unpaywall: => Option[Json]): Option[FetchedFile] =
    if !cfg.landing.enabled then None
    else
      val rules = cfg.landing.rules
      val doiUrl = s"https://doi.org/${r.doi}"
      val indexed = RepositoryLanding.distinct(
        RepositoryLanding.zenodoRecordId(r.doi).map(_ => RepositoryLanding.Candidate(doiUrl, true)).toList ++
          unpaywall.toList.flatMap(RepositoryLanding.unpaywallCandidates) ++
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
        http().bytes(c.url) match
          case Left(e) =>
            scribe.info(s"acquire: landing: ${c.url} failed ($e)"); None
          case Right(b) if com.histmersion.server.acquire.FetchChain.looksLikePdf(b) =>
            Some(FetchedFile("repository", Some(c.url), b, "pdf"))
          case Right(b) if !RepositoryLanding.looksLikeHtml(b) =>
            scribe.info(s"acquire: landing: ${c.url} is neither a PDF nor a page — skipping"); None
          case Right(b) =>
            val html = new String(b, "UTF-8")
            val pdfs = RepositoryLanding.pdfsOf(c.url, c.repository, html, rules)
            if pdfs.isEmpty then
              val why = if RepositoryLanding.isRepository(c.url, c.repository, html, rules) then "names no citation_pdf_url"
                        else "is not a repository's page"
              scribe.info(s"acquire: landing: ${c.url} $why — skipping")
            pdfs.iterator.map(u => tryUrl(http(), "repository", u)).collectFirst { case Some(f) => f }

  /** An open Zenodo record's PDF, by its REST API, checked against the record's MD5. */
  private def zenodo(id: String): Option[FetchedFile] =
    http().json(Url(cfg.zenodo.url, s"/api/records/$id")) match
      case Left(e) =>
        scribe.info(s"acquire: zenodo: record $id failed ($e)"); None
      case Right(j) =>
        val files = RepositoryLanding.zenodoFiles(j)
        if files.isEmpty then scribe.info(s"acquire: zenodo: record $id has no open PDF")
        files.iterator.map { f =>
          tryUrl(http(), "zenodo", f.url).filter { got =>
            val ok = f.md5.forall(m => OaFetch.md5(got.bytes).forall(_ == m))
            if !ok then scribe.warn(s"acquire: zenodo: ${f.url} does not match the MD5 its record gives — skipping")
            ok
          }
        }.collectFirst { case Some(f) => f }
