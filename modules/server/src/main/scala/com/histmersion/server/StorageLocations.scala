package com.histmersion.server

import com.histmersion.config.{AppConfig, Environment, LoadedConfig, UserPaths}
import com.histmersion.domain.automation.agents.AgentTrees
import com.histmersion.config.edit.SettingsFile
import com.histmersion.util.settings.StoredSettingsFile
import com.histmersion.util.storage.{Location, Storage, Store}

import java.nio.file.Path

/** Where each storage location lives, worked out from today's settings at each call — so the directories are where they
  * always were, and a reload that moves one is followed.
  */
object StorageLocations:

  /** The directory of `location` under `cfg`; `None` where the configuration places none. */
  def root(
      location: Location,
      cfg: AppConfig,
      loaded: LoadedConfig,
      environment: Environment,
      userPaths: UserPaths
  ): Option[Path] =
    val env = environment.lookup
    def dataDir = UserPaths.dataDir(cfg.runtime.dataDir, env, userPaths.osName)
    location match
      case Location.IngestOriginals   => Some(Path.of(cfg.converter.spoolDir.value))
      case Location.Spool             => Some(spool(cfg.acquire.spoolDir))
      case Location.Basemap           => Some(UserPaths.basemapDir(UserPaths.BasemapDir(cfg.basemap.dir.value), com.histmersion.config.RuntimeConfig.DataDir(cfg.init.dataDir.value), env))
      case Location.Backups           => Some(Path.of(cfg.backups.dir.value).toAbsolutePath.normalize)
      case Location.Bundles           => Some(Path.of(cfg.bundles.dir.value))
      case Location.GazetteerExtracts => Some(GazetteerPaths.of(cfg, env, userPaths.osName).extracts)
      case Location.AgentHomes        => AgentTrees.root(cfg, env, userPaths.osName)
      case Location.Plugins           => dataDir.map(_.resolve("plugins"))
      case Location.RelinkKit         =>
        nonBlank(cfg.runtime.home.value).map(h => Path.of(h).resolve("kit"))
      case Location.Install    => nonBlank(cfg.runtime.home.value).map(Path.of(_))
      case Location.Logs       => dataDir.map(_.resolve("logs"))
      case Location.UserConfig =>
        loaded.file.path.orElse(userPaths.configFile).flatMap(p => Option(p.toAbsolutePath.getParent))
      case Location.Scratch         => Some(temp)
      case Location.DatabaseCluster => dataDir.map(_.resolve("pg"))
      case Location.CorpusFolder    => configured(cfg.corpus.dir.value, env)
      case Location.GazetteerFolder => GazetteerPaths.of(cfg, env, userPaths.osName).legacy
      case Location.GazetteerDrop   => Some(GazetteerPaths.of(cfg, env, userPaths.osName).lpf)
      case Location.GazetteerSeed   => Some(Path.of(cfg.init.dataDir.value).resolve("places"))
      case Location.Converter       =>
        nonBlank(cfg.runtime.home.value).map(h => Path.of(h).resolve("converter"))
      case Location.Toolchain      => dataDir.map(_.resolve("toolchain"))
      case Location.CodexSessions  => AgentTrees.codexHome(cfg, env, userPaths.osName)
      case Location.DataDirectory  => dataDir
      case Location.CacheDirectory => UserPaths.cacheDir(env)
      case Location.Postgres       =>
        nonBlank(cfg.runtime.home.value).map(h => Path.of(h).resolve("pg"))
      case Location.PostgresTools => nonBlank(cfg.backups.pgBinDir.value).map(Path.of(_))
      case Location.Executables   => None

  /** The directories of the executables' search path `path` (`PATH`), each a store, in order. */
  def searchPath(path: Option[Environment.VariablesValue]): List[Store] =
    path.toList
      .flatMap(_.value.split(java.io.File.pathSeparatorChar).toList)
      .filter(_.nonEmpty)
      .map(d => Storage.at(Location.Executables, Path.of(d)))

  /** A configured directory: `None` when the setting is empty, a leading `~` the environment's `HOME`. */
  private def configured(s: String, env: UserPaths.Lookup): Option[Path] =
    nonBlank(s).map(p => Path.of(AgentTrees.expandIn(p, env)))

  private def nonBlank(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)

  /** The install's own places About › Storage shows beside the owners' stores: no owner's data. */
  val install: List[Location] =
    List(Location.Install, Location.Converter, Location.RelinkKit, Location.Toolchain, Location.Plugins, Location.Logs)

  /** Where About › Storage measures each location: found from `cfg` and the environment `env` the way the code that
    * writes there finds it, absolute and normalised. A directory only a setting names is measured only when the setting
    * is made; the bundled database's files and the install only in a bundle (`runtime.enabled`, or the executable in a
    * bundle's layout when `HISTMERSION_RUNTIME` is not set).
    */
  def measured(cfg: AppConfig, env: UserPaths.Lookup, osName: UserPaths.OsName, exe: Option[Path]): Storage =
    val bundled = cfg.runtime.enabled.value || (env(Environment.VariablesKey("HISTMERSION_RUNTIME")).forall(_.value.trim.isEmpty) && Supervisor.bundled(exe))
    val home = Option.when(bundled)(Supervisor.layout(cfg.runtime, exe, env, osName).toOption.map(_.home)).flatten
    val data = UserPaths.dataDir(cfg.runtime.dataDir, env, osName).map(_.toAbsolutePath.normalize)
    val gazetteer = GazetteerPaths.of(cfg, env, osName)
    def place(location: Location): Option[Path] = location match
      case Location.DatabaseCluster   => data.filter(_ => bundled).map(_.resolve("pg"))
      case Location.Backups           => Some(Path.of(cfg.backups.dir.value))
      case Location.IngestOriginals   => configured(cfg.converter.spoolDir.value, env)
      case Location.Spool             => configured(cfg.acquire.spoolDir.value, env)
      case Location.CorpusFolder      => configured(cfg.corpus.dir.value, env)
      case Location.Bundles           => configured(cfg.bundles.dir.value, env)
      case Location.GazetteerExtracts => Some(gazetteer.extracts)
      case Location.GazetteerFolder   => gazetteer.legacy
      case Location.GazetteerDrop     => Some(gazetteer.lpf)
      case Location.Basemap           => Some(UserPaths.basemapDir(UserPaths.BasemapDir(cfg.basemap.dir.value), com.histmersion.config.RuntimeConfig.DataDir(cfg.init.dataDir.value), env))
      case Location.Install           => home
      case Location.Converter         => home.map(_.resolve("converter"))
      case Location.RelinkKit         => home.map(_.resolve("kit"))
      case Location.Toolchain         => data.map(_.resolve("toolchain"))
      case Location.Plugins           => data.map(_.resolve("plugins"))
      case Location.AgentHomes        => AgentTrees.root(cfg, env, osName)
      case Location.CodexSessions     => AgentTrees.codexHome(cfg, env, osName)
      case Location.Logs              => data.map(_.resolve("logs"))
      case Location.DataDirectory     => data
      case Location.CacheDirectory    => UserPaths.cacheDir(env)
      case Location.UserConfig | Location.Postgres | Location.PostgresTools | Location.Executables |
          Location.GazetteerSeed =>
        None
      case Location.Scratch => Some(temp)
    Storage(l => place(l).map(_.toAbsolutePath.normalize), () => temp)

  /** The settings file `loaded` was read from, in the store of its directory ([[Location.UserConfig]]) and named in
    * messages as the load names it; `None` when it was read from none.
    */
  def settingsFile(loaded: LoadedConfig, storage: Storage): Option[SettingsFile] =
    loaded.file.path.flatMap(p =>
      Store.Key
        .of(p.getFileName.toString)
        .toOption
        .map(StoredSettingsFile(storage.store(Location.UserConfig), _, SettingsFile.Shown(p.toString)))
    )

  /** `acquire.spool-dir`, or `<java.io.tmpdir>/histmersion-acquire` when it is empty — absolute and normalised. */
  def spool(dir: com.histmersion.config.AcquireConfig.SpoolDir): Path =
    nonBlank(dir.value)
      .map(Path.of(_))
      .getOrElse(temp.resolve("histmersion-acquire"))
      .toAbsolutePath
      .normalize

  /** The JVM's temporary directory: what a container gets for scratch. */
  def temp: Path = Path.of(System.getProperty("java.io.tmpdir"))

  /** The server's storage over the configuration `cfg` returns at each call. */
  def storage(
      cfg: () => AppConfig,
      loaded: () => LoadedConfig,
      environment: Environment,
      userPaths: UserPaths
  ): Storage =
    Storage(l => root(l, cfg(), loaded(), environment, userPaths), () => temp)
