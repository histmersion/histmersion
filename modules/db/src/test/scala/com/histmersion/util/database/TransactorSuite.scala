package com.histmersion
package util.database

import com.augustnagro.magnum.sql
import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.util.database.TestPermits.given

class TransactorSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val db = Transactor(testPool(AppConfig.load().db.copy(url = TestDb.dbUrl.get)))

  override def beforeAll(): Unit =
    if testDb.isDefined then
      db.write(sql"CREATE TABLE IF NOT EXISTS transactor_suite (n int NOT NULL)".update.run())
      db.write(sql"DELETE FROM transactor_suite".update.run())

  override protected def afterSuite(): Unit =
    if testDb.isDefined then db.write(sql"DROP TABLE IF EXISTS transactor_suite".update.run())

  private def count(): Int = db.read(sql"SELECT count(*)::int FROM transactor_suite".query[Int].run().head)

  test("the writes of a transaction are seen together once it ends"):
    db.write {
      sql"INSERT INTO transactor_suite VALUES (1)".update.run()
      sql"INSERT INTO transactor_suite VALUES (2)".update.run()
    }
    assertEquals(count(), 2)

  test("an exception rolls every write of its transaction back"):
    val before = count()
    intercept[IllegalStateException](db.write {
      sql"INSERT INTO transactor_suite VALUES (3)".update.run()
      throw IllegalStateException("refused")
    })
    assertEquals(count(), before)

  test("a read to completion interrupted mid-statement finishes the statement, then is interrupted; its session lives"):
    val started = java.util.concurrent.CountDownLatch(1)
    val backend = java.util.concurrent.atomic.AtomicReference[Option[Int]](None)
    val answered = java.util.concurrent.atomic.AtomicReference[Option[Int]](None)
    val interrupted = java.util.concurrent.atomic.AtomicReference[Option[InterruptedException]](None)
    val reader = Thread.ofVirtual().start { () =>
      try
        db.readToCompletion {
          backend.set(sql"SELECT pg_backend_pid()".query[Int].run().headOption)
          started.countDown()
          answered.set(sql"SELECT 7 FROM pg_sleep(0.5)".query[Int].run().headOption)
        }
      catch case e: InterruptedException => interrupted.set(Some(e))
    }
    started.await()
    reader.interrupt()
    reader.join()
    assert(interrupted.get.isDefined, "the reader's thread learns of its interrupt")
    assertEquals(answered.get, Some(7))
    val pid = backend.get.getOrElse(fail("no backend pid"))
    assertEquals(db.read(sql"SELECT count(*)::int FROM pg_stat_activity WHERE pid = $pid".query[Int].run().head), 1)
