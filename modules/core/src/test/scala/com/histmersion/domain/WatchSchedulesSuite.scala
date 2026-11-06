package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Goal.Plans.{GoalSchedule, ScheduleKind}
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.WatchSchedules.*

import java.time.Instant

/** Goal schedules into watchers (phase 3): what a goal's `goal_schedules` rows become, and how a watcher reads as the
  * schedules the goal surfaces still show.
  */
class WatchSchedulesSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-17T12:00:00Z")
  private def sch(
      k: ScheduleKind,
      cron: GoalSchedule.Cron,
      on: GoalSchedule.Enabled = GoalSchedule.Enabled(true),
      usd: Option[GoalSchedule.BudgetUsd] = None,
      next: Option[GoalSchedule.NextRunAt] = None
  ) =
    GoalSchedule(com.histmersion.api.automation.Goal.Slug("tin"), k, cron, on, usd, next)

  test(
    "a new watcher: the enabled schedules' kinds, the cron that fires most often (its next firing), the acquisition budget"
  ):
    val p = migrate(
      None,
      List(
        sch(ScheduleKind.Currency, GoalSchedule.Cron("@daily"), usd = Some(GoalSchedule.BudgetUsd(0.1)), next = Some(GoalSchedule.NextRunAt("2026-10-18T00:00:00Z"))),
        sch(ScheduleKind.Acquisition, GoalSchedule.Cron("@monthly"), usd = Some(GoalSchedule.BudgetUsd(0.25)), next = Some(GoalSchedule.NextRunAt("2026-11-01T00:00:00Z")))
      ),
      now
    )
    assertEquals(p.config.sweeps, Some(List("acquisition", "currency")))
    assertEquals((p.cron, p.nextTick, p.config.budgetUsd), (Some("@daily"), Some("2026-10-18T00:00:00Z"), Some(0.25)))
    assert(
      p.notes.exists(
        _.value.contains("one cron for both: `@daily` (fires most often) over the acquisition schedule's `@monthly`")
      ),
      p.notes
    )

  test(
    "a disabled schedule is not swept, its cron kept only when nothing else is there; an empty watcher sweeps nothing"
  ):
    val p =
      migrate(None, List(sch(ScheduleKind.Currency, GoalSchedule.Cron("@weekly"), on = GoalSchedule.Enabled(false), next = Some(GoalSchedule.NextRunAt("2026-10-18T00:00:00Z")))), now)
    assertEquals((p.config.sweeps, p.cron, p.nextTick), (Some(Nil), Some("@weekly"), None))
    assert(p.notes.exists(_.value.contains("was off: not swept")), p.notes)
    assertEquals(Sweep.of(p.config), Nil)

  test("an existing watcher keeps its own cron, next firing, sweeps and budget; it gains the enabled kinds"):
    val e = Existing(
      ManagerConfig(sweeps = Some(List(com.histmersion.api.automation.Managers.Sweep.Wire("currency"))), budgetUsd = Some(ManagerConfig.BudgetUsd(1.0))),
      Some(com.histmersion.domain.Cron.Text("@weekly")),
      Some(com.histmersion.domain.ManagerPolicy.InstantText("2026-10-18T00:00:00Z"))
    )
    val p = migrate(Some(e), List(sch(ScheduleKind.Acquisition, GoalSchedule.Cron("@daily"), usd = Some(GoalSchedule.BudgetUsd(0.2)))), now)
    assertEquals(
      (p.config.sweeps, p.cron, p.nextTick, p.config.budgetUsd),
      (Some(List("acquisition", "currency")), Some("@weekly"), Some("2026-10-18T00:00:00Z"), Some(1.0))
    )
    assert(p.notes.exists(_.value.contains("its cron `@weekly` kept over the acquisition schedule's `@daily`")), p.notes)

  test("view: one schedule per sweep, the watcher's cron and next firing; paused says how to make it fire"):
    val m = ManagerInfo(
      watchId(com.histmersion.api.automation.Goal.Ref("tin")),
      com.histmersion.api.automation.Managers.ManagerInfo.Kind("watch"),
      Some(com.histmersion.api.automation.Goal.Slug("tin")),
      State.Paused.wire,
      config = ManagerConfig(sweeps = Some(List(com.histmersion.api.automation.Managers.Sweep.Wire("currency"))), budgetUsd = Some(ManagerConfig.BudgetUsd(0.5))),
      cron = Some(com.histmersion.api.automation.Managers.ManagerInfo.Cron("@daily")),
      nextTickAt = Some(com.histmersion.api.automation.Managers.ManagerInfo.NextTickAt("2026-10-18T00:00:00Z"))
    )
    val v = view(m, Nil)
    assertEquals(
      v.map(s => (s.goal, s.kind, s.cron, s.enabled, s.budgetUsd, s.nextRunAt)),
      List(("tin", ScheduleKind.Currency, "@daily", true, Some(0.5), Some("2026-10-18T00:00:00Z")))
    )
    assert(v.head.lastNote.exists(_.value.contains("paused")), v.head.lastNote)
    val it = ManagerItem(
      com.histmersion.api.automation.Managers.ManagerItem.Id(1),
      m.id,
      ManagerItem.Kind(CurrencyCheck),
      com.histmersion.api.automation.Managers.ManagerItem.Target("tin"),
      com.histmersion.api.automation.Managers.ManagerItem.Status("done"),
      runId = Some(com.histmersion.api.automation.Agents.RunInfo.Id("run-1")),
      outcome = Some(com.histmersion.api.automation.Managers.ManagerItem.Outcome("currency-check goal tin: …")),
      updatedAt = com.histmersion.api.automation.Managers.ManagerItem.UpdatedAt("2026-10-17T00:00:00Z")
    )
    val active = view(m.modify(_.state).setTo(State.Active.wire).modify(_.config).setTo(ManagerConfig()), List(it))
    assertEquals(active.map(_.kind), List(ScheduleKind.Acquisition, ScheduleKind.Currency))
    assertEquals(active(1).lastRun, Some("run-1"))
    assert(active(1).lastNote.exists(_.value.contains("currency-check goal tin")), active(1).lastNote)
    assertEquals(view(m.modify(_.goal).setTo(None), Nil), Nil)
