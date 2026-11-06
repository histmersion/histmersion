package com.histmersion.api.jobs
import com.histmersion.api.given
enum JobState:
  case Queued, Running, Done, Failed, Paused, Cancelled

  def wire: com.histmersion.api.jobs.JobState.WireResult = _root_.com.histmersion.api.jobs.JobState.WireResult(toString.toLowerCase)
  def isTerminal: com.histmersion.api.jobs.JobState.IsTerminalResult = _root_.com.histmersion.api.jobs.JobState.IsTerminalResult(this == Done || this == Failed || this == Cancelled)

object JobState:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsTerminalResult = IsTerminalResult.T
  object IsTerminalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Interrupted = "interrupted"

  def parse(s: com.histmersion.api.jobs.JobState.ParseS): Option[JobState] =
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
