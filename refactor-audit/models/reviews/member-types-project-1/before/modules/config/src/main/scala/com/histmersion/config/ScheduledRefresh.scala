package com.histmersion.config

/** How often a plan pool is read while the autopilot is on and queued work wants it, besides the reading a run's own
  * pick takes.
  */
enum ScheduledRefresh:
  case Off
  case Every(interval: RefreshIntervalMinutes)

object ScheduledRefresh:
  /** The configured minutes: 0 = off. */
  def fromMinutes(minutes: Int): Either[String, ScheduledRefresh] =
    if minutes == 0 then Right(Off)
    else
      RefreshIntervalMinutes
        .either(minutes)
        .map(Every(_))
        .left
        .map(_ => s"$minutes is not 0 (off) or a positive number of minutes")
