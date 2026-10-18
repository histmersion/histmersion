package com.histmersion.server.plugins

import com.histmersion.core.{AppConfig, HotPlan, LoadedConfig, PluginState, PluginStatus}
import org.ekrich.config.ConfigUtil
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.{Plugin, PluginHttp}
import com.histmersion.server.acquire.{ArchiveOrg, Http, HttpPlugin, OpenAlexCrossref, OutboundTls, SttpHttp}
import com.histmersion.server.acquire.fetchers.BuiltinFetchers

/** The composition root's handle on the plugins: discover the providers on the server's class path,
  * add the host's built-in ones, and build the generation for a loaded config. Opens nothing — no
  * database, no listener, no socket (the HTTP client is built only when a plugin or a fetch first asks
  * for it) — so `--self-check` can call it. */
object Plugins:

  /** The config keys the plugin generation is built from: every plugin's block (and `order`), and the
    * host settings the built-in fetchers read (the acquisition endpoints, TLS roots and politeness). */
  val Keys: List[String] = List(PluginHost.Root, "histmersion.acquire", "histmersion.media.max-retry-after-seconds")

  /** Build the generation for `loaded`. On a config reload, `previous` is the running generation and
    * `changed` the keys that changed: a plugin whose block did not change is carried over as it is
    * (not decoded, resolved or registered again) — unless it holds a secret (re-resolved on every
    * reload, §3.7), it failed last time, a secret vault's plugin changed, or a host key every
    * plugin reads changed (`order`, `acquire.*`), which rebuilds them all. */
  def load(loaded: LoadedConfig, previous: Option[Generation] = None, changed: List[String] = Nil): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    // ONE acquisition client for every plugin and built-in fetcher of this generation, built on first use
    lazy val http = acquisitionHttp(loaded.config)
    PluginHost.build(found ++ builtins(loaded.config, () => http), loaded.user, loaded.env,
      services(() => HttpPlugin(http), loaded.env), errors, previous.fold(Map.empty)(carried(_, changed)))

  /** The plugins of `previous` a reload that changed `changed` may carry over unchanged. Pure. */
  def carried(previous: Generation, changed: List[String]): Map[String, (PluginStatus, Registries)] =
    val relevant = changed.filter(k => Keys.exists(HotPlan.keyMatches(k, _)))
    val ids      = relevant.map(pluginId)
    val vaults   = previous.contributions.collect { case (id, r) if r.secretVaults.nonEmpty => id }.toSet
    if ids.contains(None) || ids.flatten.exists(vaults) then Map.empty
    else
      val touched = ids.flatten.toSet
      previous.report.plugins.filter { st =>
        !touched(st.id) && (st.state == PluginState.Disabled.wire ||
          st.state == PluginState.Registered.wire && !previous.withSecrets(st.id))
      }.flatMap(st => previous.carry(st.id)).map(c => c._1.id -> c).toMap

  /** The plugin a changed key belongs to (`histmersion.plugins.<id>.…`); `None` for any other key —
    * `order`, the whole block, a host setting — which concerns every plugin. */
  def pluginId(key: String): Option[String] =
    scala.util.Try(ConfigUtil.splitPath(key).asScala.toList).toOption match
      case Some("histmersion" :: "plugins" :: id :: _) if id != PluginHost.OrderKey => Some(id)
      case _ => None

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
