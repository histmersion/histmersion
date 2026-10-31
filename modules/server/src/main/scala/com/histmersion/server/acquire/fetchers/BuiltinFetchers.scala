package com.histmersion.server.acquire.fetchers

import com.histmersion.config.AcquireConfig
import com.histmersion.plugin.*
import com.histmersion.server.acquire.{CoreBudget, Http}
import com.histmersion.upstreams.{ArchiveOrg, OpenAlexCrossref}

import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** Today's acquisition steps as BUILT-IN plugins (docs/research-plugins.md §5.3, D2, D4): in every binary, each one
  * switched off by `histmersion.plugins.<id>.enabled = false`, all on by default.
  *
  * | plugin         | registers fetchers                               | its own settings         | access |
  * |:---------------|:-------------------------------------------------|:-------------------------|:-------|
  * | `archive-org`  | `archive-org`                                    | —                        | Open   |
  * | `oa-unpaywall` | `unpaywall`                                      | `contact-email`          | Open   |
  * | `oa-core`      | `core`                                           | `api-key` (a [[Secret]]) | Open   |
  * | `oa-openalex`  | `openalex`                                       | —                        | Open   |
  * | `oa-landing`   | `landing` (Zenodo, `citation_pdf_url`, DataCite) | —                        | Open   |
  * | `pmc`          | `pmc` (PDF), `pmc-text` (JATS full text)         | —                        | Open   |
  * | `arxiv`        | `arxiv`                                          | —                        | Open   |
  *
  * One plugin per open-access index (D4): the four `oa-*` plugins were ONE plugin, `oa-index`, until 2026-10-18; its
  * settings are still honoured ([[com.histmersion.server.plugins.LegacyPlugins]]). `pmc` registers two fetchers because
  * its steps are not adjacent in the chain (the PMC PDF comes before arXiv, its JATS text after the landing pages) and
  * share one upstream record per work, kept in the fetch's memo ([[FetchContext.memo]]) — as the Unpaywall and OpenAlex
  * records are shared by their fetchers and the landing step ([[OaIndex]]).
  *
  * Unlike a third-party plugin, these are host code: they read the host's own `histmersion.acquire` settings
  * (endpoints, the landing-page rules) and its parsers, so they are constructed by the host ([[plugins]]) instead of
  * being found by ServiceLoader. Their config block holds `enabled` and the settings that are the user's own — a
  * contact, a credential — never an endpoint; registration, ordering, the legal gate and the report are the plugin
  * host's, exactly as for any other plugin.
  */
object BuiltinFetchers:

  val Version = "1.0.0"

  /** The ids of the open-access index plugins, in the order the chain asks their fetchers. */
  val OaIndexes: List[String] = List("oa-unpaywall", "oa-core", "oa-openalex", "oa-landing")

  /** The plugins whose state the landing step reads (it reads Unpaywall's and OpenAlex's records only while their
    * plugins are on): carried over a reload together or rebuilt together.
    */
  val LandingGroup: Set[String] = Set("oa-unpaywall", "oa-openalex", "oa-landing")

  /** The plugins, over the host's acquisition settings and clients. The clients are asked for only when a fetch runs:
    * registering opens nothing (`--self-check`). `core` is the server's CORE budget, which outlives the generation.
    */
  def plugins(
      cfg: AcquireConfig,
      http: () => Http,
      openalex: () => OpenAlexCrossref,
      archive: () => ArchiveOrg,
      core: CoreBudget = CoreBudget()
  ): List[Plugin] =
    val oa = OaIndex(cfg, http, openalex, core)
    List(
      Builtin("archive-org", ctx => ctx.sourceFetchers.add("archive-org", ArchiveOrgFetcher(archive))),
      BuiltinWith[UnpaywallConfig](
        "oa-unpaywall",
        (c, ctx) =>
          val email = c.email.orElse(cfg.contact)
          ctx.sourceFetchers.add("unpaywall", oa.unpaywall(email)).map(_ => oa.unpaywallRegistered(email))
      ),
      BuiltinWith[CoreConfig](
        "oa-core",
        (c, ctx) =>
          c.apiKey
            .fold[Either[String, Option[String]]](Right(cfg.core.key))(s => ctx.secret(s).map(CoreConfig.nonBlank))
            .flatMap(key => ctx.sourceFetchers.add("core", oa.core(key)))
      ),
      Builtin("oa-openalex", ctx => ctx.sourceFetchers.add("openalex", oa.openalex).map(_ => oa.openalexRegistered())),
      Builtin("oa-landing", ctx => ctx.sourceFetchers.add("landing", oa.landing)),
      Builtin(
        "pmc",
        ctx =>
          val pmc = Pmc(cfg, http)
          ctx.sourceFetchers.add("pmc", pmc.pdf).flatMap(_ => ctx.sourceFetchers.add("pmc-text", pmc.text))
      ),
      Builtin("arxiv", ctx => ctx.sourceFetchers.add("arxiv", ArxivFetcher(cfg, http)))
    )
