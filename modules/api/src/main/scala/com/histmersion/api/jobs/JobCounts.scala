package com.histmersion.api.jobs
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class JobCounts(
    queued: JobCounts.Queued = 0,
    running: JobCounts.Running = 0,
    done: JobCounts.Done = 0,
    failed: JobCounts.Failed = 0,
    paused: JobCounts.Paused = 0,
    cancelled: JobCounts.Cancelled = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def of(s: JobState): Int = s match
    case JobState.Queued    => queued
    case JobState.Running   => running
    case JobState.Done      => done
    case JobState.Failed    => failed
    case JobState.Paused    => paused
    case JobState.Cancelled => cancelled
object JobCounts:


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

  def of(rows: List[(String, Int)]): JobCounts =
    @tailrec def loop(
        rest: List[(String, Int)],
        queued: Int,
        running: Int,
        done: Int,
        failed: Int,
        paused: Int,
        cancelled: Int
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
    loop(rows, 0, 0, 0, 0, 0, 0)
