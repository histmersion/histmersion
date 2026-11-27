package com.histmersion
package server

import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.process.Processes
import com.histmersion.config.Environment
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The backups of a server: its backups store, the PostgreSQL client tools of `backups.pg-bin-dir` or else of the
  * `PATH` of the environment the entry point read, and the registry's guarded tables on its database.
  */
@movesTo(FeaturePlace.Server)
object BackupWiring:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def service(deps: Deps): BackupService =
    BackupService(
      () => deps.cfg,
      deps.storage,
      Processes(),
      () =>
        StorageLocations.searchPath(
          deps.environment.get(Environment.VariablesKey("PATH"))
        ),
      deps.transactor,
      Deps.data
    )
