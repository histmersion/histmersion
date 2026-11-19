package com.histmersion
package api.jobs
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class JobCounts(
    queued: JobCounts.Queued = JobCounts.Queued(0),
    running: JobCounts.Running = JobCounts.Running(0),
    done: JobCounts.Done = JobCounts.Done(0),
    failed: JobCounts.Failed = JobCounts.Failed(0),
    paused: JobCounts.Paused = JobCounts.Paused(0),
    cancelled: JobCounts.Cancelled = JobCounts.Cancelled(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def of(s: JobState): JobCounts.Count = s match
    case JobState.Queued    => JobCounts.Count((queued).value)
    case JobState.Running   => JobCounts.Count((running).value)
    case JobState.Done      => JobCounts.Count((done).value)
    case JobState.Failed    => JobCounts.Count((failed).value)
    case JobState.Paused    => JobCounts.Count((paused).value)
    case JobState.Cancelled => JobCounts.Count((cancelled).value)
object JobCounts:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]

  type Queued = Queued.T
  object Queued extends Newtype[Int, Pure]
  type Running = Running.T
  object Running extends Newtype[Int, Pure]
  type Done = Done.T
  object Done extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[Int, Pure]
  type Paused = Paused.T
  object Paused extends Newtype[Int, Pure]
  type Cancelled = Cancelled.T
  object Cancelled extends Newtype[Int, Pure]

  def of(rows: List[(JobState.Wire, Count)]): JobCounts =
    @tailrec def loop(
        rest: List[(JobState.Wire, Count)],
        queued: Queued,
        running: Running,
        done: Done,
        failed: Failed,
        paused: Paused,
        cancelled: Cancelled
    ): JobCounts =
      rest match
        case Nil            => JobCounts(queued, running, done, failed, paused, cancelled)
        case (s, n) :: tail =>
          JobState.parse(s) match
            case Some(JobState.Queued) =>
              loop(tail, Queued((queued).value + (n).value), running, done, failed, paused, cancelled)
            case Some(JobState.Running) =>
              loop(tail, queued, Running((running).value + (n).value), done, failed, paused, cancelled)
            case Some(JobState.Done) =>
              loop(tail, queued, running, Done((done).value + (n).value), failed, paused, cancelled)
            case Some(JobState.Failed) =>
              loop(tail, queued, running, done, Failed((failed).value + (n).value), paused, cancelled)
            case Some(JobState.Paused) =>
              loop(tail, queued, running, done, failed, Paused((paused).value + (n).value), cancelled)
            case Some(JobState.Cancelled) =>
              loop(tail, queued, running, done, failed, paused, Cancelled((cancelled).value + (n).value))
            case None => loop(tail, queued, running, done, failed, paused, cancelled)
    loop(rows, Queued(0), Running(0), Done(0), Failed(0), Paused(0), Cancelled(0))
