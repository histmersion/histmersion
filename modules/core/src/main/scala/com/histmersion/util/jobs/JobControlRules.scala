package com.histmersion.util.jobs

import com.histmersion.api.jobs.{JobInfo, JobState}

/** Which background jobs a person may pause and cancel, and what an action on a job in a given state does. Pure.
  */
object JobControlRules:

  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def stoppable(kind: com.histmersion.api.jobs.JobKinds.Wire): Boolean = com.histmersion.api.jobs.JobActions.Stoppable.contains(kind)

  /** The step that answers `action` on `job` (a kind whose runner controls its own jobs is handed the action); `Left`
    * says why it cannot be done. `resumable`: this server holds the paused job's way to go on (a restart loses it).
    */
  def decide(job: JobInfo, action: JobAction, resumable: Boolean): Either[Problem, JobControlStep] =
    if com.histmersion.api.RegisteredJobKinds.all.ownControl(job.kind) then Right(JobControlStep.Run(action))
    else if !stoppable(job.kind) then
      Left(Problem(s"a ${job.kind} job cannot be paused or cancelled: it runs as one piece and ends by itself"))
    else
      (action, JobState.parse(job.status)) match
        case (_, None)                                  => Left(Problem(s"job ${job.id} is ${job.status}"))
        case (_, Some(s)) if s.isTerminal               => Left(Problem(s"job ${job.id} has ended (${s.wire})"))
        case (JobAction.Pause, Some(JobState.Running))  => Right(JobControlStep.RequestStop(JobStop.Pause))
        case (JobAction.Pause, Some(JobState.Paused))   => Left(Problem(s"job ${job.id} is already paused"))
        case (JobAction.Pause, Some(_))                 => Left(Problem(s"job ${job.id} has not started yet: cancel it instead"))
        case (JobAction.Cancel, Some(JobState.Running)) => Right(JobControlStep.RequestStop(JobStop.Cancel))
        case (JobAction.Cancel, Some(_))                => Right(JobControlStep.CancelNow)
        case (JobAction.Resume, Some(JobState.Paused))  =>
          if resumable then Right(JobControlStep.Resume)
          else
            Left(Problem(s"job ${job.id} was paused before the server restarted: start it again — it goes on where it stopped"))
        case (JobAction.Resume, Some(_)) => Left(Problem(s"job ${job.id} is not paused"))

  /** The progress line a stopped job keeps: what it had done, and that it was paused or cancelled. */
  def stoppedLine(stop: JobStop, last: Option[JobInfo.Progress]): JobInfo.Progress =
    val what = stop match
      case JobStop.Pause  => "paused"
      case JobStop.Cancel => "cancelled"
    JobInfo.Progress(last.map(_.value.trim).filter(_.nonEmpty).fold(what)(l => s"$what after: $l"))

  /** The error a cancelled job is recorded with. */
  val CancelledByPerson = "cancelled by a person"
