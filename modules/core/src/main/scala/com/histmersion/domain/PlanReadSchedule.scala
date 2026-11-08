package com.histmersion
package domain

import java.time.{Duration, Instant}
import io.github.iltotore.iron.constraint.any.Pure

object PlanReadSchedule:
  type AutopilotOn = AutopilotOn.T
  object AutopilotOn extends Newtype[Boolean, Pure]
  type Wanted = Wanted.T
  object Wanted extends Newtype[Boolean, Pure]
  /** Whether the schedule takes a fresh reading of a pool now: its schedule is on, the autopilot is on, queued work
    * wants the pool, and neither its newest reading (whoever took it) nor the last attempt to take one is younger than
    * the interval.
    */

  def scheduledRead(
      schedule: ReadSchedule,
      autopilotOn: AutopilotOn,
      wanted: Wanted,
      newestReading: Option[Instant],
      lastAttempt: Option[Instant],
      now: Instant
  ): ScheduledRead =
    schedule match
      case ReadSchedule.Off             => ScheduledRead.NotDue(ScheduledRead.NotDue.Why("the schedule is off"))
      case ReadSchedule.Every(interval) =>
        val latest = List(newestReading, lastAttempt).flatten.maxOption
        val age = latest.map(Duration.between(_, now))
        if !autopilotOn.value then ScheduledRead.NotDue(ScheduledRead.NotDue.Why("the autopilot is off"))
        else if !wanted.value then ScheduledRead.NotDue(ScheduledRead.NotDue.Why("no queued work wants the pool"))
        else
          age.filter(_.toMinutes < interval.toMinutes) match
            case Some(a) => ScheduledRead.NotDue(ScheduledRead.NotDue.Why(s"read ${a.toMinutes} min ago (every ${interval.toMinutes} min)"))
            case None    => ScheduledRead.Due
