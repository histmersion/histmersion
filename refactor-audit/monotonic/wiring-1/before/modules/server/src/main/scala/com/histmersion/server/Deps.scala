package com.histmersion.server

import com.histmersion.util.data.maintenance.WriteGate

import com.histmersion.pluginhost.Generation

import com.histmersion.config.{AppConfig, Environment, LoadedConfig, UserConfigFile, UserPaths}
import com.histmersion.api.settings.{ConfigFileInfo, ReloadReport}
import com.histmersion.util.jobs.Pinning
import com.histmersion.util.database.Transactor
import java.time.Clock
import javax.sql.DataSource

/** Everything the handlers need. Built once in `Main` (the composition root) and passed down — no object-initialisation
  * side effects anywhere below this.
  *
  * TWO DataSources, deliberately. `ds` is the application role: it owns the schema and every repo goes through it.
  * `queryDs` is the `histmersion_query` read-only role (migration V011) and is used by exactly ONE operation — the
  * `query` escape hatch — because that is the one place arbitrary, model-written SQL is executed. Mixing them up is the
  * bug the second field exists to make visible.
  *
  * HOT (docs/research-plugins.md §3.5, §3.6): in the server, `cfg`, `configFile` and `plugins` are read from the hot
  * component graph at each call — the generation the calling thread is pinned to (an HTTP request, a job, an agent
  * run), else the current one — and `ds`/`queryDs` are proxies that take each connection from that generation's pool.
  * So read `deps.cfg` where it is used (a `def`, never a `val` captured at construction), and once into a local where
  * one unit of work needs one consistent config. Work handed to another thread carries its generation with [[pins]].
  */
final class Deps(
    cfgNow: () => AppConfig,
    val ds: DataSource,
    val queryDs: DataSource,
    val maintenance: WriteGate,
    configFileNow: () => ConfigFileInfo,
    pluginsNow: () => Generation,
    /** Pins work to a generation; [[Pinning.none]] without a hot graph (a test's fixed `Deps`). */
    val pins: Pinning = Pinning.none,
    /** `histmersion config reload`: read the config file again and switch to it. */
    val reloader: () => ReloadReport = Deps.NoReload,
    /** A person's pause and cancel of the long background jobs this server runs. */
    val jobStops: com.histmersion.util.jobs.JobStops = com.histmersion.util.jobs.JobStops(),
    /** The load `cfg` came from: the settings file and the environment it was read with (Settings writes that file).
      */
    loadedNow: Option[() => LoadedConfig] = None,
    /** Ends this process so that the service it runs under starts it again; `None` where none does. */
    val restart: Option[() => Unit] = None,
    /** The time, the environment and the per-user paths: read once by the entry point, never globally. */
    val clock: Clock = Clock.systemUTC(),
    val environment: Environment = Environment.empty,
    val userPaths: UserPaths = UserPaths(Environment.empty, "")
):

  /** Transactions on the application role ([[ds]]). */
  val textFiles: com.histmersion.util.storage.TextFiles = new com.histmersion.util.storage.DiskTextFiles
  val stores: com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  val resources: com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  val jvmHome: com.histmersion.util.system.JvmHome = SystemContext.jvmHome()
  val transactor: Transactor = Transactor(ds)

  /** The work lists' kernel (ACQUIRE, TASKS, LOG over `queue_items`). */
  val queues: com.histmersion.domain.automation.worklists.QueueService =
    com.histmersion.domain.automation.worklists.QueueService(transactor)

  /** The work lists' operations as REST, MCP and the CLI reach them. */
  val workLists: com.histmersion.domain.automation.worklists.WorkListControllers =
    com.histmersion.domain.automation.worklists.WorkListControllers(queues, maintenance, clock)

  /** The sources' texts as the catalogue's evidence searches them (the library's). */
  lazy val passages: com.histmersion.domain.catalogue.evidence.passages.Passage.Source =
    com.histmersion.domain.library.sources.SourcePassages(transactor)

  /** The events related to what a filter selects, as the library reads the filter's sources. */
  lazy val eventSources: com.histmersion.domain.catalogue.events.EventSourceFilter =
    com.histmersion.domain.library.search.FilterEventSources()

  /** The server's files, each named place where today's settings put it. */
  lazy val storage: com.histmersion.util.storage.Storage =
    StorageLocations.storage(() => cfg, () => loaded, environment, userPaths)

  /** Connections of the read-only role ([[queryDs]]), for the `query` escape hatch alone. */
  val queryTransactor: Transactor = Transactor(queryDs)

  /** The config the calling thread sees (its pinned generation's, else the current one). */
  def cfg: AppConfig = cfgNow()

  /** The user config file `cfg` was read with (`about` shows it). */
  def configFile: ConfigFileInfo = configFileNow()

  /** The plugins' generation for this config: their registries and the report `about` and `/api/config` show.
    */
  def plugins: Generation = pluginsNow()

  /** The load the calling thread's config came from: its settings file and environment. */
  def loaded: LoadedConfig = loadedNow.fold(
    LoadedConfig(
      cfg,
      UserConfigFile(configFile.path.map(java.nio.file.Path.of(_)), configFile.loaded, configFile.unknownKeys)
    )
  )(_())

