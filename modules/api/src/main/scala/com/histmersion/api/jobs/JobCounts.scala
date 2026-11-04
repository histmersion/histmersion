package com.histmersion.api.jobs
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class JobCounts(
    queued: Int = 0,
    running: Int = 0,
    done: Int = 0,
    failed: Int = 0,
    paused: Int = 0,
    cancelled: Int = 0
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
