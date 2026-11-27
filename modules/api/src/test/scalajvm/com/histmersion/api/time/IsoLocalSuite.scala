package com.histmersion
package api
package time

import java.time.{LocalDateTime, ZoneId}

import com.histmersion.api.automation.Agents.RunInfo
import com.histmersion.api.jobs.JobInfo

class IsoLocalSuite extends munit.FunSuite:

  private val localMorning: String =
    LocalDateTime.of(2026, 11, 27, 6, 14, 3).atZone(ZoneId.systemDefault()).toInstant.toString

  test("an instant is written as ISO local in this machine's timezone, to the minute"):
    assertEquals(IsoLocal.text(localMorning), "2026-11-27 06:14")

  test("single-digit months, days, hours and minutes are padded"):
    val early = LocalDateTime.of(2026, 1, 5, 7, 8).atZone(ZoneId.systemDefault()).toInstant.toString
    assertEquals(IsoLocal.text(early), "2026-01-05 07:08")

  test("a text that is no instant, the empty one too, is kept as it came"):
    assertEquals(IsoLocal.text("soon"), "soon")
    assertEquals(IsoLocal.text(""), "")

  test("the four timestamps of jobs and runs display through it"):
    val shown = List(
      summon[Display[JobInfo.CreatedAt]].text(JobInfo.CreatedAt("2026-11-27T06:14:03Z")),
      summon[Display[JobInfo.UpdatedAt]].text(JobInfo.UpdatedAt("2026-11-27T06:14:03Z")),
      summon[Display[RunInfo.CreatedAt]].text(RunInfo.CreatedAt("2026-11-27T06:14:03Z")),
      summon[Display[RunInfo.UpdatedAt]].text(RunInfo.UpdatedAt("2026-11-27T06:14:03Z"))
    )
    assertEquals(shown, List.fill(4)(IsoLocal.text("2026-11-27T06:14:03Z")))
