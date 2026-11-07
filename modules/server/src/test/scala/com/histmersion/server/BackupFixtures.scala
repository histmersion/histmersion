package com.histmersion.server

import com.histmersion.config.{AppConfig, Environment, LoadedConfig, UserConfigFile, UserPaths}
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.database.Transactor
import com.histmersion.util.process.Processes

/** The backups of a server over `cfg` with no database behind them (nothing here counts guarded rows). */
object BackupFixtures:
  def service(cfg: AppConfig): BackupService =
    BackupService(
      () => cfg,
      StorageLocations.storage(
        () => cfg,
        () => LoadedConfig(cfg, UserConfigFile(None, loaded = com.histmersion.config.UserConfigFile.Loaded(false))),
        Environment.empty,
        UserPaths(Environment.empty, com.histmersion.config.UserPaths.OsName(""))
      ),
      Processes(),
      () => StorageLocations.searchPath(Option(System.getenv("PATH"))),
      Transactor(org.postgresql.ds.PGSimpleDataSource()),
      Deps.data
    )
