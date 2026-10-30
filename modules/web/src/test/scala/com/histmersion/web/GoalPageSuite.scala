package com.histmersion.web

import com.histmersion.api.{Goal, Managers}
import com.histmersion.api.GoalPlans.{GoalSchedule, GoalView, ScheduleKind}

/** A goal page's pure parts: the plan's steps and marks, the schedule line, pausing, its filtered lists. */
class GoalPageSuite extends munit.FunSuite:

  private def item(
      id: Long,
      status: String,
      at: String,
      kind: String = "compile-article",
      target: String = "sea-peoples"
  ) =
    Managers.ManagerItem(id, "seed:g", kind, target, status, updatedAt = at, createdAt = at)

  test("the plan: what runs now first, then what is to do, then what was done, newest first in each"):
    val is = List(
      item(1, "done", "2026-10-01T00:00:00Z"),
      item(2, "pending", "2026-10-02T00:00:00Z"),
      item(3, "active", "2026-10-03T00:00:00Z"),
      item(4, "done", "2026-10-04T00:00:00Z"),
      item(5, "failed", "2026-10-05T00:00:00Z")
    )
    assertEquals(GoalPage.steps(is).map(_.id), List(3L, 2L, 5L, 4L, 1L))
    assertEquals(
      is.map(GoalPage.markOf),
      List(GoalPage.Mark.Done, GoalPage.Mark.Todo, GoalPage.Mark.Going, GoalPage.Mark.Done, GoalPage.Mark.Stopped)
    )

  test("a step's name is its kind of work and what it is on"):
    assertEquals(
      GoalPage.stepName(item(1, "done", "t", kind = "review-article", target = "sea-peoples")),
      "Review an article — sea-peoples"
    )
    assertEquals(
      GoalPage.stepName(item(1, "done", "t", kind = "currency-check", target = "seed:g")),
      "Check sources are current"
    )

  test("days, as the state line says them"):
    def at(iso: String) = scala.scalajs.js.Date.parse(iso)
    val now = at("2026-10-30T12:00:00")
    assertEquals(GoalPage.days(at("2026-10-27T09:00:00"), now), "3 days ago")
    assertEquals(GoalPage.days(at("2026-10-30T08:00:00"), now), "today")
    assertEquals(GoalPage.days(at("2026-10-29T23:00:00"), now), "yesterday")
    assertEquals(GoalPage.days(at("2026-10-31T01:00:00"), now), "tomorrow")
    assertEquals(GoalPage.days(at("2026-11-01T20:00:00"), now), "in 2 days")

  test("the schedule: the currency check's last and next run, else the watcher's next tick"):
    val v = GoalView(
      Goal("g", "G", "seeding"),
      schedules = List(
        GoalSchedule(
          "g",
          ScheduleKind.Currency,
          "@weekly",
          enabled = true,
          lastRunAt = Some("2026-10-27T09:00:00"),
          nextRunAt = Some("2026-11-03T09:00:00")
        )
      )
    )
    val now = scala.scalajs.js.Date.parse("2026-10-30T12:00:00")
    assertEquals(
      GoalPage.schedule(v, None, None, now),
      Some("checked for new publications 3 days ago · next check in 4 days")
    )
    val watcher = Managers.ManagerInfo("watch:g", "watch", goal = Some("g"), nextTickAt = Some("2026-10-31T13:00:00"))
    assertEquals(GoalPage.schedule(v.copy(schedules = Nil), Some(watcher), None, now), Some("next check tomorrow"))
    assertEquals(GoalPage.schedule(v.copy(schedules = Nil), None, None, now), None)

  test("a goal is paused when every agent working for it is; its lists and sources are filtered to it"):
    val ms = List(
      Managers.ManagerInfo("seed:g", "seed", state = "paused"),
      Managers.ManagerInfo("watch:g", "watch", state = "paused")
    )
    assert(GoalPage.paused(ms))
    assert(!GoalPage.paused(ms.updated(0, ms.head.copy(state = "active"))))
    assert(!GoalPage.paused(Nil))
    assertEquals(GoalPage.listOf(ResearchSection.Backlog, "bronze age"), "/research/backlog?goal=bronze%20age")
    assertEquals(GoalPage.libraryOf("g"), "/library#goal=g")
    assertEquals(GoalPage.mapOf("g"), "/explore#goal=g")
