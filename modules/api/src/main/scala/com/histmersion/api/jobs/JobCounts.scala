package com.histmersion.api.jobs
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class JobCounts(
    queued: JobCounts.Queued = _root_.com.histmersion.api.jobs.JobCounts.Queued(0),
    running: JobCounts.Running = _root_.com.histmersion.api.jobs.JobCounts.Running(0),
    done: JobCounts.Done = _root_.com.histmersion.api.jobs.JobCounts.Done(0),
    failed: JobCounts.Failed = _root_.com.histmersion.api.jobs.JobCounts.Failed(0),
    paused: JobCounts.Paused = _root_.com.histmersion.api.jobs.JobCounts.Paused(0),
    cancelled: JobCounts.Cancelled = _root_.com.histmersion.api.jobs.JobCounts.Cancelled(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def of(s: JobState): com.histmersion.api.jobs.JobCounts.Count = s match
    case JobState.Queued    => _root_.com.histmersion.api.jobs.JobCounts.Count((queued).value)
    case JobState.Running   => _root_.com.histmersion.api.jobs.JobCounts.Count((running).value)
    case JobState.Done      => _root_.com.histmersion.api.jobs.JobCounts.Count((done).value)
    case JobState.Failed    => _root_.com.histmersion.api.jobs.JobCounts.Count((failed).value)
    case JobState.Paused    => _root_.com.histmersion.api.jobs.JobCounts.Count((paused).value)
    case JobState.Cancelled => _root_.com.histmersion.api.jobs.JobCounts.Count((cancelled).value)
object JobCounts:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]




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

  def of(rows: List[(com.histmersion.api.jobs.JobState.Wire, com.histmersion.api.jobs.JobCounts.Count)]): JobCounts =
    @tailrec def loop(
        rest: List[(com.histmersion.api.jobs.JobState.Wire, com.histmersion.api.jobs.JobCounts.Count)],
        queued: com.histmersion.api.jobs.JobCounts.Queued,
        running: com.histmersion.api.jobs.JobCounts.Running,
        done: com.histmersion.api.jobs.JobCounts.Done,
        failed: com.histmersion.api.jobs.JobCounts.Failed,
        paused: com.histmersion.api.jobs.JobCounts.Paused,
        cancelled: com.histmersion.api.jobs.JobCounts.Cancelled
    ): JobCounts =
      rest match
        case Nil            => JobCounts(queued, running, done, failed, paused, cancelled)
        case (s, n) :: tail =>
          JobState.parse(s) match
            case Some(JobState.Queued)    => loop(tail, _root_.com.histmersion.api.jobs.JobCounts.Queued((queued).value + (n).value), running, done, failed, paused, cancelled)
            case Some(JobState.Running)   => loop(tail, queued, _root_.com.histmersion.api.jobs.JobCounts.Running((running).value + (n).value), done, failed, paused, cancelled)
            case Some(JobState.Done)      => loop(tail, queued, running, _root_.com.histmersion.api.jobs.JobCounts.Done((done).value + (n).value), failed, paused, cancelled)
            case Some(JobState.Failed)    => loop(tail, queued, running, done, _root_.com.histmersion.api.jobs.JobCounts.Failed((failed).value + (n).value), paused, cancelled)
            case Some(JobState.Paused)    => loop(tail, queued, running, done, failed, _root_.com.histmersion.api.jobs.JobCounts.Paused((paused).value + (n).value), cancelled)
            case Some(JobState.Cancelled) => loop(tail, queued, running, done, failed, paused, _root_.com.histmersion.api.jobs.JobCounts.Cancelled((cancelled).value + (n).value))
            case None                     => loop(tail, queued, running, done, failed, paused, cancelled)
    loop(rows, _root_.com.histmersion.api.jobs.JobCounts.Queued(0), _root_.com.histmersion.api.jobs.JobCounts.Running(0), _root_.com.histmersion.api.jobs.JobCounts.Done(0), _root_.com.histmersion.api.jobs.JobCounts.Failed(0), _root_.com.histmersion.api.jobs.JobCounts.Paused(0), _root_.com.histmersion.api.jobs.JobCounts.Cancelled(0))
