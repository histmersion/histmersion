package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Fetch, FetchedFile, SourceFetcher, UpstreamQuota, WorkKey}
import com.histmersion.upstreams.http.json
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.PageSize
import com.histmersion.upstreams.core.{CoreApi, CoreFailure}
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.Url
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Access
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def records(ctx: Fetch.Context, doi: OaRecords.Doi): OaRecords =
    ctx.memo(Fetch.Context.MemoKey(s"oa:records:$doi"))(OaRecords(doi, cfg, http, openalexClient, ctx.log))

  /** Unpaywall's record of `doi`, asked with `email` at most once per fetch. Unpaywall answers 422 to a request without
    * an e-mail, so without one it is not asked. The `unpaywall` step and the landing step share it when they use the
    * same contact.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unpaywallRecord(
      ctx: Fetch.Context,
      doi: OaRecords.Doi,
      email: Option[UnpaywallConfig.ContactEmail]
  ): Option[Either[Problem, Json]] =
    email.map(e =>
      ctx.memo(Fetch.Context.MemoKey(s"oa:unpaywall:$e:$doi"))(
        http
          .json(
            Url(
              RepositoryLanding.Candidate.Url(cfg.unpaywall.url.value),
              Url.UrlPath(s"/$doi"),
              "email" -> e.value
            ).value
          )
      )
    )

  /** `unpaywall`, asking with `email` (the plugin's `contact-email`, else Histmersion's). */
  def unpaywall(email: Option[UnpaywallConfig.ContactEmail]): SourceFetcher =
    ByDoi(
      ByDoi.Step("unpaywall"),
      (ctx, d) =>
        unpaywallRecord(ctx, d, email) match
          case None =>
            Iterator(
              Fetch.Miss(
                Fetch.Absence.Unconfigured,
                Problem.Config.Setting(
                  Problem.SettingKey("histmersion.plugins.oa-unpaywall.contact-email"),
                  Problem.Config.Fault.Empty
                )
              )
            )
          case Some(Left(e))       => Iterator(Fetch.failed(e))
          case Some(Right(record)) => unpaywallPdf(ctx, record)
    )

  /** `core`, with CORE's API key; without one it is never asked (a miss). Asked through the server's CORE budget
    * (`quota`): while CORE asked to wait, or the day's tokens are spent, it is a miss saying so, and the chain goes on.
    */
  def core(key: Option[OpenAccess.CoreKey], quota: UpstreamQuota): SourceFetcher = new SourceFetcher:
    val access = Access.Open
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
      work.doi
        .map(d => OaRecords.Doi(d.value))
        .fold[Fetch](ByDoi.noIdentifier(Problem.Field("doi")))(corePdf(ctx, _, key, quota))

  val openalex: SourceFetcher = ByDoi(ByDoi.Step("openalex"), (ctx, d) => openalexOa(ctx, records(ctx, d)))

  /** `landing`: the indexes' records only of the index plugins registered, Unpaywall's with the very contact (so the
    * very memo key) `oa-unpaywall` asks with — one request per upstream per work.
    */
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def landing(unpaywallEmail: Option[UnpaywallConfig.ContactEmail]): SourceFetcher = ByDoi(
    ByDoi.Step("landing"),
    (ctx, d) =>
      landingPdf(
        ctx,
        records(ctx, d),
        Option
          .when(ctx.registered(OpenAccess.Unpaywall))(unpaywallEmail)
          .flatMap(e => unpaywallRecord(ctx, d, e).flatMap(_.toOption)),
        ctx.registered(OpenAccess.OpenAlex)
      )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unpaywallPdf(ctx: Fetch.Context, record: Json): Iterator[Fetch] =
    AcquireParse
      .unpaywall(record)
      ._1
      .iterator
      .map(u => downloads.pdf(ctx.log, FetchedFile.Source("unpaywall"), Plugin.Http.Url(u.value)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def corePdf(
      ctx: Fetch.Context,
      doi: OaRecords.Doi,
      key: Option[OpenAccess.CoreKey],
      quota: UpstreamQuota
  ): Fetch =
    key.fold[Fetch](
      Fetch.Miss(
        Fetch.Absence.Unconfigured,
        Problem.Config.Setting(Problem.SettingKey("histmersion.plugins.oa-core.api-key"), Problem.Config.Fault.Empty)
      )
    ) { key =>
      CoreApi(cfg.core, http, quota).searchWorks(
        CoreApi.SearchWorksKey(key.value),
        CoreApi.SearchWorksQuery(s"""doi:"$doi""""),
        PageSize(1),
        OpenAccess.Lookup
      ) match
        case Left(skip @ CoreFailure.Skipped(_)) => Fetch.Miss(Fetch.Absence.RateLimited, skip.message)
        case Left(CoreFailure.Failed(why))       =>
          ctx.log.info(Plugin.Log.Line(s"CORE lookup failed (${why.sentence})"))
          Fetch.failed(why)
        case Right(json) =>
          Fetch.firstFound(
            AcquireParse
              .coreDownloadUrls(json)
              .iterator
              .map(u => downloads.pdf(ctx.log, FetchedFile.Source("core"), Plugin.Http.Url(u.value))),
            ByDoi.notHeld(Problem.Raw(s"$doi"))
          )
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def openalexOa(ctx: Fetch.Context, r: OaRecords): Iterator[Fetch] =
    r.openalex.iterator
      .flatMap(AcquireParse.openalexPdfUrls)
      .map(u => downloads.pdf(ctx.log, FetchedFile.Source("openalex"), Plugin.Http.Url(u.value)))

  // ── repository landing pages ────────────────────────────────────────────────────────────────

  /** The landing pages of the work, resolved ONE step each, at most `acquire.landing.max-pages`: the Zenodo record of a
    * Zenodo DOI, the OA indexes' repository and landing locations, then the page a DataCite DOI resolves to. A
    * `doi.org` link is never followed blind (it may land on a publisher): a Zenodo DOI goes to Zenodo's API, any other
    * is replaced by where DataCite says it resolves, and a DOI DataCite does not know is skipped — unless the location
    * is OPEN and the DOI handle API says it resolves to an open-access publisher's page (`oa-publisher-hosts`), which
    * is read like a repository's. `unpaywall` is Unpaywall's record (`None` when its plugin is off); `openalex` says
    * whether OpenAlex's record may be read (its plugin on).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def landingPdf(
      ctx: Fetch.Context,
      r: OaRecords,
      unpaywall: => Option[Json],
      openalex: Boolean
  ): Iterator[Fetch] =
    if !cfg.landing.enabled.value then
      Iterator(
        Fetch.Miss(
          Fetch.Absence.Unconfigured,
          Problem.Refused.Disabled(
            Problem.Refused.Feature("landing pages"),
            Problem.SettingKey("histmersion.acquire.landing.enabled"),
            None,
            None
          )
        )
      )
    else
      val rules = cfg.landing.rules
      val doiUrl = s"https://doi.org/${r.doi}"
      val indexed = RepositoryLanding.distinct(
        RepositoryLanding
          .zenodoRecordId(RepositoryLanding.ZenodoLocator(r.doi.value))
          .map(_ =>
            RepositoryLanding
              .Candidate(RepositoryLanding.Candidate.Url(doiUrl), RepositoryLanding.Candidate.Repository(true))
          )
          .toList ++
          unpaywall.toList.flatMap(RepositoryLanding.unpaywallCandidates) ++
          (if openalex then r.openalex.toList else Nil).flatMap(RepositoryLanding.openalexCandidates)
      )
      def viaDoi(c: RepositoryLanding.Candidate): LazyList[RepositoryLanding.Candidate] =
        val isDoi = RepositoryLanding.host(c.url).exists(h => h.value == "doi.org" || h.value == "dx.doi.org")
        if !isDoi || RepositoryLanding.zenodoRecordId(RepositoryLanding.ZenodoLocator(c.url.value)).isDefined then
          LazyList(c)
        else
          val d = OaRecords.Doi(c.url.value.replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", ""))
          val own = d.value.equalsIgnoreCase(r.doi.value)
          def viaHandle = Option
            .when(c.open.value)(
              (if own then r.handle.map(h => RepositoryLanding.Candidate.Url(h.value))
               else OaRecords.handleUrl(cfg, http, ctx.log, d).map(u => RepositoryLanding.Candidate.Url(u.value)))
            )
            .flatten
            .filter(u => RepositoryLanding.host(u).exists(rules.oaPublisher))
          LazyList.from(
            (if own then r.datacite.map(u => RepositoryLanding.Candidate.Url(u.value))
             else OaRecords.dataciteUrl(cfg, http, ctx.log, d).map(u => RepositoryLanding.Candidate.Url(u.value)))
              .orElse(viaHandle)
              .map(RepositoryLanding.Candidate(_, c.repository, c.publisher, c.open))
          )
      // the DOI's own DataCite landing page last, unless a location already named it
      val pages = (LazyList.from(indexed).flatMap(viaDoi) ++
        LazyList(()).flatMap(_ =>
          LazyList.from(
            r.datacite.map(u =>
              RepositoryLanding.Candidate(
                RepositoryLanding.Candidate.Url(u.value),
                RepositoryLanding.Candidate.Repository(false)
              )
            )
          )
        ))
        .scanLeft((Set.empty[RepositoryLanding.Candidate.Url], Option.empty[RepositoryLanding.Candidate])) {
          case ((seen, _), c) =>
            if seen(c.url) then (seen, None) else (seen + c.url, Some(c))
        }
        .flatMap(_._2)
        // a publisher's page is never read, and does not use up the pages a work may read
        .filter(c =>
          RepositoryLanding.zenodoRecordId(RepositoryLanding.ZenodoLocator(c.url.value)).isDefined || !publisherPage(
            ctx,
            c,
            rules
          )
        )
        .take(math.max(0, cfg.landing.maxPages.value))
      pages.iterator.map(resolvePage(ctx, _, rules))

  /** An OA index calls it a publisher's location, or its host is denied (`acquire.landing.deny-*`) — and it is not an
    * open-access publisher's open location (`oa-publisher-hosts`).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def publisherPage(
      ctx: Fetch.Context,
      c: RepositoryLanding.Candidate,
      rules: RepositoryLanding.Rules
  ): Boolean =
    val no = !RepositoryLanding.oaPublisherPage(c, rules) &&
      (c.publisher.value || RepositoryLanding.host(c.url).exists(rules.denied))
    if no then ctx.log.info(Plugin.Log.Line(s"landing: ${c.url} is a publisher's page — not read"))
    no

  /** One landing page, one step: a Zenodo record by its API; anything else is read, and a repository's page gives its
    * `citation_pdf_url`s (a page that turns out to be the PDF is taken as it is); an open-access publisher's open page
    * its `citation_pdf_url`s and OJS PDF galleys (provenance `publisher`).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def resolvePage(
      ctx: Fetch.Context,
      c: RepositoryLanding.Candidate,
      rules: RepositoryLanding.Rules
  ): Fetch =
    val none = Downloads.NoPage
    RepositoryLanding.zenodoRecordId(RepositoryLanding.ZenodoLocator(c.url.value)) match
      case Some(id)                             => zenodo(ctx, id, none)
      case None if publisherPage(ctx, c, rules) => none
      case None                                 =>
        val oaPub = RepositoryLanding.oaPublisherPage(c, rules)
        val source = FetchedFile.Source(if oaPub then "publisher" else "repository")
        http.get(Plugin.Http.Url(c.url.value)) match
          case Left(e) =>
            ctx.log.info(Plugin.Log.Line(s"landing: ${c.url} failed (${e.sentence})")); Fetch.failed(e)
          case Right(b) if Downloads.looksLikePdf(FetchedFile.Bytes(b.value)) =>
            Fetch.Found(
              FetchedFile(
                source,
                Some(FetchedFile.Url(c.url.value)),
                FetchedFile.Bytes(b.value),
                FetchedFile.Extension("pdf")
              )
            )
          case Right(b) if !RepositoryLanding.looksLikeHtml(RepositoryLanding.PageBytes(b.value)) =>
            ctx.log.info(Plugin.Log.Line(s"landing: ${c.url} is neither a PDF nor a page — skipping")); none
          case Right(b) =>
            val html = String(b.value, "UTF-8")
            val pdfs = RepositoryLanding.pdfsOf(
              c.url,
              c.repository,
              RepositoryLanding.Html(html),
              rules,
              RepositoryLanding.Candidate.Publisher(oaPub)
            )
            if pdfs.isEmpty then
              val why = if oaPub then "names no PDF on its own hosts"
              else if RepositoryLanding.isRepository(c.url, c.repository, RepositoryLanding.Html(html), rules) then
                "names no citation_pdf_url"
              else "is not a repository's page"
              ctx.log.info(Plugin.Log.Line(s"landing: ${c.url} $why — skipping"))
            Fetch.firstFound(pdfs.iterator.map(u => downloads.pdf(ctx.log, source, Plugin.Http.Url(u.value))), none)

  /** An open Zenodo record's PDF, by its REST API, checked against the record's MD5. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def zenodo(ctx: Fetch.Context, id: RepositoryLanding.ZenodoRecordId, none: Fetch): Fetch =
    http.json(Url(RepositoryLanding.Candidate.Url(cfg.zenodo.url.value), Url.UrlPath(s"/api/records/$id")).value) match
      case Left(e) =>
        ctx.log.info(Plugin.Log.Line(s"zenodo: record $id failed ($e)")); Fetch.failed(e)
      case Right(j) =>
        val files = RepositoryLanding.zenodoFiles(j)
        if files.isEmpty then ctx.log.info(Plugin.Log.Line(s"zenodo: record $id has no open PDF"))
        Fetch.firstFound(
          files.iterator
            .map { f =>
              downloads.pdf(ctx.log, FetchedFile.Source("zenodo"), Plugin.Http.Url(f.url.value)) match
                case Fetch.Found(got) if !f.md5.forall(m => Downloads.md5(got.bytes).forall(_ == m)) =>
                  ctx.log.warn(
                    Plugin.Log.Line(s"zenodo: ${f.url} does not match the MD5 its record gives — skipping")
                  )
                  Downloads.NotPdf
                case other => other
            },
          none
        )

object OpenAccess:
  type CoreKey = CoreKey.T
  object CoreKey extends Newtype[String, Pure]

  /** The plugins whose upstreams the landing step reads on their behalf. */
  val Unpaywall: Fetch.Context.RegisteredPluginId = Fetch.Context.RegisteredPluginId("oa-unpaywall")
  val OpenAlex: Fetch.Context.RegisteredPluginId = Fetch.Context.RegisteredPluginId("oa-openalex")

  /** The share of CORE's budget a DOI lookup spends. */
  val Lookup: UpstreamQuota.TakePurpose = UpstreamQuota.TakePurpose("lookup")
