package com.histmersion.config

/** How often a plan pool is read while the autopilot is on and queued work wants it, besides the reading a run's own
  * pick takes.
  */
enum ScheduledRefresh:
  case Off
  case Every(interval: RefreshIntervalMinutes)

object ScheduledRefresh:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The configured minutes: 0 = off. */

  def fromMinutes(minutes: com.histmersion.config.Minutes): Either[com.histmersion.config.ScheduledRefresh.Problem, ScheduledRefresh] =
    if minutes.value == 0 then Right(Off)
    else
      RefreshIntervalMinutes
        .either(minutes.value)
        .map(Every(_))
        .left
        .map(_ => Problem(s"$minutes is not 0 (off) or a positive number of minutes"))
