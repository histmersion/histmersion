package com.histmersion
package util.plugins

import com.histmersion.plugin.Header

import com.histmersion.plugin.Problem

import com.histmersion.pluginhost.{FirstPartySettings, Generation, HostServices, PluginHost, Registries, ReservedNames}

import com.histmersion.config.{AppConfig, ConfigReload, HotPlan, LoadedConfig}
import com.histmersion.util.settings.SettingsService
import com.histmersion.api.plugins.{PluginSet, PluginState, PluginStatus}
import org.ekrich.config.{ConfigFactory, ConfigUtil}
import hearth.kindlings.optics.syntax.*
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.pluginhost.SystemProcesses
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.plugin.UpstreamQuota

/** The plugin generations: discover the providers on the server's class path — the first-party fetchers, event
  * catalogues, media collections, gazetteers, HTTP AI providers and vaults among them, as any plugin — add the plugins
  * the host supplies itself (`provided`), and build the generation for a loaded config. The composition root says what
  * the host supplies, which client the plugins are lent and what their upstream quotas are. Opens nothing — no
  * database, no listener, no socket (the HTTP client is built only when a plugin or a fetch first asks for it) — so
  * `--self-check` can call it.
  */
object Plugins:
  type CoreKey = CoreKey.T
  object CoreKey extends Newtype[String, Pure]

  /** The config keys the plugin generation is built from: every plugin's block (and `order`), the keys that shape the
    * client every plugin is lent, and the host settings the first-party plugins read ([[FirstPartySettings]]).
    */
  val Keys: List[ConfigReload.Key] = ConfigReload.Key(PluginHost.Root) :: FirstPartySettings.hostKeys

  /** Build the generation for `loaded`, with `provided` beside the class path's plugins. On a config reload, `previous`
    * is the running generation and `changed` the keys that changed: a plugin whose block did not change is carried over
    * as it is (not decoded, resolved or registered again) — unless it holds a secret (re-resolved on every reload), it
    * failed last time, a secret vault's plugin changed, `order` changed, or a host key it reads did ([[carried]]).
    * `http` lends every plugin of the generation the one acquisition client (built on its first use); `quota` is what
    * `ctx.quota(<upstream>)` lends.
    */
  def load(
      loaded: LoadedConfig,
      provided: List[Plugin],
      http: () => Plugin.Http,
      quota: HostServices.Quotas,
      previous: Option[Generation] = None,
      changed: List[ConfigReload.ChangedKey] = Nil
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    generation(loaded, found ++ provided, errors, http, quota, previous.fold(Map.empty)(carried(_, changed)))

  /** The generation of the first-party source fetchers alone (and `extra`, a test's) over `http`, for `loaded`: the
    * chain a test or a one-off caller runs.
    */
  def fetchers(
      loaded: LoadedConfig,
      http: () => Plugin.Http,
      quota: HostServices.Quotas,
      extra: List[Plugin] = Nil
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    generation(loaded, extra ++ found.filter(p => Fetchers(p.info.id)), errors, http, quota, Map.empty)

  private def generation(
      loaded: LoadedConfig,
      all: List[Plugin],
      errors: List[PluginHost.Problem],
      http: () => Plugin.Http,
      quota: HostServices.Quotas,
      carry: Map[Plugin.Info.Id, (PluginStatus, Registries)]
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
      HostServices(http, loaded.env, log, quotaOf(quota), files = new DiskLocalFiles, processes = new SystemProcesses),
      errors ++ legacy.warnings.map(w => PluginHost.Problem(w.value)),
      carry,
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = firstParty(set)
    )
    g.modify(_.report.set)
      .setTo(set)
      .modify(_.report.warnings)
      .using(_ ++ setError.map(e => PluginsReport.Warnings(e.value)))

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
  def firstParty(set: Option[PluginSet])(id: Plugin.Info.Id): Boolean =
    ReservedNames.firstPartyPlugins(ReservedNames.Name(id.value)) && !set.exists(
      _.jars.exists(_.plugins.exists(_.id.value == id.value))
    )

  /** The keys each plugin's defaults resource declares (`enabled` aside): where a former plugin's key goes. */
  def defaultKeys(plugins: List[Plugin]): com.histmersion.util.plugins.Plugin.Id => Set[SettingsService.ConfigPath] =
    val keys = plugins
      .map(p =>
        p.info.id ->
          PluginHost
            .defaults(p)
            .toOption
            .map(_.root.keySet.asScala.toSet.filterNot(_ == "enabled").map(SettingsService.ConfigPath(_)))
            .getOrElse(Set.empty[SettingsService.ConfigPath])
      )
      .toMap
    id => keys.getOrElse(Plugin.Info.Id(id.value), Set.empty)

  /** The plugins of `previous` a reload that changed `changed` may carry over unchanged. Pure.
    *
    * A change of `order` or of the whole block, or of a secret vault's plugin, rebuilds them all. A plugin's own block
    * changed rebuilds it. A changed HOST key rebuilds the plugins that read it ([[FirstPartySettings.rebuilds]]): a
    * first-party plugin the host keys it is supplied from (the fetchers `acquire.*`, an event catalogue its
    * `events.<catalogue>.*`, the landing step oa-unpaywall's contact; never a media collection or an AI provider, which
    * read theirs at each use), any other plugin the keys that shape the client it is lent.
    */
  def carried(
      previous: Generation,
      changed: List[ConfigReload.ChangedKey]
  ): Map[Plugin.Info.Id, (PluginStatus, Registries)] =
    val relevant = changed.filter(k => Keys.exists(HotPlan.keyMatches(k, _)))
    // a former plugin's key (oa-index) concerns its successors
    val ids =
      relevant
        .map(pluginId)
        .flatMap(
          _.fold(List(Option.empty[Plugin.Info.Id]))(id =>
            LegacyPlugins.expand(id).map(i => Some(Plugin.Info.Id(i.value)))
          )
        )
    val vaults = previous.contributions.collect { case (id, r) if r.secretVaults.nonEmpty => id }.toSet
    val whole = relevant.exists(k => HotPlan.keyMatches(k, ConfigReload.Key(PluginHost.Root)))
    if ids.flatten.exists(vaults) || (whole && ids.contains(None)) then Map.empty
    else
      val touched = ids.flatten.toSet
      val hostKeys = relevant.filter(k => pluginId(k).isEmpty)
      // a plugin's block read by another first-party plugin: its key concerns that one too
      val peerKeys = relevant.filter(pluginId(_).isDefined)
      previous.report.plugins
        .map(st => Plugin.Info.Id(st.id.value) -> st)
        .filter { (id, st) =>
          !touched(id) && !hostKeys.exists(FirstPartySettings.rebuilds(id, _)) &&
          !peerKeys.exists(k => FirstPartySettings.of(id).exists(_.readsHostKey(k))) &&
          (st.state.value == PluginState.Disabled.wire.value ||
            st.state.value == PluginState.Registered.wire.value && !previous.withSecrets(id))
        }
        .flatMap((id, _) => previous.carry(id))
        .map(c => Plugin.Info.Id(c._1.id.value) -> c)
        .toMap

  /** The plugin a changed key belongs to (`histmersion.plugins.<id>.…`); `None` for any other key — `order`, the whole
    * block, a host setting — which concerns every plugin.
    */
  def pluginId(key: ConfigReload.ChangedKey): Option[com.histmersion.util.plugins.Plugin.Id] =
    scala.util.Try(ConfigUtil.splitPath(key.value).asScala.toList).toOption match
      case Some("histmersion" :: "plugins" :: id :: _) if id != PluginHost.OrderKey =>
        com.histmersion.util.plugins.Plugin.Id.option(id)
      case _ => None

  /** CORE's key as the generation's `oa-core` plugin resolved it (its `api-key` secret reference, else `hostKey`,
    * `acquire.core.api-key`): the new-source search's `core` index asks with it too. `None` when the plugin is off.
    */
  def coreKey(g: Generation, hostKey: Option[CoreKey]): Option[CoreKey] =
    Option
      .when(g.registered(CorePlugin))(
        g.secret(CorePlugin, PluginHost.ConfigPath("api-key"))
          .fold(hostKey)(v => Option(v.value).map(_.trim).filter(_.nonEmpty).map(CoreKey(_)))
      )
      .flatten

  /** The plugin that keys CORE: its `api-key` serves the new-source search too. */
  private val CorePlugin = Plugin.Info.Id("oa-core")

  /** The generation of a server or a `Deps` built without a config to load plugins from (a test's): the plugins on the
    * class path but the source fetchers, and the host's own `provided` ones, registered through the plugin host with an
    * empty user config over `reference.conf` — so nothing is fetched (offline).
    */
  def offline(provided: List[Plugin]): Generation = offlineWith(provided, Nil, ConfigFactory.empty)

  /** [[offline]] with `extra` plugins (a test's) beside the class path's and the host's `provided` ones, under the user
    * block `user` (the host's own settings read from `user` too) and the environment `env`, carrying `carried` over
    * from an earlier generation.
    */
  def offlineWith(
      provided: List[Plugin],
      extra: List[Plugin],
      user: org.ekrich.config.Config,
      env: org.ekrich.config.Config = ConfigFactory.empty,
      carried: Map[Plugin.Info.Id, (PluginStatus, Registries)] = Map.empty
  ): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    val all = found.filterNot(p => Fetchers(p.info.id) || Offline(p.info.id)) ++ provided ++ extra
    val settings = AppConfig.loadWithUserFile(env, None).fold(_ => ConfigFactory.empty, _.merged)
    val host = FirstPartySettings.hostView(user.withFallback(settings).resolve(), user, env, all)
    PluginHost.build(
      all,
      user,
      env,
      services(
        () =>
          new Plugin.Http {
            def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
              Left(Problem("offline"))
          },
        env
      ),
      errors,
      carried,
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = firstParty(None)
    )

  /** The first-party plugins besides the fetchers an offline generation leaves out: the Calibre library is the user's
    * own, and a test's or a tool's generation must never reach it.
    */
  private val Offline: Set[Plugin.Info.Id] = Set(Plugin.Info.Id("calibre"))

  /** The first-party source fetchers' plugins, which an offline generation leaves out. */
  private val Fetchers: Set[Plugin.Info.Id] =
    Set("archive-org", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "pmc", "arxiv").map(Plugin.Info.Id(_))

  /** The host's services: the one outbound HTTP policy and scribe with the plugin's id on every line. */
  def services(http: () => Plugin.Http, env: org.ekrich.config.Config): HostServices =
    HostServices(http, env, log, files = new DiskLocalFiles, processes = new SystemProcesses)

  private def quotaOf(quota: HostServices.Quotas): HostServices.QuotaArg1 => UpstreamQuota =
    upstream => quota(upstream.value)

  private def log(id: HostServices.LogArg1, warn: HostServices.LogArg2, message: HostServices.LogArg3): Unit =
    val line = if id.value.isEmpty then s"plugins: $message" else s"plugin $id: $message"
    if warn.value then scribe.warn(line) else scribe.info(line)
