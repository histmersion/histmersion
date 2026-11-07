package com.histmersion.domain.automation.worklists

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.*

class QueueServiceDbSuite extends QueueServiceContract("db"):
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val scratch = ScratchDatabases("histmersion_s7q", AppConfig.load().db.copy(url = testDb.get))
  private val made = java.util.concurrent.atomic.AtomicInteger(0)
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  protected def fresh(test: String): Option[QueueService] =
    testDb.map(_ => QueueService(Transactor(scratch(made.incrementAndGet().toString))))

  test("the CHECK constraint refuses a list name that is not one of the three"):
    val ds = scratch("check")
    intercept[Exception]:
      connect(ds)(sql"INSERT INTO queue_items (list, text) VALUES ('notes', 'x')".update.run())
