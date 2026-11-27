package com.histmersion
package api.jobs

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
enum JobState derives CanEqual:
  case Queued, Running, Done, Failed, Paused, Cancelled

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: JobState.Wire = JobState.Wire(toString.toLowerCase)
  def isTerminal: Boolean = (this == Done || this == Failed || this == Cancelled)

object JobState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Interrupted = "interrupted"

  /** The sentence a restart leaves in a job it cut short. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[JobState] =
    values.find(_.wire == Wire((s).value.trim.toLowerCase))

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