object Deps:
  /** A fixed `Deps` — one config, one pair of DataSources, nothing hot (tests, tools); its plugins are the built-in
    * media collections and event catalogues and no fetcher ([[ServerPlugins.offline]]).
    */
  def apply(
      cfg: AppConfig,
      ds: DataSource,
      queryDs: DataSource,
      maintenance: WriteGate = WriteGate(),
      configFile: ConfigFileInfo = ConfigFileInfo(),
      plugins: Generation = ServerPlugins.offline,
      clock: Clock = Clock.systemUTC()
  ): Deps =
    new Deps(() => cfg, ds, queryDs, maintenance, () => configFile, () => plugins, clock = clock)

  /** A fixed `Deps` whose read-only DataSource is the application one: for a test that does not exercise `query` (a
    * second role would need a second pool someone has to own and close).
    */
  def apply(cfg: AppConfig, ds: DataSource): Deps = apply(cfg, ds, ds)

  /** Every owner's data contribution, in the order every operation runs the owners in: the domains bottom up, personal,
    * then the supporting areas.
    */
  lazy val data: com.histmersion.util.data.DataContributions =
    com.histmersion.util.data.DataContributions
      .of(
        List(
          com.histmersion.domain.automation.AutomationData,
          com.histmersion.domain.catalogue.CatalogueData,
          com.histmersion.domain.media.MediaData,
          com.histmersion.domain.library.LibraryData,
          com.histmersion.domain.research.ResearchData,
          com.histmersion.domain.personal.PersonalData,
          com.histmersion.util.data.DataManagementData,
          com.histmersion.util.jobs.JobsData,
          com.histmersion.util.identity.IdentityData,
          com.histmersion.util.database.DatabaseData
        )
      )
      .fold(problems => throw IllegalStateException(problems.mkString("; ")), identity)

  /** Every domain that names sources, in the order a rename reports their columns: what each keeps right when the
    * library renames or deletes a source.
    */
  val sourceReferrers: com.histmersion.domain.library.sources.referrers.SourceReferrers =
    com.histmersion.domain.library.sources.referrers.SourceReferrers(
      List(
        com.histmersion.domain.catalogue.evidence.AttestationSourceReferrer(),
        com.histmersion.domain.media.links.AssetLinkSourceReferrer(),
        com.histmersion.domain.research.articles.ArticleSourceReferrer(),
        com.histmersion.domain.catalogue.events.EventSourceReferrer(),
        com.histmersion.util.jobs.JobSourceReferrer(),
        com.histmersion.domain.catalogue.places.PlaceSourceReferrer(),
        com.histmersion.domain.automation.goals.GoalSourceReferrer(),
        com.histmersion.domain.automation.worklists.WorkListSourceReferrer()
      )
    )

  /** Every alias table, each registered by the kind's owner in the order of the kinds: how an old identifier of each
    * renamed kind is followed.
    */
  val aliasTables: com.histmersion.util.identity.AliasTables =
    com.histmersion.util.identity.AliasTables(
      List(
        com.histmersion.domain.library.sources.SourceAliasTable.table,
        com.histmersion.domain.research.articles.ArticleAliasTable.table,
        com.histmersion.domain.catalogue.events.EventAliasTable.table,
        com.histmersion.domain.automation.goals.GoalAliasTable.table,
        com.histmersion.domain.catalogue.places.PlaceAliasTable.table
      )
    )

  /** The reload of a server that was started without a config file to read again. */
  val NoReload: () => ReloadReport = () =>
    ReloadReport(
      published = false,
      generation = 0,
      errors = List("this server was started without a config file to reload")
    )
