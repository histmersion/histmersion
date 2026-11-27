package com.histmersion
package api
package time

import scala.scalajs.js

import com.histmersion.api.automation.Agents.RunInfo
import com.histmersion.api.jobs.JobInfo

class IsoLocalSuite extends munit.FunSuite:

  private val localMorning: String = new js.Date(2026, 10, 27, 6, 14, 3).toISOString()

  test("an instant is written as ISO local in the viewer's timezone, to the minute"):
    assertEquals(IsoLocal.text(localMorning), "2026-11-27 06:14")

  test("single-digit months, days, hours and minutes are padded"):
    assertEquals(IsoLocal.text(new js.Date(2026, 0, 5, 7, 8).toISOString()), "2026-01-05 07:08")

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
