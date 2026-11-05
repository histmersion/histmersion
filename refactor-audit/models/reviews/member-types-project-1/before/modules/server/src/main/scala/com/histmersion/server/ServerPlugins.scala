package com.histmersion.server

import com.histmersion.config.{AppConfig, LoadedConfig}
import com.histmersion.api.plugins.PluginStatus
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.{Generation, HostServices, HttpTransport, PolicyHttp, Registries}
import com.histmersion.domain.library.acquisition.{CoreBudget, HttpPlugin, SttpHttp}
import com.histmersion.server.agents.BuiltinAi
import com.histmersion.util.plugins.Plugins
import org.ekrich.config.ConfigFactory

/** The server's plugin generations: [[Plugins]] with what this host supplies — the command-line AI runtimes it still
  * runs itself ([[BuiltinAi]]), the one acquisition client every plugin of a generation is lent, and the CORE budget
  * `ctx.quota("core")` lends.
  */
object ServerPlugins:

  /** The generation for `loaded`; on a config reload, `previous` and the keys that `changed` ([[Plugins.load]]). `core`
    * is the server's CORE budget, which every generation lends and the new-source search spends from too.
    */
  def load(
      loaded: LoadedConfig,
      previous: Option[Generation] = None,
      changed: List[String] = Nil,
      core: CoreBudget,
      transport: AppConfig => HttpTransport = PolicyHttp.acquisitionTransport
  ): Generation =
    // ONE acquisition client for every plugin of this generation, built on first use
    lazy val http = new SttpHttp(PolicyHttp.acquisition(loaded.config, transport(loaded.config)))
    Plugins.load(loaded, providers, () => HttpPlugin(http), quotas(loaded, core), previous, changed)

  /** The first-party source fetchers alone (and `extra`, a test's) over `http` ([[Plugins.fetchers]]). */
  def fetchers(
      loaded: LoadedConfig,
      http: () => Plugin.Http,
      core: CoreBudget,
      extra: List[Plugin] = Nil
  ): Generation =
    Plugins.fetchers(loaded, http, quotas(loaded, core), extra)

  /** The generation of a server or a `Deps` built without a config to load plugins from ([[Plugins.offline]]). */
  def offline: Generation = Plugins.offline(providers)

  /** [[offline]] with `extra` plugins, under `user` and `env`, carrying `carried` ([[Plugins.offlineWith]]). */
  def offlineWith(
      extra: List[Plugin],
      user: org.ekrich.config.Config,
      env: org.ekrich.config.Config = ConfigFactory.empty,
      carried: Map[String, (PluginStatus, Registries)] = Map.empty
  ): Generation =
    Plugins.offlineWith(providers, extra, user, env, carried)

  /** The first-party plugins the host still supplies itself: the command-line AI runtimes (`ai-claude-code`,
    * `ai-codex`, `ai-opencode`). They read nothing at registration.
    */
  def providers: List[Plugin] = BuiltinAi.plugins

  private def quotas(loaded: LoadedConfig, core: CoreBudget): HostServices.Quotas =
    CoreBudget.quotas(core, CoreBudget.limits(loaded.config.acquire.core))
