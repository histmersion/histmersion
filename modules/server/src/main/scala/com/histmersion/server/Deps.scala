package com.histmersion.server

import com.histmersion.core.{AppConfig, ConfigFileInfo}
import javax.sql.DataSource

/** Everything the handlers need. Built once in `Main` (the composition root) and passed down — no
  * object-initialisation side effects anywhere below this.
  *
  * TWO DataSources, deliberately. `ds` is the application role: it owns the schema and every repo
  * goes through it. `queryDs` is the `histmersion_query` read-only role (migration V011) and is used by
  * exactly ONE operation — the `query` escape hatch — because that is the one place arbitrary,
  * model-written SQL is executed. Mixing them up is the bug the second field exists to make visible.
  */
final case class Deps(cfg: AppConfig, ds: DataSource, queryDs: DataSource,
    maintenance: Maintenance = Maintenance(),
    /** The user config file `cfg` was read with (`about` shows it). */
    configFile: ConfigFileInfo = ConfigFileInfo())

object Deps:
  /** The ordinary construction: the read-only DataSource comes from `histmersion.query.user/password`.
    * A test that does not exercise `query` can use this and never think about the second role. */
  def apply(cfg: AppConfig, ds: DataSource): Deps =
    Deps(cfg, ds, com.histmersion.db.Db.queryDataSource(cfg.db, cfg.query))
