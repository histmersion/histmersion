package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.JobRepo

import com.histmersion.domain.jobs.{JobControlRules, JobStop, JobStopped, JobStops}

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** The ONE way the server runs work longer than a request as a background job (#1946): a `jobs` row
  * (`histmersion jobs <id>` follows it; a restart fails it as `interrupted` like every other unfinished
  * job), on its own daemon thread, at most one at a time per [[Slot]]. The vacuum, the init import (and
  * the `places load` it runs) and the event catalogue runs each spelled this out themselves — and the places
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

  /** One kind of work that runs one job at a time. `current` names the running one. `held` may be shared
    * with work of the same kind that is not a job (the media import's one-place runs). */
  final class Slot(held: AtomicReference[Option[String]] = AtomicReference[Option[String]](None)):
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

  /** How a run of a job's body ended, for its row: done or failed with its outcome, or stopped by a person
    * (a job whose body ended by itself after a stop was asked ends as it ended). Pure. */
  def ending(out: Outcome, thrown: Option[JobStop], asked: Option[JobStop]): Either[JobStop, Outcome] =
    thrown match
      case Some(stop)                => Left(stop)
      case None if out.failed        => asked.toLeft(out)
      case None                      => Right(out)

  /** Start `body` as a job of `kind` (with `detail` stored on its row) in `slot`; `Left` — naming the
    * running one — when the slot is held. `what` names this kind of work in that refusal ("a vacuum
    * job"), `busyCode` is its error code. Answers the job as it stands once its thread is started.
    *
    * With `stops`, a person may pause or cancel the job: its progress report — which a long job makes
    * between its parts — throws [[JobStopped]] once a stop is asked, the row is left `paused` (keeping what
    * it had done) or `cancelled`, and a paused job resumes on the same row by running `body` again, which
    * skips the parts already done (each such job is resumable by parts). */
  def start(ds: DataSource, kind: String, detail: Option[JobDetail], slot: Slot, what: String,
      busyCode: Option[String] = None,
      /** The job runs on the generation (database, config) the caller is pinned to, even across a
        * config reload (docs/research-plugins.md §3.6). */
      pins: com.histmersion.server.hot.Pinning = com.histmersion.server.hot.Pinning.none,
      stops: Option[JobStops] = None)(
      body: (JobInfo, String => Unit) => Outcome): Either[ApiError, JobInfo] =
    if !slot.claim("starting") then Left(busy(what, slot.current, busyCode))
    else
      val job =
        try JobRepo.create(ds, kind, None, None, detail = detail)
        catch
          case e: Throwable =>
            slot.free()
            throw e
      launch(ds, job, kind, slot, what, busyCode, pins, stops, JobState.Queued, body)
      Right(JobRepo.get(ds, job.id).getOrElse(job))

  /** The job's body on its own daemon thread, its row moved from `from` to running and on to how it ended. The
    * slot is held by the caller and freed when the run ends. */
  private def launch(ds: DataSource, job: JobInfo, kind: String, slot: Slot, what: String, busyCode: Option[String],
      pins: com.histmersion.server.hot.Pinning, stops: Option[JobStops], from: JobState,
      body: (JobInfo, String => Unit) => Outcome): Unit =
    slot.note(s"job ${job.id}")
    val work = pins.carry {
      val last = AtomicReference[Option[String]](None)
      // a report closes a part: what it says is kept, and a stop asked meanwhile takes effect there
      def report(text: String): Unit =
        last.set(Some(text))
        JobRepo.progress(ds, job.id, text)
        stops.flatMap(_.requestedFor(job.id)).foreach(stop => throw JobStopped(stop))
      try
        val _ = JobRepo.transition(ds, job.id, from, JobState.Running, last.get.orElse(Some("starting")))
        val (out, thrown) =
          try (body(job, report), None)
          catch
            case s: JobStopped => (Outcome.failed("stopped"), Some(s.stop))
            case e: Throwable =>
              if !e.isInstanceOf[InterruptedException] then scribe.error(s"$kind job ${job.id} died: ${e.getMessage}", e)
              (Outcome.failed(describe(e)), None)
        val asked = stops.flatMap(_.requestedFor(job.id))
        stops.foreach(_.answered(job.id))
        val _ = ending(out, thrown, asked) match
          case Right(o) => o.error match
            case None    => JobRepo.transition(ds, job.id, JobState.Running, JobState.Done, o.summary)
            case Some(e) => JobRepo.transition(ds, job.id, JobState.Running, JobState.Failed, o.summary, Some(e))
          case Left(JobStop.Pause) =>
            stops.foreach(_.pausedWith(job.id, () => resume(ds, job, kind, slot, what, busyCode, pins, stops, body)))
            JobRepo.transition(ds, job.id, JobState.Running, JobState.Paused,
              Some(JobControlRules.stoppedLine(JobStop.Pause, last.get)))
          case Left(JobStop.Cancel) =>
            JobRepo.transition(ds, job.id, JobState.Running, JobState.Cancelled,
              Some(JobControlRules.stoppedLine(JobStop.Cancel, last.get)), Some(JobControlRules.CancelledByPerson))
      finally slot.free()
    }
    val t = Thread.ofPlatform().daemon(true).name(s"histmersion-$kind").unstarted(() => work.run())
    t.start()

  /** A paused job runs again on its row, in its slot; `Left` when the slot is held by another job now. */
  private def resume(ds: DataSource, job: JobInfo, kind: String, slot: Slot, what: String, busyCode: Option[String],
      pins: com.histmersion.server.hot.Pinning, stops: Option[JobStops],
      body: (JobInfo, String => Unit) => Outcome): Either[String, Unit] =
    if !slot.claim("resuming") then
      // the paused job keeps its way to go on: it can be resumed once the other one ends
      stops.foreach(_.pausedWith(job.id, () => resume(ds, job, kind, slot, what, busyCode, pins, stops, body)))
      Left(busy(what, slot.current, busyCode).message)
    else
      launch(ds, job, kind, slot, what, busyCode, pins, stops, JobState.Paused, body)
      Right(())
