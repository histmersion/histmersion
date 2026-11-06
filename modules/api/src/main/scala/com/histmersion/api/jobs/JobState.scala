package com.histmersion.api.jobs
import com.histmersion.api.given
enum JobState:
  case Queued, Running, Done, Failed, Paused, Cancelled

  def wire: com.histmersion.api.jobs.JobState.Wire = _root_.com.histmersion.api.jobs.JobState.Wire(toString.toLowerCase)
  def isTerminal: Boolean = (this == Done || this == Failed || this == Cancelled)

object JobState:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Interrupted = "interrupted"

  def parse(s: com.histmersion.api.jobs.JobState.Wire): Option[JobState] =
    values.find(_.wire == _root_.com.histmersion.api.jobs.JobState.Wire((s).value.trim.toLowerCase))

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
