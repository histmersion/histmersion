package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.Api

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
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Work = Work.T
  object Work extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How a job ended: its summary (the finished job's progress line) and, when it failed, why. */

  type BusyCode = BusyCode.T
  object BusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBusyCode = StartBusyCode.T
  object StartBusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaunchWhat = _root_.com.histmersion.util.jobs.BackgroundJob.Work
  val LaunchWhat: _root_.com.histmersion.util.jobs.BackgroundJob.Work.type = _root_.com.histmersion.util.jobs.BackgroundJob.Work
  type LaunchBusyCode = LaunchBusyCode.T
  object LaunchBusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeKind = _root_.com.histmersion.util.jobs.Job.Kind
  val ResumeKind: _root_.com.histmersion.util.jobs.Job.Kind.type = _root_.com.histmersion.util.jobs.Job.Kind
  type ResumeWhat = _root_.com.histmersion.util.jobs.BackgroundJob.Work
  val ResumeWhat: _root_.com.histmersion.util.jobs.BackgroundJob.Work.type = _root_.com.histmersion.util.jobs.BackgroundJob.Work
  type ResumeBusyCode = _root_.com.histmersion.util.jobs.BackgroundJob.LaunchBusyCode
  val ResumeBusyCode: _root_.com.histmersion.util.jobs.BackgroundJob.LaunchBusyCode.type = _root_.com.histmersion.util.jobs.BackgroundJob.LaunchBusyCode

  final case class Outcome(summary: Option[Outcome.Summary], error: Option[Outcome.Error] = None):
    def failed: Boolean = error.isDefined

  object Outcome:

    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def done(summary: com.histmersion.util.jobs.BackgroundJob.Outcome.Summary): Outcome = Outcome(Some(summary))
    def failed(why: com.histmersion.util.jobs.BackgroundJob.Outcome.Error): Outcome = Outcome(None, Some(why))

    /** The `Right(summary)` / `Left(why)` the catalogue and gazetteer runs answer. */
    def of(r: Either[com.histmersion.util.jobs.BackgroundJob.Outcome.Error, com.histmersion.util.jobs.BackgroundJob.Outcome.Summary]): Outcome = r.fold(failed, done)

  /** One kind of work that runs one job at a time. `current` names the running one. `held` may be shared with work of
    * the same kind that is not a job (the media import's one-place runs).
    */
  final class Slot(held: AtomicReference[Option[Slot.Held]] = AtomicReference[Option[String]](None)):
    def isRunning: Boolean = held.get().isDefined
    def current: Option[com.histmersion.util.jobs.BackgroundJob.Slot.Held] = held.get()
    private[BackgroundJob] def claim(what: com.histmersion.util.jobs.BackgroundJob.Work): Boolean = held.compareAndSet(None, Some(what))
    private[BackgroundJob] def note(what: com.histmersion.util.jobs.BackgroundJob.Work): Unit = held.set(Some(what))
    private[BackgroundJob] def free(): Unit = held.set(None)

  object Slot:
    type Held = Held.T
    object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  /** The answer for a job asked for while its slot is held: names the running one. Pure. */
  def busy(what: com.histmersion.util.jobs.BackgroundJob.Work, current: Option[com.histmersion.util.jobs.BackgroundJob.Slot.Held], code: Option[com.histmersion.util.jobs.BackgroundJob.BusyCode] = None): Api.Error =
    Api.Error(s"$what is already running (${current.getOrElse("?")}) — see `histmersion jobs`", code)

  /** What a thrown exception tells a job's error line. Pure. */
  def describe(e: Throwable): com.histmersion.util.jobs.BackgroundJob.Problem = e match
    case _: InterruptedException => "interrupted"
    case _                       => s"${e.getClass.getSimpleName}: ${e.getMessage}"

  /** How a run of a job's body ended, for its row: done or failed with its outcome, or stopped by a person (a job whose
    * body ended by itself after a stop was asked ends as it ended). Pure.
    */
  def ending(out: Outcome, thrown: Option[JobStop], asked: Option[JobStop]): Either[JobStop, Outcome] =
    thrown match
      case Some(stop)         => Left(stop)
      case None if out.failed => asked.toLeft(out)
      case None               => Right(out)

  /** Change the detail the job `id` records. */
  def amendDetail(ds: DataSource, id: com.histmersion.util.jobs.Job.Id)(change: JobDetail => JobDetail): Unit =
    Transactor(ds)
      .read(JobRepo.get(id))
      .foreach(j => Transactor(ds).transact(JobRepo.setDetail(id, change(j.detail.getOrElse(JobDetail.empty)))))

  /** Start `body` as a job of `kind` (with `detail` stored on its row) in `slot`; `Left` — naming the running one —
    * when the slot is held. `what` names this kind of work in that refusal ("a vacuum job"), `busyCode` is its error
    * code. Answers the job as it stands once its thread is started.
    *
    * With `stops`, a person may pause or cancel the job: its progress report — which a long job makes between its parts
    * — throws [[JobStopped]] once a stop is asked, the row is left `paused` (keeping what it had done) or `cancelled`,
    * and a paused job resumes on the same row by running `body` again, which skips the parts already done (each such
    * job is resumable by parts).
    */
  def start(
      ds: DataSource,
      kind: com.histmersion.util.jobs.Job.Kind,
      detail: Option[JobDetail],
      slot: Slot,
      what: com.histmersion.util.jobs.BackgroundJob.Work,
      busyCode: Option[com.histmersion.util.jobs.BackgroundJob.StartBusyCode] = None,
      /** The job runs on the generation (database, config) the caller is pinned to, even across a config reload
        * (docs/research-plugins.md §3.6).
        */
      pins: WorkCarrier = WorkCarrier.none,
      stops: Option[JobStops] = None
  )(body: (JobInfo, com.histmersion.util.jobs.Job.Id => Unit) => Outcome): Either[Api.Error, JobInfo] =
    if !slot.claim("starting") then Left(busy(what, slot.current, busyCode))
    else
      val job =
        try Transactor(ds).transact(JobRepo.create(kind, None, None, detail = detail))
        catch
          case e: Throwable =>
            slot.free()
            throw e
      launch(ds, job, kind, slot, what, busyCode, pins, stops, JobState.Queued, body)
      Right(Transactor(ds).read(JobRepo.get(job.id)).getOrElse(job))

  /** The job's body on its own daemon thread, its row moved from `from` to running and on to how it ended. The slot is
    * held by the caller and freed when the run ends.
    */
  private def launch(
      ds: DataSource,
      job: JobInfo,
      kind: com.histmersion.util.jobs.Job.Kind,
      slot: Slot,
      what: com.histmersion.util.jobs.BackgroundJob.LaunchWhat,
      busyCode: Option[com.histmersion.util.jobs.BackgroundJob.LaunchBusyCode],
      pins: WorkCarrier,
      stops: Option[JobStops],
      from: JobState,
      body: (JobInfo, com.histmersion.util.jobs.Job.Id => Unit) => Outcome
  ): Unit =
    slot.note(s"job ${job.id}")
    val work = pins.carryWork { () =>
      val last = AtomicReference[Option[String]](None)
      // a report closes a part: what it says is kept, and a stop asked meanwhile takes effect there
      def report(text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit =
        last.set(Some(text))
        Transactor(ds).transact(JobRepo.progress(job.id, text))
        stops.flatMap(_.requestedFor(job.id)).foreach(stop => throw JobStopped(stop))
      try
        val _ =
          Transactor(ds).transact(JobRepo.transition(job.id, from, JobState.Running, last.get.orElse(Some("starting"))))
        val (out, thrown) =
          try (body(job, report), None)
          catch
            case s: JobStopped => (Outcome.failed("stopped"), Some(s.stop))
            case e: Throwable  =>
              if !e.isInstanceOf[InterruptedException] then
                scribe.error(s"$kind job ${job.id} died: ${e.getMessage}", e)
              (Outcome.failed(describe(e)), None)
        val asked = stops.flatMap(_.requestedFor(job.id))
        stops.foreach(_.answered(job.id))
        val _ = ending(out, thrown, asked) match
          case Right(o) =>
            o.error match
              case None =>
                Transactor(ds).transact(JobRepo.transition(job.id, JobState.Running, JobState.Done, o.summary))
              case Some(e) =>
                Transactor(ds).transact(
                  JobRepo.transition(job.id, JobState.Running, JobState.Failed, o.summary, Some(e))
                )
          case Left(JobStop.Pause) =>
            stops.foreach(_.pausedWith(job.id, () => resume(ds, job, kind, slot, what, busyCode, pins, stops, body)))
            Transactor(ds).transact(
              JobRepo.transition(
                job.id,
                JobState.Running,
                JobState.Paused,
                Some(JobControlRules.stoppedLine(JobStop.Pause, last.get))
              )
            )
          case Left(JobStop.Cancel) =>
            Transactor(ds).transact(
              JobRepo.transition(
                job.id,
                JobState.Running,
                JobState.Cancelled,
                Some(JobControlRules.stoppedLine(JobStop.Cancel, last.get)),
                Some(JobControlRules.CancelledByPerson)
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
      kind: com.histmersion.util.jobs.BackgroundJob.ResumeKind,
      slot: Slot,
      what: com.histmersion.util.jobs.BackgroundJob.ResumeWhat,
      busyCode: Option[com.histmersion.util.jobs.BackgroundJob.ResumeBusyCode],
      pins: WorkCarrier,
      stops: Option[JobStops],
      body: (JobInfo, com.histmersion.util.jobs.Job.Id => Unit) => Outcome
  ): Either[com.histmersion.util.jobs.BackgroundJob.Problem, Unit] =
    if !slot.claim("resuming") then
      // the paused job keeps its way to go on: it can be resumed once the other one ends
      stops.foreach(_.pausedWith(job.id, () => resume(ds, job, kind, slot, what, busyCode, pins, stops, body)))
      Left(busy(what, slot.current, busyCode).message)
    else
      launch(ds, job, kind, slot, what, busyCode, pins, stops, JobState.Paused, body)
      Right(())
