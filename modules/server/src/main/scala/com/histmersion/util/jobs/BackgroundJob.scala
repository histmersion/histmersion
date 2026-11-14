package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.util.database.Transactor

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure

/** The ONE way the server runs work longer than a request as a background job (#1946): a `jobs` row (`histmersion jobs
  * <id>` follows it; a restart fails it as `interrupted` like every other unfinished job), on its own daemon thread, at
  * most one at a time per [[Slot]]. The vacuum, the init import (and the `places load` it runs) and the event catalogue
  * runs each spelled this out themselves — and the places fetch, land and hierarchy builds were bare threads a person
  * could only follow in the server log.
  *
  * A job's `body` is given its row and a progress reporter, and answers its [[Outcome]]: a summary, and why it failed
  * when it did. An exception, an interruption included, fails the job with its message; the slot is freed whatever
  * happens.
  *
  * Adopting it (the media import, #1946's follow-up): keep a `Slot`, and replace the hand-written `startJob` with
  * `BackgroundJob.start(ds, kind, Some(ImportJobKinds.Media(Some(describe))), slot, "a media run")((job, progress) =>
  * Outcome.of(…))`, inside `maintenance.guard(kind)` when it writes.
  */
object BackgroundJob:
  type Work = Work.T
  object Work extends Newtype[String, Pure]

  /** How a job ended: its summary (the finished job's progress line) and, when it failed, why. */

  final case class Outcome(summary: Option[Outcome.Summary], error: Option[Problem] = None):
    def failed: Boolean = error.isDefined

  object Outcome:

    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]

    def done(summary: Summary): Outcome = Outcome(Some(summary))
    def failed(why: Problem): Outcome = Outcome(None, Some(why))

    /** The `Right(summary)` / `Left(why)` the catalogue and gazetteer runs answer. */
    def of(r: Either[Problem, Summary]): Outcome = r.fold(failed, done)

  /** One kind of work that runs one job at a time. `current` names the running one. `held` may be shared with work of
    * the same kind that is not a job (the media import's one-place runs).
    */
  final class Slot(held: AtomicReference[Option[Work]] = AtomicReference[Option[Work]](None)):
    def isRunning: Boolean = held.get().isDefined
    def current: Option[Work] = held.get()
    private[BackgroundJob] def claim(what: Work): Boolean = held.compareAndSet(None, Some(what))
    private[BackgroundJob] def note(what: Work): Unit = held.set(Some(what))
    private[BackgroundJob] def free(): Unit = held.set(None)

  /** The answer for a job asked for while its slot is held: names the running one. Pure. */
  def busy(what: Work, current: Option[Work], code: Option[Api.Error.Code] = None): Api.Error =
    Api.Error(
      Problem.Unclassified(
        Legacy.Text(s"$what is already running (${current.fold("?")(_.value)}) — see `histmersion jobs`")
      ),
      code
    )

  /** What a thrown exception tells a job's error line. Pure. */
  def describe(e: Throwable): Problem = e match
    case _: InterruptedException => Problem.Unclassified(Legacy.Text("interrupted"))
    case _                       => Problem.Unclassified(Legacy.Text(s"${e.getClass.getSimpleName}: ${e.getMessage}"))

  /** How a run of a job's body ended, for its row: done or failed with its outcome, or stopped by a person (a job whose
    * body ended by itself after a stop was asked ends as it ended). Pure.
    */
  def ending(out: Outcome, thrown: Option[JobStop], asked: Option[JobStop]): Either[JobStop, Outcome] =
    thrown match
      case Some(stop)         => Left(stop)
      case None if out.failed => asked.toLeft(out)
      case None               => Right(out)

  /** Change the detail the job `id` records. */
  def amendDetail(ds: DataSource, id: Job.Id)(change: JobDetail => JobDetail): Unit =
    Transactor(ds)
      .read(JobRepo.get(id))
      .foreach(j => Transactor(ds).transact(JobRepo.setDetail(id, change(j.detail.getOrElse(JobDetail.empty)))))

  /** Start `body` as a job of `kind` (with `detail` stored on its row) in `slot`; `Left` — naming the running one —
    * when the slot is held. `what` names this kind of work in that refusal ("a vacuum job"), `busyCode` is its error
    * code. Answers the job as it was queued: its thread may have moved it on already, which the caller follows by id.
    *
    * With `stops`, a person may pause or cancel the job: its progress report — which a long job makes between its parts
    * — throws [[JobStopped]] once a stop is asked, the row is left `paused` (keeping what it had done) or `cancelled`,
    * and a paused job resumes on the same row by running `body` again, which skips the parts already done (each such
    * job is resumable by parts).
    */
  def start(
      ds: DataSource,
      kind: Job.Kind,
      detail: Option[JobDetail],
      slot: Slot,
      what: Work,
      busyCode: Option[Api.Error.Code] = None,
      /** The job runs on the generation (database, config) the caller is pinned to, even across a config reload
        * (docs/research-plugins.md §3.6).
        */
      pins: WorkCarrier = WorkCarrier.none,
      stops: Option[JobStops] = None
  )(body: (JobInfo, JobInfo.Progress => Unit) => Outcome): Either[Api.Error, JobInfo] =
    if !slot.claim(Work("starting")) then Left(busy(what, slot.current, busyCode))
    else
      val job =
        try Transactor(ds).transact(JobRepo.create(kind, None, None, detail = detail))
        catch
          case e: Throwable =>
            slot.free()
            throw e
      launch(ds, job, kind, slot, what, busyCode, pins, stops, JobState.Queued, body)
      Right(Transactor(ds).read(JobRepo.get(idOf(job))).getOrElse(job))

  /** A job row's id, as the database keeps it. */
  private def idOf(job: JobInfo): Job.Id = Job.Id.assume(job.id.value)

  /** The job's body on its own daemon thread, its row moved from `from` to running and on to how it ended. The slot is
    * held by the caller and freed when the run ends.
    */
  private def launch(
      ds: DataSource,
      job: JobInfo,
      kind: Job.Kind,
      slot: Slot,
      what: Work,
      busyCode: Option[Api.Error.Code],
      pins: WorkCarrier,
      stops: Option[JobStops],
      from: JobState,
      body: (JobInfo, JobInfo.Progress => Unit) => Outcome
  ): Unit =
    val id = idOf(job)
    slot.note(Work(s"job ${job.id}"))
    val work = pins.carryWork { () =>
      val last = AtomicReference[Option[JobInfo.Progress]](None)
      // a report closes a part: what it says is kept, and a stop asked meanwhile takes effect there
      def report(text: JobInfo.Progress): Unit =
        last.set(Some(text))
        Transactor(ds).transact(JobRepo.progress(id, JobRow.Progress(text.value)))
        stops.flatMap(_.requestedFor(id)).foreach(stop => throw JobStopped(stop))
      try
        val _ =
          Transactor(ds).transact(
            JobRepo.transition(
              id,
              from,
              JobState.Running,
              last.get.orElse(Some(JobInfo.Progress("starting"))).map(p => JobRow.Progress(p.value))
            )
          )
        val (out, thrown) =
          try (body(job, report), None)
          catch
            case s: JobStopped => (Outcome.failed(Problem.Unclassified(Legacy.Text("stopped"))), Some(s.stop))
            case e: Throwable  =>
              if !e.isInstanceOf[InterruptedException] then
                scribe.error(s"$kind job ${job.id} died: ${e.getMessage}", e)
              (Outcome.failed(describe(e)), None)
        val asked = stops.flatMap(_.requestedFor(id))
        stops.foreach(_.answered(id))
        val _ = ending(out, thrown, asked) match
          case Right(o) =>
            o.error match
              case None =>
                Transactor(ds).transact(
                  JobRepo.transition(id, JobState.Running, JobState.Done, o.summary.map(m => JobRow.Progress(m.value)))
                )
              case Some(e) =>
                Transactor(ds).transact(
                  JobRepo.transition(
                    id,
                    JobState.Running,
                    JobState.Failed,
                    o.summary.map(m => JobRow.Progress(m.value)),
                    Some(e)
                  )
                )
          case Left(JobStop.Pause) =>
            stops.foreach(_.pausedWith(id, () => resume(ds, job, kind, slot, what, busyCode, pins, stops, body)))
            Transactor(ds).transact(
              JobRepo.transition(
                id,
                JobState.Running,
                JobState.Paused,
                Some(JobRow.Progress(JobControlRules.stoppedLine(JobStop.Pause, last.get).value))
              )
            )
          case Left(JobStop.Cancel) =>
            Transactor(ds).transact(
              JobRepo.transition(
                id,
                JobState.Running,
                JobState.Cancelled,
                Some(JobRow.Progress(JobControlRules.stoppedLine(JobStop.Cancel, last.get).value)),
                Some(Problem.Unclassified(Legacy.Text(JobControlRules.CancelledByPerson)))
              )
            )
      finally slot.free()
    }
    val t = Thread.ofPlatform().daemon(true).name(s"histmersion-$kind").unstarted(() => work())
    t.start()

  /** A paused job runs again on its row, in its slot; `Left` when the slot is held by another job now. */
  private def resume(
      ds: DataSource,
      job: JobInfo,
      kind: Job.Kind,
      slot: Slot,
      what: Work,
      busyCode: Option[Api.Error.Code],
      pins: WorkCarrier,
      stops: Option[JobStops],
      body: (JobInfo, JobInfo.Progress => Unit) => Outcome
  ): Either[Problem, Unit] =
    if !slot.claim(Work("resuming")) then
      // the paused job keeps its way to go on: it can be resumed once the other one ends
      stops.foreach(_.pausedWith(idOf(job), () => resume(ds, job, kind, slot, what, busyCode, pins, stops, body)))
      Left(busy(what, slot.current, busyCode).message)
    else
      launch(ds, job, kind, slot, what, busyCode, pins, stops, JobState.Paused, body)
      Right(())
