package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Fetch, FetchedFile, SourceFetcher, UpstreamQuota, WorkKey}
import com.histmersion.upstreams.http.json
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.core.{CoreApi, CoreFailure}
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.Url
import io.circe.Json
import com.histmersion.plugin.Plugin

/** The fetchers of the four open-access index plugins, all by DOI: Unpaywall's best OA location (`oa-unpaywall`),
  * CORE's download URLs with a key (`oa-core`), OpenAlex's OA locations (`oa-openalex`), and the repository landing
  * pages those indexes and DataCite name (`oa-landing`). The Unpaywall and OpenAlex records are asked once per work and
  * shared by the direct steps and the landing step, through the fetch's memo (keys `oa:<what>:<doi>` — no one plugin
  * owns them).
  *
  * The landing step reads an index's record ON BEHALF OF that index's plugin: Unpaywall's only while `oa-unpaywall` is
  * registered in the generation the fetch runs in, asked with that plugin's contact; OpenAlex's only while
  * `oa-openalex` is ([[Fetch.Context.registered]]). With neither it works from the DOI alone — a Zenodo DOI's record,
  * the page DataCite says the DOI resolves to.
  *
  * The HTTP client is asked for only when a fetch runs: registering opens nothing.
  */
final class OpenAccess(cfg: UpstreamSettings, client: () => Plugin.Http, log: Plugin.Log):

  private def http: Plugin.Http = client()
  private def downloads = Downloads(http)
  private lazy val openalexClient = OpenAlexCrossref(cfg, http, log)

  private def records(ctx: Fetch.Context, doi: com.histmersion.plugins.fetchers.OpenAccess.RecordsDoi): OaRecords =
    ctx.memo(s"oa:records:$doi")(OaRecords(doi, cfg, http, openalexClient, ctx.log))

  /** Unpaywall's record of `doi`, asked with `email` at most once per fetch. Unpaywall answers 422 to a request without
    * an e-mail, so without one it is not asked. The `unpaywall` step and the landing step share it when they use the
    * same contact.
    */
  private def unpaywallRecord(ctx: Fetch.Context, doi: com.histmersion.plugins.fetchers.OpenAccess.UnpaywallRecordDoi, email: Option[com.histmersion.plugins.fetchers.OpenAccess.UnpaywallRecordEmail]): Option[Json] =
    email.flatMap(e =>
      ctx.memo(s"oa:unpaywall:$e:$doi")(http.json(Url(cfg.unpaywall.url, s"/$doi", "email" -> e)).toOption)
    )

  /** `unpaywall`, asking with `email` (the plugin's `contact-email`, else Histmersion's). */
  def unpaywall(email: Option[com.histmersion.plugins.fetchers.OpenAccess.UnpaywallEmail]): SourceFetcher =
    ByDoi("unpaywall", (ctx, d) => unpaywallPdf(ctx, unpaywallRecord(ctx, d, email)))

  /** `core`, with CORE's API key; without one it is never asked (a miss). Asked through the server's CORE budget
    * (`quota`): while CORE asked to wait, or the day's tokens are spent, it is a miss saying so, and the chain goes on.
    */
  def core(key: Option[com.histmersion.plugins.fetchers.OpenAccess.CoreKey], quota: UpstreamQuota): SourceFetcher = new SourceFetcher:
    val access = com.histmersion.plugin.Access.Open
    def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
      work.doi.map(_.value).fold[Fetch](Fetch.Miss("core needs a DOI"))(corePdf(ctx, _, key, quota))

  val openalex: SourceFetcher = ByDoi("openalex", (ctx, d) => openalexOa(ctx, records(ctx, d)))

  /** `landing`: the indexes' records only of the index plugins registered, Unpaywall's with the very contact (so the
    * very memo key) `oa-unpaywall` asks with — one request per upstream per work.
    */
  def landing(unpaywallEmail: Option[com.histmersion.plugins.fetchers.OpenAccess.LandingUnpaywallEmail]): SourceFetcher = ByDoi(
    "landing",
    (ctx, d) =>
      landingPdf(
        ctx,
        records(ctx, d),
        Option.when(ctx.registered(OpenAccess.Unpaywall))(unpaywallEmail).flatMap(e => unpaywallRecord(ctx, d, e)),
        ctx.registered(OpenAccess.OpenAlex)
      )
  )

  private def unpaywallPdf(ctx: Fetch.Context, record: Option[Json]): Option[FetchedFile] =
    record.toList
      .map(AcquireParse.unpaywall)
      .flatMap(_._1)
      .flatMap(u => downloads.pdf(ctx.log, "unpaywall", u))
      .headOption

  private def corePdf(ctx: Fetch.Context, doi: com.histmersion.plugins.fetchers.OpenAccess.CorePdfDoi, key: Option[com.histmersion.plugins.fetchers.OpenAccess.CorePdfKey], quota: UpstreamQuota): Fetch =
    key.fold[Fetch](Fetch.Miss("core needs CORE's API key (plugins.oa-core.api-key or acquire.core.api-key)")) { key =>
      CoreApi(cfg.core, http, quota).searchWorks(key, s"""doi:"$doi"""", 1, OpenAccess.Lookup) match
        case Left(skip @ CoreFailure.Skipped(_)) => Fetch.Miss(skip.message)
        case Left(CoreFailure.Failed(why))       =>
          ctx.log.info(s"CORE lookup failed ($why)")
          Fetch.Miss(s"core has no copy of $doi")
        case Right(json) =>
          AcquireParse
            .coreDownloadUrls(json)
            .iterator
            .flatMap(u => downloads.pdf(ctx.log, "core", u))
            .nextOption()
            .fold(Fetch.Miss(s"core has no copy of $doi"))(Fetch.Found(_))
    }

  private def openalexOa(ctx: Fetch.Context, r: OaRecords): Option[FetchedFile] =
    r.openalex.toList
      .flatMap(AcquireParse.openalexPdfUrls)
      .flatMap(u => downloads.pdf(ctx.log, "openalex", u))
      .headOption

  // ── repository landing pages ────────────────────────────────────────────────────────────────

  /** The landing pages of the work, resolved ONE step each, at most `acquire.landing.max-pages`: the Zenodo record of a
    * Zenodo DOI, the OA indexes' repository and landing locations, then the page a DataCite DOI resolves to. A
    * `doi.org` link is never followed blind (it may land on a publisher): a Zenodo DOI goes to Zenodo's API, any other
    * is replaced by where DataCite says it resolves, and a DOI DataCite does not know is skipped — unless the location
    * is OPEN and the DOI handle API says it resolves to an open-access publisher's page (`oa-publisher-hosts`), which
    * is read like a repository's. `unpaywall` is Unpaywall's record (`None` when its plugin is off); `openalex` says
    * whether OpenAlex's record may be read (its plugin on).
    */
  private def landingPdf(
      ctx: Fetch.Context,
      r: OaRecords,
      unpaywall: => Option[Json],
      openalex: com.histmersion.plugins.fetchers.OpenAccess.LandingPdfOpenalex
  ): Option[FetchedFile] =
    if !cfg.landing.enabled then None
    else
      val rules = cfg.landing.rules
      val doiUrl = s"https://doi.org/${r.doi}"
      val indexed = RepositoryLanding.distinct(
        RepositoryLanding.zenodoRecordId(r.doi).map(_ => RepositoryLanding.Candidate(doiUrl, true)).toList ++
          unpaywall.toList.flatMap(RepositoryLanding.unpaywallCandidates) ++
          (if openalex then r.openalex.toList else Nil).flatMap(RepositoryLanding.openalexCandidates)
      )
      def viaDoi(c: RepositoryLanding.Candidate): LazyList[RepositoryLanding.Candidate] =
        val isDoi = RepositoryLanding.host(c.url).exists(h => h == "doi.org" || h == "dx.doi.org")
        if !isDoi || RepositoryLanding.zenodoRecordId(c.url).isDefined then LazyList(c)
        else
          val d = c.url.replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
          val own = d.equalsIgnoreCase(r.doi)
          def viaHandle = Option
            .when(c.open)(if own then r.handle else OaRecords.handleUrl(cfg, http, ctx.log, d))
            .flatten
            .filter(u => RepositoryLanding.host(u).exists(rules.oaPublisher))
          LazyList.from(
            (if own then r.datacite else OaRecords.dataciteUrl(cfg, http, ctx.log, d))
              .orElse(viaHandle)
              .map(RepositoryLanding.Candidate(_, c.repository, c.publisher, c.open))
          )
      // the DOI's own DataCite landing page last, unless a location already named it
      val pages = (LazyList.from(indexed).flatMap(viaDoi) ++
        LazyList(()).flatMap(_ => LazyList.from(r.datacite.map(RepositoryLanding.Candidate(_, false)))))
        .scanLeft((Set.empty[String], Option.empty[RepositoryLanding.Candidate])) { case ((seen, _), c) =>
          if seen(c.url) then (seen, None) else (seen + c.url, Some(c))
        }
        .flatMap(_._2)
        // a publisher's page is never read, and does not use up the pages a work may read
        .filter(c => RepositoryLanding.zenodoRecordId(c.url).isDefined || !publisherPage(ctx, c, rules))
        .take(math.max(0, cfg.landing.maxPages))
      pages.map(resolvePage(ctx, _, rules)).collectFirst { case Some(f) => f }

  /** An OA index calls it a publisher's location, or its host is denied (`acquire.landing.deny-*`) — and it is not an
    * open-access publisher's open location (`oa-publisher-hosts`).
    */
  private def publisherPage(
      ctx: Fetch.Context,
      c: RepositoryLanding.Candidate,
      rules: RepositoryLanding.Rules
  ): com.histmersion.plugins.fetchers.OpenAccess.PublisherPageResult =
    val no = !RepositoryLanding.oaPublisherPage(c, rules) &&
      (c.publisher || RepositoryLanding.host(c.url).exists(rules.denied))
    if no then ctx.log.info(s"landing: ${c.url} is a publisher's page — not read")
    no

  /** One landing page, one step: a Zenodo record by its API; anything else is read, and a repository's page gives its
    * `citation_pdf_url`s (a page that turns out to be the PDF is taken as it is); an open-access publisher's open page
    * its `citation_pdf_url`s and OJS PDF galleys (provenance `publisher`).
    */
  private def resolvePage(
      ctx: Fetch.Context,
      c: RepositoryLanding.Candidate,
      rules: RepositoryLanding.Rules
  ): Option[FetchedFile] =
    RepositoryLanding.zenodoRecordId(c.url) match
      case Some(id)                             => zenodo(ctx, id)
      case None if publisherPage(ctx, c, rules) => None
      case None                                 =>
        val oaPub = RepositoryLanding.oaPublisherPage(c, rules)
        val source = if oaPub then "publisher" else "repository"
        http.get(c.url) match
          case Left(e) =>
            ctx.log.info(s"landing: ${c.url} failed ($e)"); None
          case Right(b) if Downloads.looksLikePdf(b) =>
            Some(FetchedFile(source, Some(c.url), b, "pdf"))
          case Right(b) if !RepositoryLanding.looksLikeHtml(b) =>
            ctx.log.info(s"landing: ${c.url} is neither a PDF nor a page — skipping"); None
          case Right(b) =>
            val html = String(b, "UTF-8")
            val pdfs = RepositoryLanding.pdfsOf(c.url, c.repository, html, rules, oaPub)
            if pdfs.isEmpty then
              val why = if oaPub then "names no PDF on its own hosts"
              else if RepositoryLanding.isRepository(c.url, c.repository, html, rules) then "names no citation_pdf_url"
              else "is not a repository's page"
              ctx.log.info(s"landing: ${c.url} $why — skipping")
            pdfs.iterator.map(u => downloads.pdf(ctx.log, source, u)).collectFirst { case Some(f) => f }

  /** An open Zenodo record's PDF, by its REST API, checked against the record's MD5. */
  private def zenodo(ctx: Fetch.Context, id: com.histmersion.plugins.fetchers.OpenAccess.ZenodoId): Option[FetchedFile] =
    http.json(Url(cfg.zenodo.url, s"/api/records/$id")) match
      case Left(e) =>
        ctx.log.info(s"zenodo: record $id failed ($e)"); None
      case Right(j) =>
        val files = RepositoryLanding.zenodoFiles(j)
        if files.isEmpty then ctx.log.info(s"zenodo: record $id has no open PDF")
        files.iterator
          .map { f =>
            downloads.pdf(ctx.log, "zenodo", f.url).filter { got =>
              val ok = f.md5.forall(m => Downloads.md5(got.bytes).forall(_ == m))
              if !ok then ctx.log.warn(s"zenodo: ${f.url} does not match the MD5 its record gives — skipping")
              ok
            }
          }
          .collectFirst { case Some(f) => f }

object OpenAccess:
  /** The plugins whose upstreams the landing step reads on their behalf. */
  type RecordsDoi = RecordsDoi.T
  object RecordsDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnpaywallRecordDoi = UnpaywallRecordDoi.T
  object UnpaywallRecordDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnpaywallRecordEmail = UnpaywallRecordEmail.T
  object UnpaywallRecordEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnpaywallEmail = UnpaywallEmail.T
  object UnpaywallEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoreKey = CoreKey.T
  object CoreKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LandingUnpaywallEmail = LandingUnpaywallEmail.T
  object LandingUnpaywallEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CorePdfDoi = CorePdfDoi.T
  object CorePdfDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CorePdfKey = CorePdfKey.T
  object CorePdfKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LandingPdfOpenalex = LandingPdfOpenalex.T
  object LandingPdfOpenalex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PublisherPageResult = PublisherPageResult.T
  object PublisherPageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZenodoId = ZenodoId.T
  object ZenodoId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Unpaywall = "oa-unpaywall"
  val OpenAlex = "oa-openalex"

  /** The share of CORE's budget a DOI lookup spends. */
  val Lookup = "lookup"
