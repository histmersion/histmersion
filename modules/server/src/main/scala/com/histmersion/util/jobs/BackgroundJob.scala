package com.histmersion
package util.jobs
import com.histmersion.util.problem.ProblemLog
import com.histmersion.util.problem.{Legacy, Problem, Problems, Raised}

import com.histmersion.util.database.Transactor

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import scala.annotation.{nowarn, tailrec}
import scala.util.control.NonFatal

/** The ONE way the server runs work longer than a request as a background job (#1946): a `jobs` row (`histmersion jobs
  * <id>` follows it; a restart fails it as `interrupted` like every other unfinished job), on its own daemon thread, at
  * most one at a time per [[Slot]]. The vacuum, the init import (and the `places load` it runs) and the event catalogue
  * runs each spelled this out themselves — and the places fetch, land and hierarchy builds were bare threads a person
  * could only follow in the server log.
  *
  * A job's `body` is given its row and a progress reporter, and answers its [[Outcome]]: a summary, and why it failed
  * when it did. An exception, an interruption included, fails the job with its message; the slot is freed whatever
  * happens. The body runs only once its row moved to `running` (a job cancelled while it waited never runs), and how it
  * ended is written with a bounded retry; a failure to record it is logged with the outcome it could not record.
  *
  * The thread is started through the server's [[Workers]] as one unit of work: a restore waits for it, and fences it
  * off from the database when it outlasts the wait.
  *
  * Adopting it (the media import, #1946's follow-up): keep a `Slot`, and replace the hand-written `startJob` with
  * `BackgroundJob.start(ds, workers, kind, Some(ImportJobKinds.Media(Some(describe))), slot, "a media run")((job,
  * progress) => Outcome.of(…))`, inside `maintenance.guard(kind)` when it writes.
  */
