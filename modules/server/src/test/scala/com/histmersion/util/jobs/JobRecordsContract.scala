package com.histmersion.util.jobs

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.config.AppConfig

import java.time.Clock
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

/** The one-piece jobs' rows, the same on the database and in memory: created `queued`, moved by compare-and-set (a
  * second claim is refused), progress only while running, the error and progress of the last move kept.
  */
abstract class JobRecordsContract extends com.histmersion.db.DbSuite:
  protected def fresh(): Option[JobRecords]

  private def jobId(info: JobInfo): Job.Id = Job.Id.applyUnsafe(info.id.value)

  private def jtest(title: String)(body: JobRecords => Unit): Unit = test(title)(fresh().foreach(body))

  jtest("a job is created queued, claimed once, annotated while running, and done") { jobs =>
    val j = jobs.create(com.histmersion.util.jobs.Job.Kind("ingest"), Some(com.histmersion.domain.library.Citekey("zq_job")), Some(com.histmersion.Sha256.applyUnsafe("a" * 64)), Some(com.histmersion.util.jobs.JobRow.AcquireItem(7)))
    assertEquals(
      (j.kind, j.status, j.citekey, j.sha256, j.acquireItem),
      ("ingest", "queued", Some("zq_job"), Some("a" * 64), Some(7))
    )
    assertEquals(jobs.get(jobId(j)).map(_.status), Some("queued"))
    jobs.progress(jobId(j), com.histmersion.util.jobs.JobRow.Progress("not yet"))
    assertEquals(jobs.get(jobId(j)).flatMap(_.progress), None, "a queued job takes no progress")
    assertEquals(jobs.transition(jobId(j), JobState.Queued, JobState.Running, Some(com.histmersion.util.jobs.JobRow.Progress("converting"))), Some(JobState.Running))
    assertEquals(jobs.transition(jobId(j), JobState.Queued, JobState.Running), None, "a second claim is refused")
    jobs.progress(jobId(j), com.histmersion.util.jobs.JobRow.Progress("storing 1 asset(s)"))
    assertEquals(jobs.get(jobId(j)).flatMap(_.progress), Some("storing 1 asset(s)"))
    assertEquals(jobs.transition(jobId(j), JobState.Running, JobState.Done, Some(com.histmersion.util.jobs.JobRow.Progress("2 page(s)"))), Some(JobState.Done))
    val done: Option[JobInfo] = jobs.get(jobId(j))
    assertEquals(done.map(j => (j.status, j.progress, j.error)), Some(("done", Some("2 page(s)"), None)))
    assertEquals(jobs.get(Job.Id.applyUnsafe("00000000-0000-0000-0000-000000000000")), None)
  }

  jtest("a failed job keeps its error") { jobs =>
    val j = jobs.create(com.histmersion.util.jobs.Job.Kind("reocr"), None, None, None)
    val _ = jobs.transition(jobId(j), JobState.Queued, JobState.Running)
    assertEquals(
      jobs.transition(jobId(j), JobState.Running, JobState.Failed, error = Some(com.histmersion.util.jobs.JobRow.Error("offline"))),
      Some(JobState.Failed)
    )
    assertEquals(jobs.get(jobId(j)).map(j => (j.status, j.error)), Some(("failed", Some("offline"))))
  }

class JobRecordsInMemorySuite extends JobRecordsContract:
  protected def fresh(): Option[JobRecords] = Some(JobRecords.InMemory(AtomicReference(Map.empty), Clock.systemUTC()))

class JobRecordsDbSuite extends JobRecordsContract:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val scratch = ScratchDatabases("histmersion_s10j", AppConfig.load().db.copy(url = com.histmersion.config.DbConfig.Url(testDb.get)))
  private val made = AtomicLong(0L)
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
  protected def fresh(): Option[JobRecords] =
    testDb.map { _ =>
      val ds = scratch(made.incrementAndGet().toString)
      JobRecords(() => ds)
    }
