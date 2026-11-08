package com.histmersion
package util.jobs

import com.histmersion.api.Problem
import com.histmersion.config.AppConfig
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*

import com.histmersion.domain.automation.Run

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig

class JobRunsDbSuite extends JobRunsContract("db"):
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val scratch =
    ScratchDatabases("histmersion_s7j", AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
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

  protected def row(
      runs: JobRuns,
      id: Run.Id
  ): (RunRow.Status, Option[Problem], Option[JobRunState.Progress], Option[RunRow.Origin]) =
    val (status, error, progress, origin) = connect(sources(runs))(
      sql"SELECT status, error, progress, origin FROM jobs WHERE id = ${id.value}"
        .query[(String, Option[String], Option[String], Option[String])]
        .run()
        .head
    )
    (
      RunRow.Status(status),
      error.map(Problem(_)),
      progress.map(JobRunState.Progress(_)),
      origin.map(RunRow.Origin(_))
    )
