package com.histmersion.server

import com.histmersion.pluginhost.Generation

import com.histmersion.config.{AppConfig, LoadedConfig, UserConfigFile}
import com.histmersion.api.{ConfigFileInfo, ReloadReport}
import com.histmersion.server.hot.Pinning
import com.histmersion.server.plugins.Plugins
import javax.sql.DataSource

/** Everything the handlers need. Built once in `Main` (the composition root) and passed down — no
  * object-initialisation side effects anywhere below this.
  *
  * TWO DataSources, deliberately. `ds` is the application role: it owns the schema and every repo
  * goes through it. `queryDs` is the `histmersion_query` read-only role (migration V011) and is used by
  * exactly ONE operation — the `query` escape hatch — because that is the one place arbitrary,
  * model-written SQL is executed. Mixing them up is the bug the second field exists to make visible.
  *
  * HOT (docs/research-plugins.md §3.5, §3.6): in the server, `cfg`, `configFile` and `plugins` are
  * read from the hot component graph at each call — the generation the calling thread is pinned to
  * (an HTTP request, a job, an agent run), else the current one — and `ds`/`queryDs` are proxies that
  * take each connection from that generation's pool. So read `deps.cfg` where it is used (a `def`,
  * never a `val` captured at construction), and once into a local where one unit of work needs one
  * consistent config. Work handed to another thread carries its generation with [[pins]]. */
final class Deps(
    cfgNow: () => AppConfig,
    val ds: DataSource,
    val queryDs: DataSource,
    val maintenance: Maintenance,
    configFileNow: () => ConfigFileInfo,
    pluginsNow: () => Generation,
    /** Pins work to a generation; [[Pinning.none]] without a hot graph (a test's fixed `Deps`). */
    val pins: Pinning = Pinning.none,
    /** `histmersion config reload`: read the config file again and switch to it. */
    val reloader: () => ReloadReport = Deps.NoReload,
    /** A person's pause and cancel of the long background jobs this server runs. */
    val jobStops: com.histmersion.domain.jobs.JobStops = com.histmersion.domain.jobs.JobStops(),
    /** The load `cfg` came from: the settings file and the environment it was read with (Settings writes
      * that file). */
    loadedNow: Option[() => LoadedConfig] = None,
    /** Ends this process so that the service it runs under starts it again; `None` where none does. */
    val restart: Option[() => Unit] = None):

  /** The config the calling thread sees (its pinned generation's, else the current one). */
  def cfg: AppConfig = cfgNow()

  /** The user config file `cfg` was read with (`about` shows it). */
  def configFile: ConfigFileInfo = configFileNow()

  /** The plugins' generation for this config: their registries and the report `about` and
    * `/api/config` show. */
  def plugins: Generation = pluginsNow()

  /** The load the calling thread's config came from: its settings file and environment. */
  def loaded: LoadedConfig = loadedNow.fold(LoadedConfig(cfg, UserConfigFile(
    configFile.path.map(java.nio.file.Path.of(_)), configFile.loaded, configFile.unknownKeys)))(_())

object Deps:
  /** A fixed `Deps` — one config, one pair of DataSources, nothing hot (tests, tools); its plugins are
    * the built-in media collections and event catalogues and no fetcher ([[Plugins.offline]]). */
  def apply(cfg: AppConfig, ds: DataSource, queryDs: DataSource, maintenance: Maintenance = Maintenance(),
      configFile: ConfigFileInfo = ConfigFileInfo(), plugins: Generation = Plugins.offline): Deps =
    new Deps(() => cfg, ds, queryDs, maintenance, () => configFile, () => plugins)

  /** A fixed `Deps` whose read-only DataSource is the application one: for a test that does not
    * exercise `query` (a second role would need a second pool someone has to own and close). */
  def apply(cfg: AppConfig, ds: DataSource): Deps = apply(cfg, ds, ds)

  /** The reload of a server that was started without a config file to read again. */
  val NoReload: () => ReloadReport = () =>
    ReloadReport(published = false, generation = 0,
      errors = List("this server was started without a config file to reload"))
