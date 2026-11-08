package com.histmersion
package config

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** How often a plan pool is read while the autopilot is on and queued work wants it, besides the reading a run's own
  * pick takes.
  */
enum ScheduledRefresh:
  case Off
  case Every(interval: RefreshIntervalMinutes)

object ScheduledRefresh:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  /** The configured minutes: 0 = off. */

  def fromMinutes(minutes: Minutes): Either[Problem, ScheduledRefresh] =
    if minutes.value == 0 then Right(Off)
    else
      RefreshIntervalMinutes
        .either(minutes.value)
        .map(Every(_))
        .left
        .map(_ => Problem(s"$minutes is not 0 (off) or a positive number of minutes"))
