package com.histmersion.util.jobs

import com.histmersion.config.AppConfig
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*

import java.time.Instant
import javax.sql.DataSource

class JobRunsDbSuite extends JobRunsContract("db"):
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val scratch = ScratchDatabases("histmersion_s7j", AppConfig.load().db.copy(url = testDb.get))
  private val made = java.util.concurrent.atomic.AtomicInteger(0)
  private val sources = collection.concurrent.TrieMap.empty[JobRuns, DataSource]
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  protected def fresh(test: String): Option[(JobRuns, Instant)] =
    testDb.map { _ =>
      val ds = scratch(made.incrementAndGet().toString)
      val r = JobRuns(Transactor(ds))
      sources.update(r, ds)
      (r, Instant.now())
    }

  protected def row(runs: JobRuns, id: String): (String, Option[String], Option[String], Option[String]) =
    connect(sources(runs))(
      sql"SELECT status, error, progress, origin FROM jobs WHERE id = $id"
        .query[(String, Option[String], Option[String], Option[String])]
        .run()
        .head
    )
