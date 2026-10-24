package com.histmersion.server.acquire.fetchers

import com.histmersion.pluginhost.FetchChain

import com.histmersion.core.AcquireConfig
import com.histmersion.plugin.*
import com.histmersion.server.acquire.{ArchiveClient, FetchedFile, Http, OpenAlexCrossref}

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given

/** Today's acquisition steps as BUILT-IN plugins (docs/research-plugins.md §5.3, D2, D4): in every
  * binary, each one switched off by `histmersion.plugins.<id>.enabled = false`, all on by default.
  *
  * | plugin         | registers fetchers                       | its own settings | access |
  * |----------------|------------------------------------------|------------------|--------|
  * | `archive-org`  | `archive-org`                            | —                | Open   |
  * | `oa-unpaywall` | `unpaywall`                              | `contact-email`  | Open   |
  * | `oa-core`      | `core`                                   | `api-key` (a [[Secret]]) | Open |
  * | `oa-openalex`  | `openalex`                               | —                | Open   |
  * | `oa-landing`   | `landing` (Zenodo, `citation_pdf_url`, DataCite) | —        | Open   |
  * | `pmc`          | `pmc` (PDF), `pmc-text` (JATS full text) | —                | Open   |
  * | `arxiv`        | `arxiv`                                  | —                | Open   |
  *
  * One plugin per open-access index (D4): the four `oa-*` plugins were ONE plugin, `oa-index`, until
  * 2026-10-18; its settings are still honoured ([[com.histmersion.server.plugins.LegacyPlugins]]). `pmc`
  * registers two fetchers because its steps are not adjacent in the chain (the PMC PDF comes before
  * arXiv, its JATS text after the landing pages) and share one upstream record per work, kept in the
  * fetch's memo ([[FetchContext.memo]]) — as the Unpaywall and OpenAlex records are shared by their
  * fetchers and the landing step ([[OaIndex]]).
  *
  * Unlike a third-party plugin, these are host code: they read the host's own `histmersion.acquire`
  * settings (endpoints, the landing-page rules) and its parsers, so they are constructed by the host
  * ([[plugins]]) instead of being found by ServiceLoader. Their config block holds `enabled` and the
  * settings that are the user's own — a contact, a credential — never an endpoint; registration,
  * ordering, the legal gate and the report are the plugin host's, exactly as for any other plugin. */
object BuiltinFetchers:

  val Version = "1.0.0"

  /** The ids of the open-access index plugins, in the order the chain asks their fetchers. */
  val OaIndexes: List[String] = List("oa-unpaywall", "oa-core", "oa-openalex", "oa-landing")

  /** The plugins whose state the landing step reads (it reads Unpaywall's and OpenAlex's records only
    * while their plugins are on): carried over a reload together or rebuilt together. */
  val LandingGroup: Set[String] = Set("oa-unpaywall", "oa-openalex", "oa-landing")

  /** The plugins, over the host's acquisition settings and clients. The clients are asked for only
    * when a fetch runs: registering opens nothing (`--self-check`). */
  def plugins(cfg: AcquireConfig, http: () => Http, openalex: () => OpenAlexCrossref,
      archive: () => ArchiveClient): List[Plugin] =
    val oa = OaIndex(cfg, http, openalex)
    List(
      Builtin("archive-org", ctx => ctx.sourceFetchers.add("archive-org", ArchiveOrgFetcher(archive))),
      BuiltinWith[UnpaywallConfig]("oa-unpaywall", (c, ctx) =>
        val email = c.email.orElse(cfg.contact)
        ctx.sourceFetchers.add("unpaywall", oa.unpaywall(email)).map(_ => oa.unpaywallRegistered(email))),
      BuiltinWith[CoreConfig]("oa-core", (c, ctx) =>
        c.apiKey.fold[Either[String, Option[String]]](Right(cfg.core.key))(s => ctx.secret(s).map(CoreConfig.nonBlank))
          .flatMap(key => ctx.sourceFetchers.add("core", oa.core(key)))),
      Builtin("oa-openalex", ctx => ctx.sourceFetchers.add("openalex", oa.openalex).map(_ => oa.openalexRegistered())),
      Builtin("oa-landing", ctx => ctx.sourceFetchers.add("landing", oa.landing)),
      Builtin("pmc", ctx =>
        val pmc = Pmc(cfg, http)
        ctx.sourceFetchers.add("pmc", pmc.pdf).flatMap(_ => ctx.sourceFetchers.add("pmc-text", pmc.text))),
      Builtin("arxiv", ctx => ctx.sourceFetchers.add("arxiv", ArxivFetcher(cfg, http)))
    )

