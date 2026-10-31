package com.histmersion.server

import com.histmersion.config.{AppConfig, Environment, LoadedConfig, UserPaths}
import com.histmersion.server.agents.AgentTrees
import com.histmersion.util.basemap.BasemapService
import com.histmersion.server.importers.GazetteerLoad
import com.histmersion.util.storage.{Location, Storage}

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
      case Location.IngestOriginals   => Some(Path.of(cfg.converter.spoolDir))
      case Location.Spool             => Some(spool(cfg.acquire.spoolDir))
      case Location.Basemap           => Some(BasemapService.dir(cfg, env))
      case Location.Backups           => Some(Path.of(cfg.backups.dir))
      case Location.Bundles           => Some(Path.of(cfg.bundles.dir))
      case Location.GazetteerExtracts => Some(GazetteerLoad.paths(cfg, env).extracts)
      case Location.AgentHomes        => AgentTrees.root(cfg, env)
      case Location.Plugins           => dataDir.map(_.resolve("plugins"))
      case Location.RelinkKit         =>
        Option(cfg.runtime.home).map(_.trim).filter(_.nonEmpty).map(h => Path.of(h).resolve("kit"))
      case Location.UserConfig =>
        loaded.file.path.orElse(userPaths.configFile).flatMap(p => Option(p.toAbsolutePath.getParent))
      case Location.Scratch => Some(temp)

  /** `acquire.spool-dir`, or `<java.io.tmpdir>/histmersion-acquire` when it is empty — absolute and normalised. */
  def spool(dir: String): Path =
    Option(dir)
      .map(_.trim)
      .filter(_.nonEmpty)
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
