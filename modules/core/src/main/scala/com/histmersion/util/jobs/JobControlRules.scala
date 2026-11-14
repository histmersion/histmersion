package com.histmersion
package util.jobs
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Conflict, Raw}

import com.histmersion.api.jobs.{JobInfo, JobState}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobActions
import com.histmersion.api.RegisteredJobKinds

/** Which background jobs a person may pause and cancel, and what an action on a job in a given state does. Pure.
  */
object JobControlRules:

  def stoppable(kind: JobKinds.Wire): Boolean = JobActions.Stoppable.contains(kind)

  /** The step that answers `action` on `job` (a kind whose runner controls its own jobs is handed the action); `Left`
    * says why it cannot be done. `resumable`: this server holds the paused job's way to go on (a restart loses it).
    */
  def decide(job: JobInfo, action: JobAction, resumable: Boolean): Either[Problem, JobControlStep] =
    def refused(control: Conflict.Control): Either[Problem, JobControlStep] =
      Left(Conflict.Job(Raw.of(job.id), Conflict.State.of(job.status), control))
    if RegisteredJobKinds.all.ownControl(job.kind) then Right(JobControlStep.Run(action))
    else if !stoppable(job.kind) then refused(Conflict.Control.OnePiece(Raw.of(job.kind)))
    else
      (action, JobState.parse(job.status)) match
        case (_, None)                                  => refused(Conflict.Control.Unrecognised)
        case (_, Some(s)) if s.isTerminal               => refused(Conflict.Control.Ended)
        case (JobAction.Pause, Some(JobState.Running))  => Right(JobControlStep.RequestStop(JobStop.Pause))
        case (JobAction.Pause, Some(JobState.Paused))   => refused(Conflict.Control.AlreadyPaused)
        case (JobAction.Pause, Some(_))                 => refused(Conflict.Control.NotStarted)
        case (JobAction.Cancel, Some(JobState.Running)) => Right(JobControlStep.RequestStop(JobStop.Cancel))
        case (JobAction.Cancel, Some(_))                => Right(JobControlStep.CancelNow)
        case (JobAction.Resume, Some(JobState.Paused))  =>
          if resumable then Right(JobControlStep.Resume)
          else refused(Conflict.Control.PausedBeforeRestart)
        case (JobAction.Resume, Some(_)) => refused(Conflict.Control.NotPaused)

  /** The progress line a stopped job keeps: what it had done, and that it was paused or cancelled. */
  def stoppedLine(stop: JobStop, last: Option[JobInfo.Progress]): JobInfo.Progress =
    val what = stop match
      case JobStop.Pause  => "paused"
      case JobStop.Cancel => "cancelled"
    JobInfo.Progress(last.map(_.value.trim).filter(_.nonEmpty).fold(what)(l => s"$what after: $l"))

  /** The error a cancelled job is recorded with. */
  val CancelledByPerson = "cancelled by a person"
