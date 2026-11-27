package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.{Goal, Managers}
import com.histmersion.api.automation.Goal.Plans.{GoalSchedule, GoalView, ScheduleKind}
import com.histmersion.api.automation.Managers.ManagerItem
import com.histmersion.lint.{tolerated, LintRule}

/** A goal page's pure parts: the plan's steps and marks, the schedule line, pausing, its filtered lists. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class GoalPageSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def item(
      id: ManagerItem.Id,
      status: ManagerItem.Status,
      at: ManagerItem.UpdatedAt,
      kind: ManagerItem.Kind = ManagerItem.Kind("compile-article"),
      target: ManagerItem.Target = ManagerItem.Target("sea-peoples")
  ) =
    Managers.ManagerItem(
      id,
      Managers.ManagerInfo.Id("seed:g"),
      kind,
      target,
      status,
      updatedAt = at,
      createdAt = ManagerItem.CreatedAt(at.value)
    )

  test("the plan: what runs now first, then what is to do, then what was done, newest first in each"):
    val is = List(
      item(ManagerItem.Id(1), ManagerItem.Status("done"), ManagerItem.UpdatedAt("2026-10-01T00:00:00Z")),
      item(ManagerItem.Id(2), ManagerItem.Status("pending"), ManagerItem.UpdatedAt("2026-10-02T00:00:00Z")),
      item(ManagerItem.Id(3), ManagerItem.Status("active"), ManagerItem.UpdatedAt("2026-10-03T00:00:00Z")),
      item(ManagerItem.Id(4), ManagerItem.Status("done"), ManagerItem.UpdatedAt("2026-10-04T00:00:00Z")),
      item(ManagerItem.Id(5), ManagerItem.Status("failed"), ManagerItem.UpdatedAt("2026-10-05T00:00:00Z"))
    )
    assertEquals(GoalPage.steps(is).map(_.id.value), List(3L, 2L, 5L, 4L, 1L))
    assertEquals(
      is.map(GoalPage.markOf),
      List(GoalPage.Mark.Done, GoalPage.Mark.Todo, GoalPage.Mark.Going, GoalPage.Mark.Done, GoalPage.Mark.Stopped)
    )

  test("a step's name is its kind of work and what it is on"):
    assertEquals(
      GoalPage
        .stepName(
          item(
            ManagerItem.Id(1),
            ManagerItem.Status("done"),
            ManagerItem.UpdatedAt("t"),
            kind = ManagerItem.Kind("review-article"),
            target = ManagerItem.Target("sea-peoples")
          )
        )
        .value,
      "Review an article — sea-peoples"
    )
    assertEquals(
      GoalPage
        .stepName(
          item(
            ManagerItem.Id(1),
            ManagerItem.Status("done"),
            ManagerItem.UpdatedAt("t"),
            kind = ManagerItem.Kind("currency-check"),
            target = ManagerItem.Target("seed:g")
          )
        )
        .value,
      "Check sources are current"
    )

  test("days, as the state line says them"):
    def at(iso: String) = GoalPage.Millis(scala.scalajs.js.Date.parse(iso))
    val now = at("2026-10-30T12:00:00")
    assertEquals(GoalPage.days(at("2026-10-27T09:00:00"), now).value, "3 days ago")
    assertEquals(GoalPage.days(at("2026-10-30T08:00:00"), now).value, "today")
    assertEquals(GoalPage.days(at("2026-10-29T23:00:00"), now).value, "yesterday")
    assertEquals(GoalPage.days(at("2026-10-31T01:00:00"), now).value, "tomorrow")
    assertEquals(GoalPage.days(at("2026-11-01T20:00:00"), now).value, "in 2 days")

  test("the schedule: the currency check's last and next run, else the watcher's next tick"):
    val v = GoalView(
      Goal(Goal.Slug("g"), Goal.Title("G"), Goal.StatusValue("seeding")),
      schedules = List(
        GoalSchedule(
          Goal.Slug("g"),
          ScheduleKind.Currency,
          GoalSchedule.Cron("@weekly"),
          enabled = GoalSchedule.Enabled(true),
          lastRunAt = Some(GoalSchedule.LastRunAt("2026-10-27T09:00:00")),
          nextRunAt = Some(GoalSchedule.NextRunAt("2026-11-03T09:00:00"))
        )
      )
    )
    val now = GoalPage.ScheduleNowMs(scala.scalajs.js.Date.parse("2026-10-30T12:00:00"))
    assertEquals(
      GoalPage.schedule(v, None, None, now).map(_.value),
      Some("checked for new publications 3 days ago · next check in 4 days")
    )
    val watcher = Managers.ManagerInfo(
      Managers.ManagerInfo.Id("watch:g"),
      Managers.ManagerInfo.Kind("watch"),
      goal = Some(Goal.Slug("g")),
      nextTickAt = Some(Managers.ManagerInfo.NextTickAt("2026-10-31T13:00:00"))
    )
    assertEquals(
      GoalPage.schedule(v.modify(_.schedules).setTo(Nil), Some(watcher), None, now).map(_.value),
      Some("next check tomorrow")
    )
    assertEquals(GoalPage.schedule(v.modify(_.schedules).setTo(Nil), None, None, now), None)

  test("a goal is paused when every agent working for it is; its lists and sources are filtered to it"):
    val ms = List(
      Managers.ManagerInfo(
        Managers.ManagerInfo.Id("seed:g"),
        Managers.ManagerInfo.Kind("seed"),
        state = Managers.ManagerInfo.State("paused")
      ),
      Managers.ManagerInfo(
        Managers.ManagerInfo.Id("watch:g"),
        Managers.ManagerInfo.Kind("watch"),
        state = Managers.ManagerInfo.State("paused")
      )
    )
    assert(GoalPage.paused(ms))
    assert(!GoalPage.paused(ms.updated(0, ms.head.copy(state = Managers.ManagerInfo.State("active")))))
    assert(!GoalPage.paused(Nil))
    assertEquals(
      GoalPage.listOf(ResearchSection.Backlog, Goal.Slug("bronze age")).value,
      "/research/backlog?goal=bronze%20age"
    )
    assertEquals(GoalPage.libraryOf(Goal.Slug("g")).value, "/library#goal=g")
    assertEquals(GoalPage.mapOf(Goal.Slug("g")).value, "/explore#goal=g")
