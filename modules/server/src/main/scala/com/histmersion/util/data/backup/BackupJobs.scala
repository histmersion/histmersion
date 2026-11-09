package com.histmersion
package util.data.backup
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.util.jobs.WorkCarrier

import java.time.Instant
import java.util.concurrent.{CompletableFuture, TimeoutException, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api
import com.histmersion.api.backup.Backup

/** Backups run as a JOB, not inside the HTTP request. A dump of the corpus takes minutes, and the request that started
  * it used to be cut at 60 s (by the server and by any proxy in between): the handler thread was interrupted and the
  * dump was lost a few KB before its end, every time. Now the dump runs on its own daemon thread; the request waits up
  * to `waitSeconds` and otherwise answers `running`, and `GET /api/backup/status` reports the job until it is done or
  * failed.
  *
  * One job at a time: a second `start` while one runs joins it rather than dumping twice.
  */
final class BackupJobs(
    service: BackupService,
    waitSeconds: => Int,
    pins: WorkCarrier = WorkCarrier.none
):

  final private case class Job(
      file: PreparedBackup.File,
      started: Long,
      result: CompletableFuture[Either[Api.Error, Backup.Result]]
  )
  private val current = AtomicReference[Option[Job]](None)

  def running: Boolean = current.get().exists(!_.result.isDone)

  /** Start a dump (or join the running one) and wait up to `waitSeconds` for it. */
  def start(now: Instant): Either[Api.Error, Backup.Status] =
    val job = current.get() match
      case Some(j) if !j.result.isDone => Right(j)
      case _                           =>
        service.prepare(now).map { p =>
          val f = CompletableFuture[Either[Api.Error, Backup.Result]]()
          val j = Job(p.file, System.nanoTime(), f)
          current.set(Some(j))
          // the dump runs on the generation (database) the request was pinned to
          val work = pins.carryWork { () =>
            val _ = f.complete(
              scala.util
                .Try(p.run())
                .toEither
                .left
                .map(e => Api.Error(Problem.Unclassified(Legacy.Text(s"backup crashed: ${e.getMessage}"))))
                .flatten
            )
          }
          val _ = Thread.ofPlatform().daemon(true).name("histmersion-backup").start(() => work())
          j
        }
    job.map { j =>
      try { val _ = j.result.get(math.max(0, waitSeconds).toLong, TimeUnit.SECONDS) }
      catch case _: TimeoutException => ()
      statusOf(j)
    }

  /** The running job, or the last one this server ran (`None` before the first). */
  def status: Option[Backup.Status] = current.get().map(statusOf)

  private def statusOf(j: Job): Backup.Status =
    val elapsed = Backup.Result.DurationMs((System.nanoTime() - j.started) / 1000000L)
    if !j.result.isDone then Backup.Status.running(Backup.Status.RunningFile(j.file.value), elapsed)
    else
      j.result.get() match
        case Right(r) => Backup.Status.done(r)
        case Left(e)  =>
          Backup.Status.failed(Backup.Status.FailedFile(j.file.value), elapsed, e.message)
