package com.histmersion.server

import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.process.Processes

/** The backups of a server: its backups store, the PostgreSQL client tools of `backups.pg-bin-dir` or else of the
  * `PATH` of the environment the entry point read, and the registry's guarded tables on its database.
  */
object BackupWiring:
  def service(deps: Deps): BackupService =
    BackupService(
      () => deps.cfg,
      deps.storage,
      Processes(),
      () => StorageLocations.searchPath(
          deps.environment.get(com.histmersion.config.Environment.VariablesKey("PATH")).map(v => StorageLocations.SearchPathPath(v.value))
        ),
      deps.transactor,
      Deps.data
    )
