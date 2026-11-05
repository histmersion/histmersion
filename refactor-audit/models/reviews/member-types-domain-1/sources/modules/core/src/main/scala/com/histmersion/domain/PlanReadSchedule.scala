package com.histmersion.domain

import java.time.{Duration, Instant}

object PlanReadSchedule:
  /** Whether the schedule takes a fresh reading of a pool now: its schedule is on, the autopilot is on, queued work
    * wants the pool, and neither its newest reading (whoever took it) nor the last attempt to take one is younger than
    * the interval.
    */
  type ScheduledReadAutopilotOn = ScheduledReadAutopilotOn.T
  object ScheduledReadAutopilotOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduledReadWanted = ScheduledReadWanted.T
  object ScheduledReadWanted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def scheduledRead(
      schedule: ReadSchedule,
      autopilotOn: com.histmersion.domain.PlanReadSchedule.ScheduledReadAutopilotOn,
      wanted: com.histmersion.domain.PlanReadSchedule.ScheduledReadWanted,
      newestReading: Option[Instant],
      lastAttempt: Option[Instant],
      now: Instant
  ): ScheduledRead =
    schedule match
      case ReadSchedule.Off             => ScheduledRead.NotDue("the schedule is off")
      case ReadSchedule.Every(interval) =>
        val latest = List(newestReading, lastAttempt).flatten.maxOption
        val age = latest.map(Duration.between(_, now))
        if !autopilotOn then ScheduledRead.NotDue("the autopilot is off")
        else if !wanted then ScheduledRead.NotDue("no queued work wants the pool")
        else
          age.filter(_.toMinutes < interval.toMinutes) match
            case Some(a) => ScheduledRead.NotDue(s"read ${a.toMinutes} min ago (every ${interval.toMinutes} min)")
            case None    => ScheduledRead.Due
