package com.histmersion.api.jobs
import com.histmersion.api.given
enum JobState:
  case Queued, Running, Done, Failed, Paused, Cancelled

  def wire: String = toString.toLowerCase
  def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

object JobState:
  val Interrupted = "interrupted"

  def parse(s: String): Option[JobState] =
    values.find(_.wire == s.trim.toLowerCase)

  def next(from: JobState, to: JobState): Option[JobState] = (from, to) match
    case (Queued, Running)    => Some(Running)
    case (Queued, Failed)     => Some(Failed)
    case (Running, Done)      => Some(Done)
    case (Running, Failed)    => Some(Failed)
    case (Running, Paused)    => Some(Paused)
    case (Paused, Running)    => Some(Running)
    case (Running, Cancelled) => Some(Cancelled)
    case (Paused, Cancelled)  => Some(Cancelled)
    case (Queued, Cancelled)  => Some(Cancelled)
    case _                    => None
