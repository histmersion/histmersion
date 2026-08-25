package histdb.server

import histdb.core.AppConfig
import javax.sql.DataSource

/** Everything the handlers need. Built once in `Main` (the composition root) and passed down — no
  * object-initialisation side effects anywhere below this.
  *
  * TWO DataSources, deliberately. `ds` is the application role: it owns the schema and every repo
  * goes through it. `queryDs` is the `histdb_query` read-only role (migration V011) and is used by
  * exactly ONE operation — the `query` escape hatch — because that is the one place arbitrary,
  * model-written SQL is executed. Mixing them up is the bug the second field exists to make visible.
  */
final case class Deps(cfg: AppConfig, ds: DataSource, queryDs: DataSource,
    maintenance: Maintenance = Maintenance())

object Deps:
  /** The ordinary construction: the read-only DataSource comes from `histdb.query.user/password`.
    * A test that does not exercise `query` can use this and never think about the second role. */
  def apply(cfg: AppConfig, ds: DataSource): Deps =
    Deps(cfg, ds, histdb.db.Db.queryDataSource(cfg.db, cfg.query))
