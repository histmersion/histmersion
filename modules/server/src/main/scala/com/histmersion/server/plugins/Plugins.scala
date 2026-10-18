package com.histmersion.server.plugins

import com.histmersion.core.{AppConfig, LoadedConfig}
import com.histmersion.plugin.{Plugin, PluginHttp}
import com.histmersion.server.acquire.{ArchiveOrg, Http, HttpPlugin, OpenAlexCrossref, OutboundTls, SttpHttp}
import com.histmersion.server.acquire.fetchers.BuiltinFetchers

/** The composition root's handle on the plugins: discover the providers on the server's class path,
  * add the host's built-in ones, and build the generation for a loaded config. Opens nothing — no
  * database, no listener, no socket (the HTTP client is built only when a plugin or a fetch first asks
  * for it) — so `--self-check` can call it. */
object Plugins:

  def load(loaded: LoadedConfig): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    // ONE acquisition client for every plugin and built-in fetcher of this generation, built on first use
    lazy val http = acquisitionHttp(loaded.config)
    PluginHost.build(found ++ builtins(loaded.config, () => http), loaded.user, loaded.env,
      services(() => HttpPlugin(http), loaded.env), errors)

  /** The plugins the host itself supplies: today's acquisition steps (`archive-org`, `oa-index`,
    * `pmc`, `arxiv`), each its own plugin, enabled by default and switched off like any other. */
  def builtins(cfg: AppConfig, http: () => Http): List[Plugin] =
    lazy val openalex = OpenAlexCrossref(cfg.acquire, http())
    lazy val archive  = ArchiveOrg(cfg.acquire, http())
    BuiltinFetchers.plugins(cfg.acquire, http, () => openalex, () => archive)

  /** The host's services: the one outbound HTTP policy and scribe with the plugin's id on every line. */
  def services(http: () => PluginHttp, env: org.ekrich.config.Config): HostServices =
    HostServices(http, env, log)

  /** The acquisition client (`acquire.tls` included) under the one outbound policy. */
  private def acquisitionHttp(cfg: AppConfig): SttpHttp =
    val tls = OutboundTls.configure(cfg.acquire.tls)
    SttpHttp(cfg.acquire, maxRetryAfterSeconds = cfg.media.maxRetryAfterSeconds, tls = tls.map(_._1))

  private def log(id: String, warn: Boolean, message: String): Unit =
    val line = if id.isEmpty then s"plugins: $message" else s"plugin $id: $message"
    if warn then scribe.warn(line) else scribe.info(line)
