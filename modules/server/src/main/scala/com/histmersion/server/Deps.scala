package com.histmersion
package server

import com.histmersion.util.data.maintenance.WriteGate

import com.histmersion.pluginhost.Generation

import com.histmersion.config.{AppConfig, Environment, LoadedConfig, UserConfigFile, UserPaths}
import com.histmersion.api.settings.{ConfigFileInfo, ReloadReport}
import com.histmersion.util.jobs.Pinning
import com.histmersion.util.database.Transactor
import java.time.Clock
import javax.sql.DataSource
import com.histmersion.util.jobs.JobStops
import com.histmersion.util.time.Timer
import com.histmersion.util.time.SystemTimer
import com.histmersion.util.time.Pause
import com.histmersion.util.time.ThreadPause
import com.histmersion.util.storage.TextFiles
import com.histmersion.util.storage.DiskTextFiles
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.catalogue.evidence.passages.Passage
import com.histmersion.domain.library.sources.SourcePassages
import com.histmersion.domain.catalogue.events.EventSourceFilter
import com.histmersion.domain.library.search.FilterEventSources
import com.histmersion.util.storage.Storage
import com.histmersion.util.data.DataContributions
import com.histmersion.domain.automation.AutomationData
import com.histmersion.domain.catalogue.CatalogueData
import com.histmersion.domain.media.MediaData
import com.histmersion.domain.library.LibraryData
import com.histmersion.domain.research.ResearchData
import com.histmersion.domain.personal.PersonalData
import com.histmersion.util.data.DataManagementData
import com.histmersion.util.jobs.JobsData
import com.histmersion.util.identity.IdentityData
import com.histmersion.util.database.DatabaseData
import com.histmersion.util.log.{LogData, LogFiles, LogWriter}
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.domain.catalogue.evidence.AttestationSourceReferrer
import com.histmersion.domain.media.links.AssetLinkSourceReferrer
import com.histmersion.domain.research.articles.ArticleSourceReferrer
import com.histmersion.domain.catalogue.events.EventSourceReferrer
import com.histmersion.util.jobs.JobSourceReferrer
import com.histmersion.domain.catalogue.places.PlaceSourceReferrer
import com.histmersion.domain.automation.goals.GoalSourceReferrer
import com.histmersion.domain.automation.worklists.WorkListSourceReferrer
import com.histmersion.util.identity.AliasTables
import com.histmersion.domain.library.sources.SourceAliasTable
import com.histmersion.domain.research.articles.ArticleAliasTable
import com.histmersion.domain.catalogue.events.EventAliasTable
import com.histmersion.domain.automation.goals.GoalAliasTable
import com.histmersion.domain.catalogue.places.PlaceAliasTable
import com.histmersion.server.instance.WebBundle

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
    val jobStops: JobStops = JobStops(),
    /** The load `cfg` came from: the settings file and the environment it was read with (Settings writes that file).
      */
    loadedNow: Option[() => LoadedConfig] = None,
    /** Ends this process so that the service it runs under starts it again; `None` where none does. */
    val restart: Option[() => Unit] = None,
    /** The time, the environment and the per-user paths: read once by the entry point, never globally. */
    val clock: Clock = Clock.systemUTC(),
    val environment: Environment = Environment.empty,
    val userPaths: UserPaths = UserPaths(Environment.empty, UserPaths.OsName("")),
    val timer: Timer = new SystemTimer,
    val pause: Pause = new ThreadPause,
    /** The machine's physical memory ([[SystemContext.memory]]), which the bundled Postgres' `auto` sizes come from. */
    val memory: Option[ByteCount] = None
):

  /** Transactions on the application role ([[ds]]). */
  val textFiles: TextFiles = new DiskTextFiles
  val stores: Stores = Stores.disk
  val resources: Resources = Resources.classpath(getClass.getClassLoader)
  val jvmHome: JvmHome = SystemContext.jvmHome()
  val transactor: Transactor = Transactor(ds)

  lazy val webBundle: WebBundle = WebBundle.read(resources)

  /** The work lists' kernel (ACQUIRE, TASKS, LOG over `queue_items`). */
  val queues: QueueService =
    QueueService(transactor)

  /** The work lists' operations as REST, MCP and the CLI reach them. */
  val workLists: WorkListControllers =
    WorkListControllers(queues, maintenance, clock)

  /** The sources' texts as the catalogue's evidence searches them (the library's). */
  lazy val passages: Passage.Source =
    SourcePassages(transactor)(using timer)

  /** The events related to what a filter selects, as the library reads the filter's sources. */
  lazy val eventSources: EventSourceFilter =
    FilterEventSources()

  /** The structured log's rolling files, in `log.dir` (empty: the server's logs directory's `structured`). */
  lazy val logFiles: LogFiles =
    LogFiles(() => StorageLocations.logDir(cfg, loaded, environment, userPaths))

  /** The structured log's one writer: the sink of every trace scope ([[TracedPinning]] gives work that starts without
    * one a scope of its own). Its own connections are told no trace: it runs in none, and the log's tables record no
    * writes. The composition starts and stops its thread.
    */
  lazy val logWriter: LogWriter = LogWriter(() => cfg.log, logFiles, transactor, maintenance.permits, clock)

  /** The server's files, each named place where today's settings put it. */
  lazy val storage: Storage =
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
      UserConfigFile(
        configFile.path.map(p => java.nio.file.Path.of(p.value)),
        UserConfigFile.Loaded(configFile.loaded.value),
        configFile.unknownKeys.map(k => UserConfigFile.UnknownKeys(k.value))
      )
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
      clock: Clock = Clock.systemUTC(),
      timer: Timer = new SystemTimer,
      pause: Pause = new ThreadPause
  ): Deps =
    new Deps(
      () => cfg,
      // behind the maintenance gate, as the server's are: a restore lets no other thread have a connection
      maintenance.permits.dataSource(ds),
      maintenance.permits.dataSource(queryDs),
      maintenance,
      () => configFile,
      () => plugins,
      clock = clock,
      timer = timer,
      pause = pause
    )

  /** A fixed `Deps` whose read-only DataSource is the application one: for a test that does not exercise `query` (a
    * second role would need a second pool someone has to own and close).
    */
  def apply(cfg: AppConfig, ds: DataSource): Deps = apply(cfg, ds, ds)

  /** Every owner's data contribution, in the order every operation runs the owners in: the domains bottom up, personal,
    * then the supporting areas.
    */
  lazy val data: DataContributions =
    DataContributions
      .of(
        List(
          AutomationData,
          CatalogueData,
          MediaData,
          LibraryData(sourceReferrers),
          ResearchData,
          PersonalData,
          DataManagementData,
          JobsData,
          IdentityData,
          LogData,
          DatabaseData
        )
      )
      .fold(problems => throw IllegalStateException(problems.mkString("; ")), identity)

  /** Every domain that names sources, in the order a rename reports their columns: what each keeps right when the
    * library renames or deletes a source.
    */
  val sourceReferrers: SourceReferrers =
    SourceReferrers(
      List(
        AttestationSourceReferrer(),
        AssetLinkSourceReferrer(),
        ArticleSourceReferrer(),
        EventSourceReferrer(),
        JobSourceReferrer(),
        PlaceSourceReferrer(),
        GoalSourceReferrer(),
        WorkListSourceReferrer()
      )
    )

  /** Every alias table, each registered by the kind's owner in the order of the kinds: how an old identifier of each
    * renamed kind is followed.
    */
  val aliasTables: AliasTables =
    AliasTables(
      List(
        SourceAliasTable.table,
        ArticleAliasTable.table,
        EventAliasTable.table,
        GoalAliasTable.table,
        PlaceAliasTable.table
      )
    )

  /** The reload of a server that was started without a config file to read again. */
  val NoReload: () => ReloadReport = () =>
    ReloadReport(
      published = ReloadReport.Published(false),
      generation = ReloadReport.Generation(0),
      errors = List(ReloadReport.Errors("this server was started without a config file to reload"))
    )
