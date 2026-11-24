package com.histmersion
package server

import com.histmersion.config.{AppConfig, Environment, LoadedConfig, UserConfigFile, UserPaths}
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.database.Transactor
import com.histmersion.util.process.Processes
import com.histmersion.lint.{LintRule, tolerated}

/** The backups of a server over `cfg` with no database behind them (nothing here counts guarded rows). */
object BackupFixtures:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def service(cfg: AppConfig): BackupService =
    BackupService(
      () => cfg,
      StorageLocations.storage(
        () => cfg,
        () => LoadedConfig(cfg, UserConfigFile(None, loaded = UserConfigFile.Loaded(false))),
        Environment.empty,
        UserPaths(Environment.empty, UserPaths.OsName(""))
      ),
      Processes(),
      () => StorageLocations.searchPath(Option(System.getenv("PATH")).map(Environment.VariablesValue(_))),
      Transactor(org.postgresql.ds.PGSimpleDataSource()),
      Deps.data
    )
