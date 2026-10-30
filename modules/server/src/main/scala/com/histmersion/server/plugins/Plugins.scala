package com.histmersion.server.plugins

import com.histmersion.pluginhost.{Generation, HostServices, PluginHost, PolicyHttp, Registries}

import com.histmersion.config.{AppConfig, HotPlan, LoadedConfig}
import com.histmersion.api.{PluginState, PluginStatus}
import org.ekrich.config.{ConfigFactory, ConfigUtil}
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.{Plugin, PluginHttp}
import com.histmersion.server.acquire.{ArchiveOrg, CoreBudget, Http, HttpPlugin, OpenAlexCrossref, SttpHttp}
import com.histmersion.server.acquire.fetchers.BuiltinFetchers
import com.histmersion.server.events.BuiltinCatalogues
import com.histmersion.server.media.BuiltinMedia
import com.histmersion.server.agents.BuiltinAi

/** The composition root's handle on the plugins: discover the providers on the server's class path, add the host's
  * built-in ones, and build the generation for a loaded config. Opens nothing — no database, no listener, no socket
  * (the HTTP client is built only when a plugin or a fetch first asks for it) — so `--self-check` can call it.
  */
object Plugins:

  /** The config keys the plugin generation is built from: every plugin's block (and `order`), and the host settings the
    * built-in fetchers read (the acquisition endpoints, TLS roots and politeness).
    */
  val Keys: List[String] = List(PluginHost.Root, "histmersion.acquire", "histmersion.media.max-retry-after-seconds")

  /** Build the generation for `loaded`. On a config reload, `previous` is the running generation and `changed` the keys
    * that changed: a plugin whose block did not change is carried over as it is (not decoded, resolved or registered
    * again) — unless it holds a secret (re-resolved on every reload, §3.7), it failed last time, a secret vault's
    * plugin changed, or a host key every plugin reads changed (`order`, `acquire.*`), which rebuilds them all. `core`
    * is the server's CORE budget, which every generation's `core` fetcher spends from.
    */
  def load(
      loaded: LoadedConfig,
      previous: Option[Generation] = None,
      changed: List[String] = Nil,
      core: CoreBudget = CoreBudget()
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    // ONE acquisition client for every plugin and built-in fetcher of this generation, built on first use
    lazy val http = acquisitionHttp(loaded.config)
    val all = found ++ builtins(loaded.config, () => http, core)
    // a split plugin's former settings (oa-index) mapped onto its successors, with a deprecation warning
    val legacy = LegacyPlugins.migrate(loaded.user, loaded.env, defaultKeys(all))
    val g = PluginHost.build(
      all,
      legacy.user,
      legacy.env,
      services(() => HttpPlugin(http), loaded.env),
      errors ++ legacy.warnings,
      previous.fold(Map.empty)(carried(_, changed))
    )
    // the user-added plugin set this binary was linked with (a relink writes it; none in a stock image)
    val (set, setError) = PluginSetResource.load(getClass.getClassLoader) match
      case Right(s) => (s, Nil)
      case Left(e)  => (None, List(e))
    g.copy(report = g.report.copy(set = set, warnings = g.report.warnings ++ setError))

  /** The keys each plugin's defaults resource declares (`enabled` aside): where a former plugin's key goes. */
  def defaultKeys(plugins: List[Plugin]): String => Set[String] =
    val keys = plugins
      .map(p =>
        p.info.id ->
          PluginHost.defaults(p).toOption.map(_.root.keySet.asScala.toSet - "enabled").getOrElse(Set.empty[String])
      )
      .toMap
    id => keys.getOrElse(id, Set.empty)

  /** The plugins of `previous` a reload that changed `changed` may carry over unchanged. Pure.
    *
    * A change of `order` or of the whole block, or of a secret vault's plugin, rebuilds them all. A changed HOST key
    * (`acquire.*`, the media politeness) rebuilds every plugin but the `independent` ones — the built-in media
    * collections and event catalogues, which read their settings at each import and never the HTTP client a host key
    * shapes — so a media/events plugin is rebuilt only when its own block changes. `oa-unpaywall`, `oa-openalex` and
    * `oa-landing` are carried only all together ([[BuiltinFetchers.LandingGroup]]): the landing step reads what the
    * other two registered.
    */
  def carried(
      previous: Generation,
      changed: List[String],
      independent: Set[String] = BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds ++ BuiltinAi.pluginIds
  ): Map[String, (PluginStatus, Registries)] =
    val relevant = changed.filter(k => Keys.exists(HotPlan.keyMatches(k, _)))
    // a former plugin's key (oa-index) concerns its successors
    val ids =
      relevant.map(pluginId).flatMap(_.fold(List(Option.empty[String]))(id => LegacyPlugins.expand(id).map(Some(_))))
    val vaults = previous.contributions.collect { case (id, r) if r.secretVaults.nonEmpty => id }.toSet
    val whole = relevant.exists(k => HotPlan.keyMatches(k, PluginHost.Root))
    if ids.flatten.exists(vaults) || (whole && ids.contains(None)) then Map.empty
    else
      val touched = ids.flatten.toSet
      val hostKey = ids.contains(None)
      val kept = previous.report.plugins
        .filter { st =>
          !touched(st.id) && (!hostKey || independent(st.id)) && (st.state == PluginState.Disabled.wire ||
            st.state == PluginState.Registered.wire && !previous.withSecrets(st.id))
        }
        .flatMap(st => previous.carry(st.id))
        .map(c => c._1.id -> c)
        .toMap
      // the landing step reads which of oa-unpaywall/oa-openalex registered: the three go together
      val group = BuiltinFetchers.LandingGroup.filter(id => previous.report.plugins.exists(_.id == id))
      if group.forall(kept.contains) then kept else kept -- group

  /** The plugin a changed key belongs to (`histmersion.plugins.<id>.…`); `None` for any other key — `order`, the whole
    * block, a host setting — which concerns every plugin.
    */
  def pluginId(key: String): Option[String] =
    scala.util.Try(ConfigUtil.splitPath(key).asScala.toList).toOption match
      case Some("histmersion" :: "plugins" :: id :: _) if id != PluginHost.OrderKey => Some(id)
      case _                                                                        => None

  /** The plugins the host itself supplies, each its own plugin, enabled by default and switched off like any other:
    * today's acquisition steps (`archive-org`, the open-access indexes `oa-unpaywall`, `oa-core`, `oa-openalex`,
    * `oa-landing`, then `pmc`, `arxiv`), media collections (`media-wikidata`, `media-depicts`, `media-met`, … —
    * [[BuiltinMedia]]) and event catalogues (`events-gvp`, `events-noaa`, `events-wikidata` — [[BuiltinCatalogues]])
    * and AI providers (`ai-anthropic`, `ai-openai`, `ai-local`, `ai-claude-code`, `ai-codex`, `ai-opencode` —
    * [[BuiltinAi]]).
    */
  def builtins(cfg: AppConfig, http: () => Http, core: CoreBudget = CoreBudget()): List[Plugin] =
    lazy val openalex = OpenAlexCrossref(cfg.acquire, http())
    lazy val archive = ArchiveOrg(cfg.acquire, http())
    BuiltinFetchers.plugins(cfg.acquire, http, () => openalex, () => archive, core) ++ providers

  /** The built-in media collections, event catalogues and AI providers: they read nothing at registration. */
  def providers: List[Plugin] = BuiltinMedia.plugins ++ BuiltinCatalogues.plugins ++ BuiltinAi.plugins

  /** The generation of a server or a `Deps` built without a config to load plugins from (a test's): the built-in media
    * collections, event catalogues and AI providers, registered through the plugin host with an empty user config — and
    * no acquisition fetcher, so nothing is fetched (offline).
    */
  def offline: Generation =
    PluginHost.build(
      providers,
      ConfigFactory.empty,
      ConfigFactory.empty,
      services(
        () => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
        ConfigFactory.empty
      )
    )

  /** The host's services: the one outbound HTTP policy and scribe with the plugin's id on every line. */
  def services(http: () => PluginHttp, env: org.ekrich.config.Config): HostServices =
    HostServices(http, env, log)

  /** The acquisition client (`acquire.tls` included) under the one outbound policy. */
  private def acquisitionHttp(cfg: AppConfig): SttpHttp =
    new SttpHttp(PolicyHttp.acquisition(cfg, PolicyHttp.acquisitionTransport(cfg)))

  private def log(id: String, warn: Boolean, message: String): Unit =
    val line = if id.isEmpty then s"plugins: $message" else s"plugin $id: $message"
    if warn then scribe.warn(line) else scribe.info(line)
