package com.histmersion.util.data.backup

import com.histmersion.api.{ApiError, BackupResult, BackupStatus}
import com.histmersion.util.jobs.WorkCarrier

import java.time.Instant
import java.util.concurrent.{CompletableFuture, TimeoutException, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

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

  final private case class Job(file: String, started: Long, result: CompletableFuture[Either[ApiError, BackupResult]])
  private val current = AtomicReference[Option[Job]](None)

  def running: Boolean = current.get().exists(!_.result.isDone)

  /** Start a dump (or join the running one) and wait up to `waitSeconds` for it. */
  def start(now: Instant): Either[ApiError, BackupStatus] =
    val job = current.get() match
      case Some(j) if !j.result.isDone => Right(j)
      case _                           =>
        service.prepare(now).map { p =>
          val f = CompletableFuture[Either[ApiError, BackupResult]]()
          val j = Job(p.file, System.nanoTime(), f)
          current.set(Some(j))
          // the dump runs on the generation (database) the request was pinned to
          val work = pins.carryWork { () =>
            val _ = f.complete(
              scala.util.Try(p.run()).toEither.left.map(e => ApiError(s"backup crashed: ${e.getMessage}")).flatten
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
  def status: Option[BackupStatus] = current.get().map(statusOf)

  private def statusOf(j: Job): BackupStatus =
    val elapsed = (System.nanoTime() - j.started) / 1000000L
    if !j.result.isDone then BackupStatus.running(j.file, elapsed)
    else
      j.result.get() match
        case Right(r) => BackupStatus.done(r)
        case Left(e)  => BackupStatus.failed(j.file, elapsed, e.message)
