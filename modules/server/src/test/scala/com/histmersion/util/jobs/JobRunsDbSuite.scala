package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.config.AppConfig
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*

import com.histmersion.domain.automation.Run

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.lint.{LintRule, tolerated}

class JobRunsDbSuite extends JobRunsContract("db"):
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def row(
      runs: JobRuns,
      id: Run.Id
  ): (RunRow.Status, Option[Problem], Option[JobRunState.Progress], Option[RunRow.Origin]) =
    val (status, error, progress, origin) = connect(sources(runs))(
      sql"SELECT status, error, progress, origin FROM jobs WHERE id = $id"
        .query[(String, Option[NonEmptyVector[Problem]], Option[String], Option[String])]
        .run()
        .head
    )
    (
      RunRow.Status(status),
      error.map(Problems.asOne),
      progress.map(JobRunState.Progress(_)),
      origin.map(RunRow.Origin(_))
    )
