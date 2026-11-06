package com.histmersion.api.jobs
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class JobCounts(
    queued: JobCounts.Queued = _root_.com.histmersion.api.jobs.JobCounts.Queued(0),
    running: JobCounts.Running = _root_.com.histmersion.api.jobs.JobCounts.Running(0),
    done: JobCounts.Done = _root_.com.histmersion.api.jobs.JobCounts.Done(0),
    failed: JobCounts.Failed = _root_.com.histmersion.api.jobs.JobCounts.Failed(0),
    paused: JobCounts.Paused = _root_.com.histmersion.api.jobs.JobCounts.Paused(0),
    cancelled: JobCounts.Cancelled = _root_.com.histmersion.api.jobs.JobCounts.Cancelled(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def of(s: JobState): com.histmersion.api.jobs.JobCounts.OfResult = s match
    case JobState.Queued    => queued
    case JobState.Running   => running
    case JobState.Done      => done
    case JobState.Failed    => failed
    case JobState.Paused    => paused
    case JobState.Cancelled => cancelled
object JobCounts:


  type OfLoopRestPart1 = OfLoopRestPart1.T
  object OfLoopRestPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopRestPart2 = OfLoopRestPart2.T
  object OfLoopRestPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopQueued = OfLoopQueued.T
  object OfLoopQueued extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopRunning = OfLoopRunning.T
  object OfLoopRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopDone = OfLoopDone.T
  object OfLoopDone extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopFailed = OfLoopFailed.T
  object OfLoopFailed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopPaused = OfLoopPaused.T
  object OfLoopPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLoopCancelled = OfLoopCancelled.T
  object OfLoopCancelled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfRowsPart1 = OfRowsPart1.T
  object OfRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfRowsPart2 = OfRowsPart2.T
  object OfRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Queued = Queued.T
  object Queued extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Running = Running.T
  object Running extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Done = Done.T
  object Done extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failed = Failed.T
  object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Paused = Paused.T
  object Paused extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cancelled = Cancelled.T
  object Cancelled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(rows: List[(com.histmersion.api.jobs.JobCounts.OfRowsPart1, com.histmersion.api.jobs.JobCounts.OfRowsPart2)]): JobCounts =
    @tailrec def loop(
        rest: List[(com.histmersion.api.jobs.JobCounts.OfLoopRestPart1, com.histmersion.api.jobs.JobCounts.OfLoopRestPart2)],
        queued: com.histmersion.api.jobs.JobCounts.OfLoopQueued,
        running: com.histmersion.api.jobs.JobCounts.OfLoopRunning,
        done: com.histmersion.api.jobs.JobCounts.OfLoopDone,
        failed: com.histmersion.api.jobs.JobCounts.OfLoopFailed,
        paused: com.histmersion.api.jobs.JobCounts.OfLoopPaused,
        cancelled: com.histmersion.api.jobs.JobCounts.OfLoopCancelled
    ): JobCounts =
      rest match
        case Nil            => JobCounts(queued, running, done, failed, paused, cancelled)
        case (s, n) :: tail =>
          JobState.parse(s) match
            case Some(JobState.Queued)    => loop(tail, queued + n, running, done, failed, paused, cancelled)
            case Some(JobState.Running)   => loop(tail, queued, running + n, done, failed, paused, cancelled)
            case Some(JobState.Done)      => loop(tail, queued, running, done + n, failed, paused, cancelled)
            case Some(JobState.Failed)    => loop(tail, queued, running, done, failed + n, paused, cancelled)
            case Some(JobState.Paused)    => loop(tail, queued, running, done, failed, paused + n, cancelled)
            case Some(JobState.Cancelled) => loop(tail, queued, running, done, failed, paused, cancelled + n)
            case None                     => loop(tail, queued, running, done, failed, paused, cancelled)
    loop(rows, _root_.com.histmersion.api.jobs.JobCounts.OfLoopQueued(0), _root_.com.histmersion.api.jobs.JobCounts.OfLoopRunning(0), _root_.com.histmersion.api.jobs.JobCounts.OfLoopDone(0), _root_.com.histmersion.api.jobs.JobCounts.OfLoopFailed(0), _root_.com.histmersion.api.jobs.JobCounts.OfLoopPaused(0), _root_.com.histmersion.api.jobs.JobCounts.OfLoopCancelled(0))
