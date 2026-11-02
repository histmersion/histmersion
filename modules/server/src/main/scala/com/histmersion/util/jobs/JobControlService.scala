package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.api.{ApiError, Write}
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** Pause, resume and cancel a job: a long background job cooperatively (it stops at its next part boundary, keeping
  * what it did; resumed, it skips that), an agent run by its own control.
  */
trait JobControlService:
  /** Answer `action` on job `id`, with the job as it stands after it. A pause or cancel of a running background job is
    * asked of it: the answer is still `running`, and the job stops after its current part.
    */
  def control(id: String, action: JobAction): Either[ApiError, JobInfo]

object JobControlService:

  private def notFound(id: String) = ApiError(s"No job '$id'", Some(Write.CodeNotFound))
  private def conflict(why: String) = ApiError(why, Some(com.histmersion.api.jobs.JobActions.CodeConflict))

  /** Over the jobs table; `runs` is the agent runs' own pause, resume and cancel. */
  def apply(ds: DataSource, stops: JobStops, runs: (String, JobAction) => Either[ApiError, Unit]): JobControlService =
    Implementation(ds, stops, runs)

  final private class Implementation(
      ds: DataSource,
      stops: JobStops,
      runs: (String, JobAction) => Either[ApiError, Unit]
  ) extends JobControlService:

    def control(id: String, action: JobAction): Either[ApiError, JobInfo] =
      def current = Transactor(ds).read(JobRepo.get(id)).toRight(notFound(id))
      current.flatMap { job =>
        JobControlRules.decide(job, action, stops.canResume(id)).left.map(conflict).flatMap {
          case JobControlStep.Run(a)         => runs(id, a).flatMap(_ => current)
          case JobControlStep.RequestStop(s) => stops.request(id, s); current
          case JobControlStep.Resume         => stops.resume(id).left.map(conflict).flatMap(_ => current)
          case JobControlStep.CancelNow      =>
            stops.forget(id)
            Transactor(ds)
              .transact(
                JobRepo
                  .transition(id, job.state, JobState.Cancelled, job.progress, Some(JobControlRules.CancelledByPerson))
              )
              .toRight(conflict(s"job $id moved on; look again"))
              .flatMap(_ => current)
        }
      }

  /** The same answers over jobs held in memory (tests). A running job's body is the test's: it asks
    * [[JobStops.requestedFor]] between its parts and reports how it stopped with [[stopped]].
    */
  final class InMemory(
      jobs: AtomicReference[Map[String, JobInfo]],
      stops: JobStops,
      runs: (String, JobAction) => Either[ApiError, Unit] = (_, _) => Right(())
  ) extends JobControlService:

    /** A job, as the store would hold it. */
    def put(job: JobInfo): Unit = jobs.updateAndGet(_ + (job.id -> job)): Unit

    def get(id: String): Option[JobInfo] = jobs.get.get(id)

    /** A running job's body stopped as asked, after `progress`: paused (resumed by `resume`) or cancelled. */
    def stopped(id: String, progress: Option[String], resume: () => Either[String, Unit]): Unit =
      stops.requestedFor(id).foreach { stop =>
        stops.answered(id)
        val to = if stop == JobStop.Pause then JobState.Paused else JobState.Cancelled
        if stop == JobStop.Pause then stops.pausedWith(id, () => { move(id, JobState.Running, None); resume() })
        move(id, to, Some(JobControlRules.stoppedLine(stop, progress)))
      }

    private def move(id: String, to: JobState, progress: Option[String]): Unit =
      jobs.updateAndGet(m =>
        m.get(id)
          .fold(m)(j =>
            m + (id -> j.copy(
              status = to.wire,
              progress = progress.orElse(j.progress),
              error = Option.when(to == JobState.Cancelled)(JobControlRules.CancelledByPerson).orElse(j.error)
            ))
          )
      ): Unit

    def control(id: String, action: JobAction): Either[ApiError, JobInfo] =
      def current = get(id).toRight(notFound(id))
      current.flatMap { job =>
        JobControlRules.decide(job, action, stops.canResume(id)).left.map(conflict).flatMap {
          case JobControlStep.Run(a)         => runs(id, a).flatMap(_ => current)
          case JobControlStep.RequestStop(s) => stops.request(id, s); current
          case JobControlStep.Resume         => stops.resume(id).left.map(conflict).flatMap(_ => current)
          case JobControlStep.CancelNow      =>
            if JobState.next(job.state, JobState.Cancelled).isEmpty then Left(conflict(s"job $id moved on; look again"))
            else
              stops.forget(id)
              move(id, JobState.Cancelled, None)
              current
        }
      }
