package com.histmersion.db

import com.histmersion.config.AppConfig

/** The schema version without a Flyway instance (#880), against a real Postgres (HISTMERSION_TEST_DB_URL); skipped
  * otherwise.
  */
class MigrateCurrentSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  /** The scribe records Flyway logs on THIS thread while `f` runs (other suites migrate concurrently). */
  private def flywayLogs(f: => Unit): List[String] =
    val buf = java.util.concurrent.ConcurrentLinkedQueue[scribe.LogRecord]()
    val me = Thread.currentThread()
    val prev = scribe.Logger.root
    val _ = prev
      .withHandler(new scribe.handler.LogHandler:
        def log(r: scribe.LogRecord): Unit = { val _ = buf.add(r) })
      .replace()
    try f
    finally { val _ = prev.replace() }
    import scala.jdk.CollectionConverters.*
    buf.asScala.toList.filter(r => r.thread == me && r.className.startsWith("org.flywaydb")).map(_.logOutput.plainText)

  test("the schema version is Flyway's, read without building Flyway: a health check logs nothing from it"):
    val ds0 = ds
    val logs = flywayLogs((1 to 3).foreach(_ => Migrate.current(ds0)))
    assertEquals(logs, Nil)
    val flyway = org.flywaydb.core.Flyway.configure().dataSource(ds0).load()
    assertEquals(Migrate.current(ds0), Option(flyway.info().current()).map(_.getVersion.toString))
    assertEquals(Migrate.current(ds0), Migrate.migrationNames.lastOption.map(_.drop(1).takeWhile(_ != '_')))
