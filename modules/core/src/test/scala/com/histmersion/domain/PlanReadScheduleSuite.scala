package com.histmersion
package domain

class PlanReadScheduleSuite extends munit.FunSuite:

  test(
    "scheduled reads: due only with the schedule and the autopilot on, work wanting the pool, and nothing read within the interval"
  ):
    val at = java.time.Instant.parse("2026-10-24T12:00:00Z")
    def minutesAgo(m: Int) = Some(at.minusSeconds(m * 60L))
    val every3h = ReadSchedule.Every(java.time.Duration.ofMinutes(180))
    def read(
        schedule: ReadSchedule = every3h,
        autopilotOn: ReadSchedule.AutopilotOn = ReadSchedule.AutopilotOn(true),
        wanted: ReadSchedule.Wanted = ReadSchedule.Wanted(true),
        newest: Option[java.time.Instant] = minutesAgo(200),
        attempt: Option[java.time.Instant] = None
    ) =
      schedule.scheduledRead(autopilotOn, wanted, newest, attempt, at)
    assertEquals(read(), ScheduledRead.Due)
    assertEquals(read(newest = None), ScheduledRead.Due, "never read: due")
    assertEquals(read(newest = minutesAgo(180)), ScheduledRead.Due, "exactly the interval: due")
    assertEquals(
      read(newest = minutesAgo(60)),
      ScheduledRead.NotDue(ReadSchedule.Rationale("read 60 min ago (every 180 min)"))
    )
    assertEquals(
      read(attempt = minutesAgo(5)),
      ScheduledRead.NotDue(ReadSchedule.Rationale("read 5 min ago (every 180 min)")),
      "a failed attempt counts like a reading: no retry before the interval"
    )
    assertEquals(
      read(schedule = ReadSchedule.Off),
      ScheduledRead.NotDue(ReadSchedule.Rationale("the schedule is off"))
    )
    assertEquals(
      read(autopilotOn = ReadSchedule.AutopilotOn(false)),
      ScheduledRead.NotDue(ReadSchedule.Rationale("the autopilot is off"))
    )
    assertEquals(
      read(wanted = ReadSchedule.Wanted(false)),
      ScheduledRead.NotDue(ReadSchedule.Rationale("no queued work wants the pool"))
    )
