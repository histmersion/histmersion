package com.histmersion
package db

import hearth.kindlings.optics.syntax.*

import com.augustnagro.magnum.*

import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.domain.automation.autopilot.AutopilotRepo

import javax.sql.DataSource

/** The autopilot's one row: the migration makes it, a read only reads (it runs in a read-only transaction, where an
  * insert fails), a database without the row reads as the row the migration makes and writes nothing, and the first
  * write makes it again. In a migrated database of its own beside HISTMERSION_TEST_DB_URL's, dropped afterwards;
  * skipped without it.
  */
class AutopilotRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val scratch =
    ScratchDatabases("histmersion_autopilot", cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def rows(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM agent_autopilot".query[Int].run().head)

  /** `f` in a read-only transaction: any write it attempts fails. */
  private def readOnly[A](ds: DataSource)(f: DbCon ?=> A): A =
    transact(ds) {
      val _ = sql"SET TRANSACTION READ ONLY".update.run()
      f
    }

  test("the migration makes the row; a read of it writes nothing"):
    val ds = scratch("made")
    assertEquals(rows(ds), 1, "a migrated database has the one row")
    val row = readOnly(ds)(AutopilotRepo.get())
    assertEquals(s"${row.enabled}", "false")
    assert(row.paused.isEmpty)
    assert(readOnly(ds)(AutopilotRepo.strategy()).isEmpty)
    assert(readOnly(ds)(AutopilotRepo.lastDigest()).isEmpty)
    assert(readOnly(ds)(AutopilotRepo.drain()).isEmpty)

  test("without the row, every read gives the row the migration makes and leaves the table empty"):
    val ds = scratch("absent")
    connect(ds)(sql"DELETE FROM agent_autopilot".update.run(): Unit)
    val row = readOnly(ds)(AutopilotRepo.get())
    assertEquals(s"${row.enabled}|${row.changedBy}", "false|")
    assert(row.paused.isEmpty && row.lastEnsureAt.isEmpty && row.lastBackupAt.isEmpty && row.lastLintAt.isEmpty)
    assert(s"${row.changedAt}".matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), s"${row.changedAt}")
    assert(readOnly(ds)(AutopilotRepo.strategy()).isEmpty)
    assert(readOnly(ds)(AutopilotRepo.lastDigest()).isEmpty)
    assert(readOnly(ds)(AutopilotRepo.drain()).isEmpty)
    assertEquals(rows(ds), 0, "no read made the row")

  test("the first write makes the row again: switching, housekeeping, strategy and drain"):
    val ds = scratch("rewrite")
    def deleted(): Unit = connect(ds)(sql"DELETE FROM agent_autopilot".update.run(): Unit)
    val by = AutopilotRepo.Row.ChangedBy("zzautopilot")
    deleted()
    assert(transact(ds)(AutopilotRepo.set(AutopilotRepo.Row.Enabled(true), by, Nil)), "off → on is a change")
    assertEquals(rows(ds), 1)
    assertEquals(s"${connect(ds)(AutopilotRepo.get()).enabled}", "true")
    deleted()
    assert(!transact(ds)(AutopilotRepo.set(AutopilotRepo.Row.Enabled(false), by, Nil)), "off → off is none")
    assertEquals(rows(ds), 1)
    deleted()
    transact(ds)(AutopilotRepo.linted(java.time.Instant.parse("2026-11-17T10:00:00Z")))
    assertEquals(connect(ds)(AutopilotRepo.get()).lastLintAt.map(t => s"$t"), Some("2026-11-17T10:00:00Z"))
    deleted()
    transact(ds)(AutopilotRepo.setStrategy(Some(AutopilotRepo.StrategyName("round-robin")), by))
    assertEquals(connect(ds)(AutopilotRepo.strategy()).map(s => s"${s._1}"), Some("round-robin"))
    deleted()
    assert(transact(ds)(AutopilotRepo.setDrain(on = true, by)))
    assertEquals(connect(ds)(AutopilotRepo.drain()).map(d => s"${d._2}"), Some("zzautopilot"))
    deleted()
    transact(ds)(AutopilotRepo.digested(java.time.Instant.parse("2026-11-17T11:00:00Z")))
    assertEquals(connect(ds)(AutopilotRepo.lastDigest()).map(d => s"$d"), Some("2026-11-17T11:00:00Z"))
    assertEquals(rows(ds), 1)
