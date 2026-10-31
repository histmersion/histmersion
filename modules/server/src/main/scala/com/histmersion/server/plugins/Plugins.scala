package com.histmersion.server.plugins

import com.histmersion.pluginhost.{
  FirstPartySettings,
  Generation,
  HostServices,
  HttpTransport,
  PluginHost,
  PolicyHttp,
  Registries,
  ReservedNames
}

import com.histmersion.config.{AppConfig, HotPlan, LoadedConfig}
import com.histmersion.api.{PluginSet, PluginState, PluginStatus}
import org.ekrich.config.{ConfigFactory, ConfigUtil}
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.{Plugin, PluginHttp}
import com.histmersion.server.acquire.{CoreBudget, HttpPlugin, SttpHttp}
import com.histmersion.server.agents.BuiltinAi

/** The composition root's handle on the plugins: discover the providers on the server's class path — the first-party
  * fetchers, event catalogues, media collections, gazetteers, HTTP AI providers and vaults among them, as any plugin —
  * add the command-line AI runtimes the host still supplies itself, and build the generation for a loaded config. Opens
  * nothing — no database, no listener, no socket (the HTTP client is built only when a plugin or a fetch first asks for
  * it) — so `--self-check` can call it.
  */
object Plugins:

  /** The config keys the plugin generation is built from: every plugin's block (and `order`), the keys that shape the
    * client every plugin is lent, and the host settings the first-party plugins read ([[FirstPartySettings]]).
    */
  val Keys: List[String] = PluginHost.Root :: FirstPartySettings.hostKeys

  /** Build the generation for `loaded`. On a config reload, `previous` is the running generation and `changed` the keys
    * that changed: a plugin whose block did not change is carried over as it is (not decoded, resolved or registered
    * again) — unless it holds a secret (re-resolved on every reload), it failed last time, a secret vault's plugin
    * changed, `order` changed, or a host key it reads did ([[carried]]). `core` is the server's CORE budget, which
    * every generation's `ctx.quota("core")` lends and the new-source search spends from too.
    */
  def load(
      loaded: LoadedConfig,
      previous: Option[Generation] = None,
      changed: List[String] = Nil,
      core: CoreBudget = CoreBudget(),
      transport: AppConfig => HttpTransport = PolicyHttp.acquisitionTransport
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    // ONE acquisition client for every plugin of this generation, built on first use
    lazy val http = acquisitionHttp(loaded.config, transport)
    generation(
      loaded,
      found ++ providers,
      errors,
      () => HttpPlugin(http),
      core,
      previous.fold(Map.empty)(carried(_, changed))
    )

  /** The generation of the first-party source fetchers alone (and `extra`, a test's) over `http`, for `loaded`: the
    * chain a test or a one-off caller runs ([[com.histmersion.server.acquire.OaFetch]]).
    */
  def fetchers(
      loaded: LoadedConfig,
      http: () => PluginHttp,
      core: CoreBudget = CoreBudget(),
      extra: List[Plugin] = Nil
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    generation(loaded, extra ++ found.filter(p => Fetchers(p.info.id)), errors, http, core, Map.empty)

  private def generation(
      loaded: LoadedConfig,
      all: List[Plugin],
      errors: List[String],
      http: () => PluginHttp,
      core: CoreBudget,
      carry: Map[String, (PluginStatus, Registries)]
  ): Generation =
    // a split plugin's former settings (oa-index) mapped onto its successors, with a deprecation warning
    val legacy = LegacyPlugins.migrate(loaded.user, loaded.env, defaultKeys(all))
    // the user-added plugin set this binary was linked with (a relink writes it; none in a stock image)
    val (set, setError) = PluginSetResource.load(getClass.getClassLoader) match
      case Right(s) => (s, Nil)
      case Left(e)  => (None, List(e))
    val host = FirstPartySettings.hostView(hostSettings(loaded), legacy.user, legacy.env, all)
    val g = PluginHost.build(
      all,
      legacy.user,
      legacy.env,
      HostServices(http, loaded.env, log, CoreBudget.quotas(core, CoreBudget.limits(loaded.config.acquire.core))),
      errors ++ legacy.warnings,
      carry,
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = firstParty(set)
    )
    g.copy(report = g.report.copy(set = set, warnings = g.report.warnings ++ setError))

  /** The resolved `histmersion` tree a load read; a load made by hand (a test's) has none, and reads `reference.conf`
    * with its environment instead.
    */
  private def hostSettings(loaded: LoadedConfig): org.ekrich.config.Config =
    if !loaded.merged.root.isEmpty then loaded.merged else referenceSettings(loaded.env)

  /** `reference.conf` with `env`'s overrides, as a load without a file resolves it. */
  private def referenceSettings(env: org.ekrich.config.Config): org.ekrich.config.Config =
    AppConfig.loadWithUserFile(env, None).fold(_ => ConfigFactory.empty, _.merged)

  /** First-party: one of Histmersion's own ids ([[ReservedNames.firstPartyPlugins]]) loaded from the image's own class
    * path — not a plugin of the set a relink added under that id.
    */
  def firstParty(set: Option[PluginSet])(id: String): Boolean =
    ReservedNames.firstPartyPlugins(id) && !set.exists(_.jars.exists(_.plugins.exists(_.id == id)))

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
    * A change of `order` or of the whole block, or of a secret vault's plugin, rebuilds them all. A plugin's own block
    * changed rebuilds it. A changed HOST key rebuilds the plugins that read it ([[FirstPartySettings.rebuilds]]): a
    * first-party plugin the host keys it is supplied from (the fetchers `acquire.*`, an event catalogue its
    * `events.<catalogue>.*`, the landing step oa-unpaywall's contact; never a media collection or an AI provider, which
    * read theirs at each use), any other plugin the keys that shape the client it is lent.
    */
  def carried(previous: Generation, changed: List[String]): Map[String, (PluginStatus, Registries)] =
    val relevant = changed.filter(k => Keys.exists(HotPlan.keyMatches(k, _)))
    // a former plugin's key (oa-index) concerns its successors
    val ids =
      relevant.map(pluginId).flatMap(_.fold(List(Option.empty[String]))(id => LegacyPlugins.expand(id).map(Some(_))))
    val vaults = previous.contributions.collect { case (id, r) if r.secretVaults.nonEmpty => id }.toSet
    val whole = relevant.exists(k => HotPlan.keyMatches(k, PluginHost.Root))
    if ids.flatten.exists(vaults) || (whole && ids.contains(None)) then Map.empty
    else
      val touched = ids.flatten.toSet
      val hostKeys = relevant.filter(k => pluginId(k).isEmpty)
      // a plugin's block read by another first-party plugin: its key concerns that one too
      val peerKeys = relevant.filter(pluginId(_).isDefined)
      previous.report.plugins
        .filter { st =>
          !touched(st.id) && !hostKeys.exists(FirstPartySettings.rebuilds(st.id, _)) &&
          !peerKeys.exists(k => FirstPartySettings.of(st.id).exists(_.readsHostKey(k))) &&
          (st.state == PluginState.Disabled.wire ||
            st.state == PluginState.Registered.wire && !previous.withSecrets(st.id))
        }
        .flatMap(st => previous.carry(st.id))
        .map(c => c._1.id -> c)
        .toMap

  /** The plugin a changed key belongs to (`histmersion.plugins.<id>.…`); `None` for any other key — `order`, the whole
    * block, a host setting — which concerns every plugin.
    */
  def pluginId(key: String): Option[String] =
    scala.util.Try(ConfigUtil.splitPath(key).asScala.toList).toOption match
      case Some("histmersion" :: "plugins" :: id :: _) if id != PluginHost.OrderKey => Some(id)
      case _                                                                        => None

  /** CORE's key as the generation's `oa-core` plugin resolved it (its `api-key` secret reference, else `hostKey`,
    * `acquire.core.api-key`): the new-source search's `core` index asks with it too. `None` when the plugin is off.
    */
  def coreKey(g: Generation, hostKey: Option[String]): Option[String] =
    Option
      .when(g.registered(CorePlugin))(
        g.secret(CorePlugin, "api-key").fold(hostKey)(v => Option(v).map(_.trim).filter(_.nonEmpty))
      )
      .flatten

  /** The plugin that keys CORE: its `api-key` serves the new-source search too. */
  private val CorePlugin = "oa-core"

  /** The first-party plugins the host still supplies itself: the command-line AI runtimes (`ai-claude-code`,
    * `ai-codex`, `ai-opencode` — [[BuiltinAi]]). They read nothing at registration.
    */
  def providers: List[Plugin] = BuiltinAi.plugins

  /** The generation of a server or a `Deps` built without a config to load plugins from (a test's): the plugins on the
    * class path but the source fetchers, and the host's own command-line AI runtimes, registered through the plugin
    * host with an empty user config over `reference.conf` — so nothing is fetched (offline).
    */
  def offline: Generation = offlineWith(Nil, ConfigFactory.empty)

  /** [[offline]] with `extra` plugins (a test's) beside the class path's, under the user block `user` (the host's own
    * settings read from `user` too) and the environment `env`, carrying `carried` over from an earlier generation.
    */
  def offlineWith(
      extra: List[Plugin],
      user: org.ekrich.config.Config,
      env: org.ekrich.config.Config = ConfigFactory.empty,
      carried: Map[String, (PluginStatus, Registries)] = Map.empty
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    val all = found.filterNot(p => Fetchers(p.info.id)) ++ providers ++ extra
    val settings = AppConfig.loadWithUserFile(env, None).fold(_ => ConfigFactory.empty, _.merged)
    val host = FirstPartySettings.hostView(user.withFallback(settings).resolve(), user, env, all)
    PluginHost.build(
      all,
      user,
      env,
      services(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") }, env),
      errors,
      carried,
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = firstParty(None)
    )

  /** The first-party source fetchers' plugins, which an offline generation leaves out. */
  private val Fetchers: Set[String] =
    Set("archive-org", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "pmc", "arxiv")

  /** The host's services: the one outbound HTTP policy and scribe with the plugin's id on every line. */
  def services(http: () => PluginHttp, env: org.ekrich.config.Config): HostServices =
    HostServices(http, env, log)

  /** The acquisition client (`acquire.tls` included) under the one outbound policy. */
  private def acquisitionHttp(cfg: AppConfig, transport: AppConfig => HttpTransport): SttpHttp =
    new SttpHttp(PolicyHttp.acquisition(cfg, transport(cfg)))

  private def log(id: String, warn: Boolean, message: String): Unit =
    val line = if id.isEmpty then s"plugins: $message" else s"plugin $id: $message"
    if warn then scribe.warn(line) else scribe.info(line)
