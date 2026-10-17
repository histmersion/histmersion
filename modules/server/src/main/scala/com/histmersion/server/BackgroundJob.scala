package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.JobRepo

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** The ONE way the server runs work longer than a request as a background job (#1946): a `jobs` row
  * (`histmersion jobs <id>` follows it; a restart fails it as `interrupted` like every other unfinished
  * job), on its own daemon thread, at most one at a time per [[Slot]]. The vacuum, the init import, the
  * event catalogue runs and the gazetteer fetches each spelled this out themselves — and the places
  * fetch, land and hierarchy builds were bare threads a person could only follow in the server log.
  *
  * A job's `body` is given its row and a progress reporter, and answers its [[Outcome]]: a
  * summary, and why it failed when it did. An exception, an interruption included, fails the job with
  * its message; the slot is freed whatever happens.
  *
  * Adopting it (the media import, #1946's follow-up): keep a `Slot`, and replace the hand-written
  * `startJob` with `BackgroundJob.start(ds, kind, Some(JobDetail(media = Some(describe))), slot,
  * "a media run")((job, progress) => Outcome.of(…))`, inside `maintenance.guard(kind)` when it writes. */
object BackgroundJob:

  /** How a job ended: its summary (the finished job's progress line) and, when it failed, why. */
  final case class Outcome(summary: Option[String], error: Option[String] = None):
    def failed: Boolean = error.isDefined

  object Outcome:
    def done(summary: String): Outcome = Outcome(Some(summary))
    def failed(why: String): Outcome   = Outcome(None, Some(why))
    /** The `Right(summary)` / `Left(why)` the catalogue and gazetteer runs answer. */
    def of(r: Either[String, String]): Outcome = r.fold(failed, done)

  /** One kind of work that runs one job at a time. `current` names the running one. */
  final class Slot:
    private val held = AtomicReference[Option[String]](None)
    def isRunning: Boolean          = held.get().isDefined
    def current: Option[String]     = held.get()
    private[BackgroundJob] def claim(what: String): Boolean = held.compareAndSet(None, Some(what))
    private[BackgroundJob] def note(what: String): Unit     = held.set(Some(what))
    private[BackgroundJob] def free(): Unit                 = held.set(None)

  /** The answer for a job asked for while its slot is held: names the running one. Pure. */
  def busy(what: String, current: Option[String], code: Option[String] = None): ApiError =
    ApiError(s"$what is already running (${current.getOrElse("?")}) — see `histmersion jobs`", code)

  /** What a thrown exception tells a job's error line. Pure. */
  def describe(e: Throwable): String = e match
    case _: InterruptedException => "interrupted"
    case _                        => s"${e.getClass.getSimpleName}: ${e.getMessage}"

  /** Start `body` as a job of `kind` (with `detail` stored on its row) in `slot`; `Left` — naming the
    * running one — when the slot is held. `what` names this kind of work in that refusal ("a vacuum
    * job"), `busyCode` is its error code. Answers the job as it stands once its thread is started. */
  def start(ds: DataSource, kind: String, detail: Option[JobDetail], slot: Slot, what: String,
      busyCode: Option[String] = None)(body: (JobInfo, String => Unit) => Outcome): Either[ApiError, JobInfo] =
    if !slot.claim("starting") then Left(busy(what, slot.current, busyCode))
    else
      val job =
        try JobRepo.create(ds, kind, None, None, detail = detail)
        catch
          case e: Throwable =>
            slot.free()
            throw e
      slot.note(s"job ${job.id}")
      val t = Thread.ofPlatform().daemon(true).name(s"histmersion-$kind").unstarted { () =>
        try
          val _ = JobRepo.transition(ds, job.id, JobState.Queued, JobState.Running, Some("starting"))
          val out =
            try body(job, p => JobRepo.progress(ds, job.id, p))
            catch
              case e: Throwable =>
                if !e.isInstanceOf[InterruptedException] then scribe.error(s"$kind job ${job.id} died: ${e.getMessage}", e)
                Outcome.failed(describe(e))
          val _ = out.error match
            case None    => JobRepo.transition(ds, job.id, JobState.Running, JobState.Done, out.summary)
            case Some(e) => JobRepo.transition(ds, job.id, JobState.Running, JobState.Failed, out.summary, Some(e))
        finally slot.free()
      }
      t.start()
      Right(JobRepo.get(ds, job.id).getOrElse(job))
