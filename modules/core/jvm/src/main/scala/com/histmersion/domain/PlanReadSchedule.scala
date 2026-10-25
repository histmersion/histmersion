package com.histmersion.domain

import com.histmersion.config.ScheduledRefresh

import java.time.{Duration, Instant}

enum ScheduledRead:
  case Due
  case NotDue(why: String)

object PlanReadSchedule:
  /** Whether the schedule takes a fresh reading of a pool now: its schedule is on, the autopilot is on,
    * queued work wants the pool, and neither its newest reading (whoever took it) nor the last attempt to
    * take one is younger than the interval. */
  def scheduledRead(schedule: ScheduledRefresh, autopilotOn: Boolean, wanted: Boolean, newestReading: Option[Instant],
      lastAttempt: Option[Instant], now: Instant): ScheduledRead =
    schedule match
      case ScheduledRefresh.Off => ScheduledRead.NotDue("the schedule is off")
      case ScheduledRefresh.Every(interval) =>
        val latest = List(newestReading, lastAttempt).flatten.maxOption
        val age = latest.map(Duration.between(_, now))
        if !autopilotOn then ScheduledRead.NotDue("the autopilot is off")
        else if !wanted then ScheduledRead.NotDue("no queued work wants the pool")
        else age.filter(_.toMinutes < interval.value) match
          case Some(a) => ScheduledRead.NotDue(s"read ${a.toMinutes} min ago (every ${interval.value} min)")
          case None    => ScheduledRead.Due