/** A built-in plugin's settings: none of its own beyond the host's `enabled`. */
final case class BuiltinConfig() derives ConfigReader

/** `oa-unpaywall`'s settings. `contact-email`: the e-mail Unpaywall requires with every request (its
  * `email` parameter, nothing else); `null` = Histmersion's own `acquire.contact-email`, and with neither
  * Unpaywall is not asked (it answers 422). Per install, never in the repository. */
final case class UnpaywallConfig(contactEmail: Option[String]) derives ConfigReader:
  def email: Option[String] = contactEmail.map(_.trim).filter(_.nonEmpty)

/** `oa-core`'s settings. `api-key`: CORE's API key as a secret reference (`env:<NAME>`, a vault's);
  * `null` = Histmersion's own `acquire.core.api-key`; without a key CORE is not asked. */
final case class CoreConfig(apiKey: Option[Secret]) derives ConfigReader

object CoreConfig:
  private[fetchers] def nonBlank(v: String): Option[String] = Option(v).map(_.trim).filter(_.nonEmpty)

/** One built-in plugin with settings of its own, `C`, decoded from its block by `C`'s reader. Its
  * defaults resource (`META-INF/histmersion/plugins/<id>.conf`, in the server) names every key. */
final class BuiltinWith[C](id: String, contribute: (C, PluginContext) => Either[String, Unit])(using ConfigReader[C])
    extends Plugin:
  type Config = C
  val info: PluginInfo = PluginInfo(id, BuiltinFetchers.Version, Spi.Version)
  def register(config: C, ctx: PluginContext): Either[String, Unit] = contribute(config, ctx)

/** One built-in plugin: its id, and what it registers. Its defaults resource
  * (`META-INF/histmersion/plugins/<id>.conf`, in the server) sets `enabled = true`. */
final class Builtin(id: String, contribute: PluginContext => Either[String, Unit]) extends Plugin:
  type Config = BuiltinConfig
  val info: PluginInfo = PluginInfo(id, BuiltinFetchers.Version, Spi.Version)
  def register(config: BuiltinConfig, ctx: PluginContext): Either[String, Unit] = contribute(ctx)

/** What the built-in fetchers share: one download, checked. */
private[acquire] object Fetching:

  /** Download `url`; a copy only when it is a PDF. Any other answer is logged and is no copy. */
  def tryUrl(http: Http, source: String, url: String, headers: Map[String, String] = Map.empty): Option[FetchedFile] =
    http.bytes(url, headers) match
      case Right(b) if FetchChain.looksLikePdf(b) => Some(FetchedFile(source, Some(url), b, "pdf"))
      case Right(b) =>
        scribe.info(s"acquire: $source: $url returned ${b.length} bytes that are not a PDF — skipping")
        None
      case Left(e) =>
        scribe.info(s"acquire: $source fetch failed ($e)")
        None

  /** A step's answer: the copy, or a miss saying which identifier it needed or that it found none. */
  def answer(found: Option[FetchedFile], none: => String): Fetch =
    found.fold(Fetch.Miss(none))(Fetch.Found(_))

  /** A fetcher from a function — the built-ins are all open access. */
  def open(f: (WorkKey, FetchContext) => Fetch): SourceFetcher = new SourceFetcher:
    val access: Access = Access.Open
    def fetch(work: WorkKey, ctx: FetchContext): Fetch = f(work, ctx)
