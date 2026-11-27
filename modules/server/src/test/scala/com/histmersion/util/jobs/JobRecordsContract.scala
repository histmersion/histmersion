package com.histmersion
package util.jobs
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.problem.{Legacy, Problem, Problems}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.config.AppConfig

import java.time.Clock
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import com.histmersion.db.DbSuite
import com.histmersion.domain.library.Citekey
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The one-piece jobs' rows, the same on the database and in memory: created `queued`, moved by compare-and-set (a
  * second claim is refused), progress only while running, the error and progress of the last move kept.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class JobRecordsContract extends DbSuite:
  protected def fresh(): Option[JobRecords]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jobId(info: JobInfo): Job.Id = Job.Id.applyUnsafe(info.id.value)

  private def jtest(title: String)(body: JobRecords => Unit): Unit = test(title)(fresh().foreach(body))

  jtest("a job is created queued, claimed once, annotated while running, and done") { jobs =>
    val j = jobs.create(
      Job.Kind("ingest"),
      Some(Citekey("zq_job")),
      Some(Sha256.applyUnsafe("a" * 64)),
      Some(JobRow.AcquireItem(7))
    )
    assertEquals(
      (j.kind, j.status, j.citekey, j.sha256, j.acquireItem),
      ("ingest", "queued", Some("zq_job"), Some("a" * 64), Some(7))
    )
    assertEquals(jobs.get(jobId(j)).map(_.status), Some("queued"))
    jobs.progress(jobId(j), JobRow.Progress("not yet"))
    assertEquals(jobs.get(jobId(j)).flatMap(_.progress), None, "a queued job takes no progress")
    assertEquals(
      jobs.transition(jobId(j), JobState.Queued, JobState.Running, Some(JobRow.Progress("converting"))),
      Some(JobState.Running)
    )
    assertEquals(jobs.transition(jobId(j), JobState.Queued, JobState.Running), None, "a second claim is refused")
    jobs.progress(jobId(j), JobRow.Progress("storing 1 asset(s)"))
    assertEquals(jobs.get(jobId(j)).flatMap(_.progress), Some("storing 1 asset(s)"))
    assertEquals(
      jobs.transition(jobId(j), JobState.Running, JobState.Done, Some(JobRow.Progress("2 page(s)"))),
      Some(JobState.Done)
    )
    val done: Option[JobInfo] = jobs.get(jobId(j))
    assertEquals(done.map(j => (j.status, j.progress, j.problems)), Some(("done", Some("2 page(s)"), None)))
    assertEquals(jobs.get(Job.Id.applyUnsafe("00000000-0000-0000-0000-000000000000")), None)
  }

  jtest("a failed job keeps its error") { jobs =>
    val j = jobs.create(Job.Kind("reocr"), None, None, None)
    val _ = jobs.transition(jobId(j), JobState.Queued, JobState.Running)
    assertEquals(
      jobs.transition(
        jobId(j),
        JobState.Running,
        JobState.Failed,
        error = Some(Problem.Unclassified(Legacy.Text("offline")))
      ),
      Some(JobState.Failed)
    )
    assertEquals(
      jobs.get(jobId(j)).map(j => (j.status, j.problems.map(_.sentences))),
      Some(("failed", Some(Vector("offline"))))
    )
  }

  jtest("a job that failed with several problems keeps every one, in order") { jobs =>
    val j = jobs.create(Job.Kind("reocr"), None, None, None)
    val _ = jobs.transition(jobId(j), JobState.Queued, JobState.Running)
    val all = Problems.of(Problem.Conflict.Busy(Problem.Conflict.Activity.Restore), Problem.Run.Interrupted)
    val _ = jobs.transition(jobId(j), JobState.Running, JobState.Failed, error = Some(all))
    assertEquals(jobs.get(jobId(j)).flatMap(_.problems), Some(all.toNonEmpty))
  }

class JobRecordsInMemorySuite extends JobRecordsContract:
  protected def fresh(): Option[JobRecords] = Some(JobRecords.InMemory(AtomicReference(Map.empty), Clock.systemUTC()))

class JobRecordsDbSuite extends JobRecordsContract:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val scratch =
    ScratchDatabases("histmersion_s10j", AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
  private val made = AtomicLong(0L)
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
  protected def fresh(): Option[JobRecords] =
    testDb.map { _ =>
      val ds = scratch(made.incrementAndGet().toString)
      JobRecords(() => ds)
    }
