package com.histmersion.server.acquire

import com.histmersion.pluginhost.{upstreamSettings, FetchChain, HostLog, PluginHost}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** The acquisition chain over the BUILT-IN fetchers (archive.org, Unpaywall, CORE, OpenAlex, PMC, arXiv, landing pages,
  * PMC's JATS text) — registered through the plugin host exactly as the server registers them, every one enabled, in
  * `acquire.chain` order. What a test or a one-off caller runs; the server runs the chain of its own plugin generation,
  * which may hold more fetchers or fewer.
  */
object OaFetch:
  def apply(cfg: AcquireConfig, http: Http): FetchChain =
    val upstream = cfg.upstreamSettings
    val openalex = com.histmersion.upstreams.OpenAlexCrossref(upstream, HttpPlugin(http), HostLog.scribe.plugin)
    val generation = PluginHost.build(
      fetchers.BuiltinFetchers.plugins(
        cfg,
        () => http,
        () => openalex,
        () => com.histmersion.upstreams.ArchiveOrg(upstream.archiveOrg, HttpPlugin(http))
      ),
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