object BackgroundJob:
  type Work = Work.T
  object Work extends Newtype[String, Pure]

  /** How a job ended: its summary (the finished job's progress line) and, when it failed, every problem why. */

  final case class Outcome(summary: Option[Outcome.Summary], error: Option[Problems] = None):
    def failed: Boolean = error.isDefined

  object Outcome:

    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]

    def done(summary: Summary): Outcome = Outcome(Some(summary))
    def failed(why: Problems): Outcome = Outcome(None, Some(why))

    /** The `Right(summary)` / `Left(every problem)` the catalogue and gazetteer runs answer. */
    def of(r: Either[Problems, Summary]): Outcome = r.fold(failed, done)

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

  /** What a thrown exception tells a job's error line: every problem it carries. Pure. */
  def describe(e: Throwable): Problems = e match
    case _: InterruptedException => Problem.Run.Interrupted
    case r: Raised               => r.problems
    case _                       => ProblemLog.unexpected(e, Problem.Where("a background job"))

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
      workers: Workers,
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
          // frees the slot and rethrows: every Throwable, or a fatal one would leave the slot claimed for good
          case e: (Throwable @nowarn("id=E235")) =>
            slot.free()
            throw e
      Launch(ds, workers, job, kind, slot, what, busyCode, pins, stops, body).run(JobState.Queued)
      // the row as created, not read again: by then its thread may have moved it on, and the answer would vary
      Right(job)

  /** How often how a job ended is tried before the failure to record it is logged, and how long between two tries. The
    * row stays `running` until the next start when none of them gets through.
    */
  private val EndingAttempts = 5
  private val EndingBackoffMs = 1000L

  /** One job's runs, the first one and every resume. */
  final private class Launch(
      ds: DataSource,
      workers: Workers,
      job: JobInfo,
      kind: Job.Kind,
      slot: Slot,
      what: Work,
      busyCode: Option[Api.Error.Code],
      pins: WorkCarrier,
      stops: Option[JobStops],
      body: (JobInfo, JobInfo.Progress => Unit) => Outcome
  ):
    /** The job row's id, as the database keeps it. */
    private val id: Job.Id = Job.Id.assume(job.id.value)
    private val db = Transactor(ds)

    /** The body on its own daemon thread, its row moved from `from` to running and on to how it ended. The slot is held
      * by the caller and freed when the run ends.
      */
    def run(from: JobState): Unit =
      slot.note(Work(s"job ${job.id}"))
      val work = pins.carryWork { () =>
        val last = AtomicReference[Option[JobInfo.Progress]](None)
        // a report closes a part: what it says is kept, and a stop asked meanwhile takes effect there
        def report(text: JobInfo.Progress): Unit =
          last.set(Some(text))
          db.transact(JobRepo.progress(id, JobRow.Progress(text.value)))
          stops.flatMap(_.requestedFor(id)).foreach(stop => throw JobStopped(stop))
        try
          val moved =
            db.transact(
              JobRepo.transition(
                id,
                from,
                JobState.Running,
                last.get.orElse(Some(JobInfo.Progress("starting"))).map(p => JobRow.Progress(p.value))
              )
            )
          // a job cancelled (or otherwise moved on) while it waited does not run: its row says what became of it
          if moved.isEmpty then notStarted(from)
          else ran(last, report)
        catch
          // the database failed before the body ran, or while its end was recorded: logged, the slot freed below
          case NonFatal(e) => val _ = ProblemLog.unexpected(e, Problem.Where(s"job ${job.id} ($kind)"))
        finally slot.free()
      }
      workers.detached(Quiescent.Name(s"$kind job ${job.id}"))(() => work())

    /** The row did not move to `running`: the body is not run, and the log says what the row is instead. */
    private def notStarted(from: JobState): Unit =
      val now =
        try db.read(JobRepo.get(id)).map(_.status)
        catch case NonFatal(_) => None
      scribe.warn(
        s"job ${job.id} ($kind) was not started: its row is ${now.fold("gone")(s => s"$s")}, not ${from.wire}"
      )

    /** Run the body and record how it ended. */
    private def ran(last: AtomicReference[Option[JobInfo.Progress]], report: JobInfo.Progress => Unit): Unit =
      val (out, thrown) =
        try (body(job, report), None)
        catch
          case s: JobStopped => (Outcome.failed(Problem.Run.Cancelled(Problem.Run.Actor.Person)), Some(s.stop))
          // an interrupt is the job's interruption (`describe`), and a deep recursion fails this job alone: its
          // stack is unwound here
          case e: InterruptedException => (Outcome.failed(describe(e)), None)
          case e: StackOverflowError   => (Outcome.failed(describe(e)), None)
          case NonFatal(e)             => (Outcome.failed(describe(e)), None)
      val asked = stops.flatMap(_.requestedFor(id))
      stops.foreach(_.answered(id))
      ending(out, thrown, asked) match
        case Right(o) =>
          o.error match
            case None =>
              recordEnding(JobState.Done)(
                db.transact(
                  JobRepo.transition(id, JobState.Running, JobState.Done, o.summary.map(m => JobRow.Progress(m.value)))
                )
              )
            case Some(e) =>
              recordEnding(JobState.Failed)(
                db.transact(
                  JobRepo.transition(
                    id,
                    JobState.Running,
                    JobState.Failed,
                    o.summary.map(m => JobRow.Progress(m.value)),
                    Some(e.toNonEmpty)
                  )
                )
              )
        case Left(JobStop.Pause) =>
          stops.foreach(_.pausedWith(id, () => resume()))
          recordEnding(JobState.Paused)(
            db.transact(
              JobRepo.transition(
                id,
                JobState.Running,
                JobState.Paused,
                Some(JobRow.Progress(JobControlRules.stoppedLine(JobStop.Pause, last.get).value))
              )
            )
          )
        case Left(JobStop.Cancel) =>
          recordEnding(JobState.Cancelled)(
            db.transact(
              JobRepo.transition(
                id,
                JobState.Running,
                JobState.Cancelled,
                Some(JobRow.Progress(JobControlRules.stoppedLine(JobStop.Cancel, last.get).value)),
                Some(Problem.Unclassified(Legacy.Text(JobControlRules.CancelledByPerson)).toNonEmpty)
              )
            )
          )

    /** Write that the job ended `as`, trying again when the database fails, up to [[EndingAttempts]] times. A row that
      * had moved on meanwhile is left as it is; a refusal of the maintenance gate (a restore runs, or replaced the
      * database the job wrote to) is not tried again, since the row it would write is gone; either is logged. When
      * every try fails, the failure is logged with the outcome it could not record.
      */
    private def recordEnding(as: JobState)(write: => Option[JobState]): Unit =
      @tailrec def attempt(left: Int): Unit =
        val tried =
          try Right(write)
          catch case NonFatal(e) => Left(e)
        tried match
          case Right(Some(_)) => ()
          case Right(None)    =>
            scribe.warn(s"job ${job.id} ($kind) ended ${as.wire}, but its row had moved on: left as it is")
          case Left(r: Raised) =>
            scribe.warn(s"job ${job.id} ($kind) ended ${as.wire}, which was not recorded: ${r.getMessage}")
          case Left(_) if left > 1 =>
            Thread.sleep(EndingBackoffMs)
            attempt(left - 1)
          case Left(e) =>
            val _ = ProblemLog.unexpected(
              e,
              Problem.Where(
                s"recording that job ${job.id} ($kind) ended ${as.wire}, after $EndingAttempts tries; the row stays " +
                  "as it was until the next start"
              )
            )
      attempt(EndingAttempts)

    /** A paused job runs again on its row, in its slot; `Left` when the slot is held by another job now. */
    private def resume(): Either[Problems, Unit] =
      if !slot.claim(Work("resuming")) then
        // the paused job keeps its way to go on: it can be resumed once the other one ends
        stops.foreach(_.pausedWith(id, () => resume()))
        Left(busy(what, slot.current, busyCode).problems)
      else
        run(JobState.Paused)
        Right(())
