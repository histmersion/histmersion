package com.histmersion
package util.jobs

import com.histmersion.api.Problem
import com.histmersion.util.database.Transactor

import com.histmersion.api.Write
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.api.jobs.JobActions

/** Pause, resume and cancel a job: a long background job cooperatively (it stops at its next part boundary, keeping
  * what it did; resumed, it skips that), an agent run by its own control.
  */
trait JobControlService:
  /** Answer `action` on job `id`, with the job as it stands after it. A pause or cancel of a running background job is
    * asked of it: the answer is still `running`, and the job stops after its current part.
    */
  def control(id: Job.Id, action: JobAction): Either[Api.Error, JobInfo]

object JobControlService:
  private def notFound(id: Job.Id) =
    Api.Error(Problem(s"No job '$id'"), Some(Api.Error.Code(Write.CodeNotFound)))
  private def conflict(why: String) =
    Api.Error(Problem(why), Some(Api.Error.Code(JobActions.CodeConflict)))

  /** Over the jobs table; `runs` is the agent runs' own pause, resume and cancel. */
  def apply(ds: DataSource, stops: JobStops, runs: (Job.Id, JobAction) => Either[Api.Error, Unit]): JobControlService =
    Implementation(ds, stops, runs)

  final private class Implementation(
      ds: DataSource,
      stops: JobStops,
      runs: (Job.Id, JobAction) => Either[Api.Error, Unit]
  ) extends JobControlService:

    def control(id: Job.Id, action: JobAction): Either[Api.Error, JobInfo] =
      def current = Transactor(ds).read(JobRepo.get(id)).toRight(notFound(id))
      current.flatMap { job =>
        JobControlRules.decide(job, action, stops.canResume(id)).left.map(p => conflict(p.value)).flatMap {
          case JobControlStep.Run(a)         => runs(id, a).flatMap(_ => current)
          case JobControlStep.RequestStop(s) => stops.request(id, s); current
          case JobControlStep.Resume         => stops.resume(id).left.map(p => conflict(p.value)).flatMap(_ => current)
          case JobControlStep.CancelNow      =>
            stops.forget(id)
            Transactor(ds)
              .transact(
                JobRepo
                  .transition(
                    id,
                    job.state,
                    JobState.Cancelled,
                    job.progress.map(p => JobRow.Progress(p.value)),
                    Some(Problem(JobControlRules.CancelledByPerson))
                  )
              )
              .toRight(conflict(s"job $id moved on; look again"))
              .flatMap(_ => current)
        }
      }

  /** The same answers over jobs held in memory (tests). A running job's body is the test's: it asks
    * [[JobStops.requestedFor]] between its parts and reports how it stopped with [[stopped]].
    */
  final class InMemory(
      jobs: AtomicReference[Map[JobInfo.Id, JobInfo]],
      stops: JobStops,
      runs: (Job.Id, JobAction) => Either[Api.Error, Unit] = (_, _) => Right(())
  ) extends JobControlService:

    /** A job, as the store would hold it. */
    def put(job: JobInfo): Unit = jobs.updateAndGet(_ + (JobInfo.Id(job.id.value) -> job)): Unit

    def get(id: Job.Id): Option[JobInfo] = jobs.get.get(JobInfo.Id(id.value))

    /** A running job's body stopped as asked, after `progress`: paused (resumed by `resume`) or cancelled. */
    def stopped(id: Job.Id, progress: Option[JobInfo.Progress], resume: () => Either[Problem, Unit]): Unit =
      stops.requestedFor(id).foreach { stop =>
        stops.answered(id)
        val to = if stop == JobStop.Pause then JobState.Paused else JobState.Cancelled
        if stop == JobStop.Pause then stops.pausedWith(id, () => { move(id, JobState.Running, None); resume() })
        move(id, to, Some(JobControlRules.stoppedLine(stop, progress)))
      }

    private def move(id: Job.Id, to: JobState, progress: Option[JobInfo.Progress]): Unit =
      val key = JobInfo.Id(id.value)
      jobs.updateAndGet(m =>
        m.get(key)
          .fold(m)(j =>
            m + (key -> j.copy(
              status = to.wire,
              progress = progress.orElse(j.progress),
              error = Option.when(to == JobState.Cancelled)(Problem(JobControlRules.CancelledByPerson)).orElse(j.error)
            ))
          )
      ): Unit

    def control(id: Job.Id, action: JobAction): Either[Api.Error, JobInfo] =
      def current = get(id).toRight(notFound(id))
      current.flatMap { job =>
        JobControlRules.decide(job, action, stops.canResume(id)).left.map(p => conflict(p.value)).flatMap {
          case JobControlStep.Run(a)         => runs(id, a).flatMap(_ => current)
          case JobControlStep.RequestStop(s) => stops.request(id, s); current
          case JobControlStep.Resume         => stops.resume(id).left.map(p => conflict(p.value)).flatMap(_ => current)
          case JobControlStep.CancelNow      =>
            if JobState.next(job.state, JobState.Cancelled).isEmpty then Left(conflict(s"job $id moved on; look again"))
            else
              stops.forget(id)
              move(id, JobState.Cancelled, None)
              current
        }
      }
