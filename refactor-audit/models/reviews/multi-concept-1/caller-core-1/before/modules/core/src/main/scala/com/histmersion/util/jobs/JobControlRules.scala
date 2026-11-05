package com.histmersion.util.jobs

import com.histmersion.api.jobs.{JobInfo, JobState}

/** Which background jobs a person may pause and cancel, and what an action on a job in a given state does. Pure.
  */
object JobControlRules:

  def stoppable(kind: String): Boolean = com.histmersion.api.jobs.JobActions.Stoppable.contains(kind)

  /** The step that answers `action` on `job` (a kind whose runner controls its own jobs is handed the action); `Left`
    * says why it cannot be done. `resumable`: this server holds the paused job's way to go on (a restart loses it).
    */
  def decide(job: JobInfo, action: JobAction, resumable: Boolean): Either[String, JobControlStep] =
    if com.histmersion.api.RegisteredJobKinds.all.ownControl(job.kind) then Right(JobControlStep.Run(action))
    else if !stoppable(job.kind) then
      Left(s"a ${job.kind} job cannot be paused or cancelled: it runs as one piece and ends by itself")
    else
      (action, JobState.parse(job.status)) match
        case (_, None)                                  => Left(s"job ${job.id} is ${job.status}")
        case (_, Some(s)) if s.isTerminal               => Left(s"job ${job.id} has ended (${s.wire})")
        case (JobAction.Pause, Some(JobState.Running))  => Right(JobControlStep.RequestStop(JobStop.Pause))
        case (JobAction.Pause, Some(JobState.Paused))   => Left(s"job ${job.id} is already paused")
        case (JobAction.Pause, Some(_))                 => Left(s"job ${job.id} has not started yet: cancel it instead")
        case (JobAction.Cancel, Some(JobState.Running)) => Right(JobControlStep.RequestStop(JobStop.Cancel))
        case (JobAction.Cancel, Some(_))                => Right(JobControlStep.CancelNow)
        case (JobAction.Resume, Some(JobState.Paused))  =>
          if resumable then Right(JobControlStep.Resume)
          else
            Left(s"job ${job.id} was paused before the server restarted: start it again — it goes on where it stopped")
        case (JobAction.Resume, Some(_)) => Left(s"job ${job.id} is not paused")

  /** The progress line a stopped job keeps: what it had done, and that it was paused or cancelled. */
  def stoppedLine(stop: JobStop, last: Option[String]): String =
    val what = stop match
      case JobStop.Pause  => "paused"
      case JobStop.Cancel => "cancelled"
    last.map(_.trim).filter(_.nonEmpty).fold(what)(l => s"$what after: $l")

  /** The error a cancelled job is recorded with. */
  val CancelledByPerson = "cancelled by a person"
