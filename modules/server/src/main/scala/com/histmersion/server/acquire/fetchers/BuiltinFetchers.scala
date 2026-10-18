package com.histmersion.server.acquire.fetchers

import com.histmersion.core.AcquireConfig
import com.histmersion.plugin.*
import com.histmersion.server.acquire.{ArchiveClient, FetchChain, FetchedFile, Http, OpenAlexCrossref}

import hearth.kindlings.sconfigderivation.ConfigReader

/** Today's acquisition steps as BUILT-IN plugins (docs/research-plugins.md §5.3, D2): in every binary,
  * each one switched off by `histmersion.plugins.<id>.enabled = false`, all on by default.
  *
  * | plugin        | registers fetchers                       | access |
  * |---------------|------------------------------------------|--------|
  * | `archive-org` | `archive-org`                            | Open   |
  * | `oa-index`    | `unpaywall`, `core`, `openalex`, `landing` | Open |
  * | `pmc`         | `pmc` (PDF), `pmc-text` (JATS full text) | Open   |
  * | `arxiv`       | `arxiv`                                  | Open   |
  *
  * One plugin registers several fetchers because its steps are not adjacent in the chain (the PMC PDF
  * comes before arXiv, its JATS text after the landing pages) and share one upstream record per work,
  * kept in the fetch's memo ([[FetchContext.memo]]).
  *
  * Unlike a third-party plugin, these are host code: they read the host's own `histmersion.acquire`
  * settings (endpoints, the contact e-mail, the landing-page rules) and its parsers, so they are
  * constructed by the host ([[plugins]]) instead of being found by ServiceLoader. Their config block
  * holds only `enabled`; registration, ordering, the legal gate and the report are the plugin host's,
  * exactly as for any other plugin. */
object BuiltinFetchers:

  val Version = "1.0.0"

  /** The four plugins, over the host's acquisition settings and clients. The clients are asked for
    * only when a fetch runs: registering opens nothing (`--self-check`). */
  def plugins(cfg: AcquireConfig, http: () => Http, openalex: () => OpenAlexCrossref,
      archive: () => ArchiveClient): List[Plugin] =
    List(
      Builtin("archive-org", ctx => ctx.sourceFetchers.add("archive-org", ArchiveOrgFetcher(archive))),
      Builtin("oa-index", ctx =>
        val oa = OaIndex(cfg, http, openalex)
        for
          _ <- ctx.sourceFetchers.add("unpaywall", oa.unpaywall)
          _ <- ctx.sourceFetchers.add("core", oa.core)
          _ <- ctx.sourceFetchers.add("openalex", oa.openalex)
          _ <- ctx.sourceFetchers.add("landing", oa.landing)
        yield ()),
      Builtin("pmc", ctx =>
        val pmc = Pmc(cfg, http)
        ctx.sourceFetchers.add("pmc", pmc.pdf).flatMap(_ => ctx.sourceFetchers.add("pmc-text", pmc.text))),
      Builtin("arxiv", ctx => ctx.sourceFetchers.add("arxiv", ArxivFetcher(cfg, http)))
    )

/** A built-in plugin's settings: none of its own beyond the host's `enabled`. */
final case class BuiltinConfig() derives ConfigReader

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
