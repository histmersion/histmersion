package com.histmersion.util.database

import com.augustnagro.magnum.sql
import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.db.{DbSuite, TestDb}

class TransactorSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val db = Transactor(testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))))

  override def beforeAll(): Unit =
    if testDb.isDefined then
      db.transact(sql"CREATE TABLE IF NOT EXISTS transactor_suite (n int NOT NULL)".update.run())
      db.transact(sql"DELETE FROM transactor_suite".update.run())

  override protected def afterSuite(): Unit =
    if testDb.isDefined then db.transact(sql"DROP TABLE IF EXISTS transactor_suite".update.run())

  private def count(): Int = db.read(sql"SELECT count(*)::int FROM transactor_suite".query[Int].run().head)

  test("the writes of a transaction are seen together once it ends"):
    db.transact {
      sql"INSERT INTO transactor_suite VALUES (1)".update.run()
      sql"INSERT INTO transactor_suite VALUES (2)".update.run()
    }
    assertEquals(count(), 2)

  test("an exception rolls every write of its transaction back"):
    val before = count()
    intercept[IllegalStateException](db.transact {
      sql"INSERT INTO transactor_suite VALUES (3)".update.run()
      throw IllegalStateException("refused")
    })
    assertEquals(count(), before)
