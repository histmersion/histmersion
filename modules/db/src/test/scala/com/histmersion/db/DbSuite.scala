package com.histmersion.db

import com.histmersion.core.{DbConfig, QueryConfig}
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import scala.util.Try

/** A suite that owns the connection pools it opens: one per database and role it asks for
  * ([[testPool]], [[testQueryPool]]), opened on first use and closed when the suite is done — after
  * its own clean-up ([[afterSuite]]), which may still need them.
  *
  * There is no process-wide pool any more ([[Db]] builds a new one per call; docs/research-plugins.md
  * §3.6), so a pool a suite did not close would keep its connections until the JVM ends, and every
  * suite of a run shares the test database's `max_connections`. The suites run one at a time, so a
  * run holds one suite's pools (and those of the servers it starts, which close with their scope). */
abstract class DbSuite extends munit.FunSuite:
  private val owned = scala.collection.mutable.LinkedHashMap.empty[(String, String), HikariDataSource]

  /** The application pool on `cfg`'s database, this suite's own. */
  protected def testPool(cfg: DbConfig): DataSource = owned.synchronized {
    owned.getOrElseUpdate((cfg.url, cfg.user), Db.pool(cfg))
  }

  /** The read-only role's pool on `db`'s database, this suite's own. */
  protected def testQueryPool(db: DbConfig, q: QueryConfig): DataSource = owned.synchronized {
    owned.getOrElseUpdate((db.url, q.user), Db.queryPool(db, q))
  }

  /** The suite's own clean-up, run before its pools are closed (in place of `afterAll`). */
  protected def afterSuite(): Unit = ()

  final override def afterAll(): Unit =
    try afterSuite()
    finally closePools()

  /** Close every pool this suite opened (a suite that drops a database closes them first). */
  protected def closePools(): Unit = owned.synchronized {
    owned.values.foreach(p => Try(p.close()))
    owned.clear()
  }
