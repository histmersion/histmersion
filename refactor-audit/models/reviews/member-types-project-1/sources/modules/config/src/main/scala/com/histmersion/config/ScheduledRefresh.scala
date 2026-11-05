package com.histmersion.config

/** How often a plan pool is read while the autopilot is on and queued work wants it, besides the reading a run's own
  * pick takes.
  */
enum ScheduledRefresh:
  case Off
  case Every(interval: RefreshIntervalMinutes)

object ScheduledRefresh:
  /** The configured minutes: 0 = off. */
  type FromMinutesResultPart1 = FromMinutesResultPart1.T
  object FromMinutesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromMinutesMinutes = FromMinutesMinutes.T
  object FromMinutesMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromMinutes(minutes: com.histmersion.config.ScheduledRefresh.FromMinutesMinutes): Either[com.histmersion.config.ScheduledRefresh.FromMinutesResultPart1, ScheduledRefresh] =
    if minutes == 0 then Right(Off)
    else
      RefreshIntervalMinutes
        .either(minutes)
        .map(Every(_))
        .left
        .map(_ => s"$minutes is not 0 (off) or a positive number of minutes")
