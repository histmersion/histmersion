package com.histmersion.core

import com.histmersion.core.GoalPlans.{GoalSchedule, ScheduleKind}
import com.histmersion.core.Managers.*

import java.time.Instant

/** Goal schedules are research-area watchers since phase 3 (docs/research-process-managers.md §4 (c)):
  * the pure half of moving a `goal_schedules` row into its goal's `watch:<goal>` manager, and of showing
  * a watcher as the schedules the goal surfaces (REST, MCP `goal_schedule`, CLI, the goal window, the
  * bundle) still speak of. A schedule kind is a watcher's sweep (`currency` ↔ `currency-check`,
  * `acquisition` ↔ `acquisition-sweep`); a watcher has ONE cron for its sweeps. */
object WatchSchedules:

  def sweepOf(k: ScheduleKind): Sweep = k match
    case ScheduleKind.Currency    => Sweep.Currency
    case ScheduleKind.Acquisition => Sweep.Acquisition

  def kindOf(s: Sweep): ScheduleKind = s match
    case Sweep.Currency    => ScheduleKind.Currency
    case Sweep.Acquisition => ScheduleKind.Acquisition

  /** What a watcher has before the migration (None: it does not exist yet). */
  final case class Existing(config: ManagerConfig, cron: Option[String], nextTick: Option[String])

  /** The watcher a goal's schedules become: its config (sweeps, per-run budget), its cron and next
    * firing, and what was carried over or left out (the migration's event and log line). */
  final case class Plan(config: ManagerConfig, cron: Option[String], nextTick: Option[String], notes: List[String])

  /** A goal's schedules into its watcher. The watcher's own settings win (an existing one keeps its cron;
    * as a bundle import keeps this collection's schedules, #881): its sweeps gain the ENABLED schedules'
    * kinds; a disabled schedule is not swept (noted). With no cron of its own it takes the enabled
    * schedules' cron — when they differ, the one that fires most often (no sweep runs less often than it
    * did), the other noted — and that schedule's next firing. The per-run budget is the acquisition
    * schedule's (the currency check calls no model), else the currency one's, unless the watcher has one.
    * `now` only ranks two crons by how often they fire. */
  def migrate(existing: Option[Existing], schedules: List[GoalSchedule], now: Instant): Plan =
    val ordered = schedules.sortBy(_.kind.ordinal)
    val on      = ordered.filter(_.enabled)
    val off     = ordered.filterNot(_.enabled)
    val base    = existing.map(_.config).getOrElse(ManagerConfig(sweeps = Some(Nil)))
    val had     = existing.fold(List.empty[Sweep])(e => Sweep.of(e.config))
    val sweeps  = Sweep.values.toList.filter(s => had.contains(s) || on.exists(x => sweepOf(x.kind) == s))
    def gap(s: GoalSchedule): Long = Cron.parse(s.cron).toOption.flatMap(_.shortestGapMinutes(now)).getOrElse(Long.MaxValue)
    val pick    = (if on.nonEmpty then on else ordered).sortBy(s => (gap(s), s.kind.ordinal)).headOption
    val keepOwn = existing.flatMap(_.cron).isDefined
    val cron    = existing.flatMap(_.cron).orElse(pick.map(_.cron))
    val next    = if keepOwn then existing.flatMap(_.nextTick) else pick.flatMap(s => Option.when(s.enabled)(s.nextRunAt).flatten)
    val budget  = base.budgetUsd.orElse(ordered.find(_.kind == ScheduleKind.Acquisition).flatMap(_.budgetUsd))
      .orElse(ordered.flatMap(_.budgetUsd).headOption)
    val notes =
      on.map(s => s"${s.kind.wire} schedule `${s.cron}` → the ${sweepOf(s.kind).itemKind} sweep") ++
        off.map(s => s"${s.kind.wire} schedule `${s.cron}` was off: not swept") ++
        (if keepOwn then ordered.filter(s => !existing.flatMap(_.cron).contains(s.cron))
           .map(s => s"its cron `${existing.flatMap(_.cron).getOrElse("")}` kept over the ${s.kind.wire} schedule's `${s.cron}`")
         else on.filter(s => pick.exists(p => p.kind != s.kind && p.cron != s.cron))
           .map(s => s"one cron for both: `${pick.get.cron}` (fires most often) over the ${s.kind.wire} schedule's `${s.cron}`"))
    Plan(base.copy(sweeps = Some(sweeps.map(_.wire)), budgetUsd = budget), cron, next, notes)

  /** A watcher as the goal surfaces show its schedules: one per sweep it runs, with its cron, its next
    * firing, its last run (the item's) and a note saying whether it is paused. */
  def view(m: ManagerInfo, items: List[ManagerItem]): List[GoalSchedule] =
    m.goal.toList.flatMap { g =>
      Sweep.of(m.config).map { s =>
        val it = items.find(_.kind == s.itemKind)
        val state = State.parse(m.state).getOrElse(State.Paused)
        val note = state match
          case State.Active  => s"watcher ${m.id}" + it.flatMap(_.outcome).fold("")(o => s": $o")
          case State.Paused  => s"watcher ${m.id} is paused: resume it (Managers) for this schedule to fire"
          case State.Stopped => s"watcher ${m.id} is stopped"
        GoalSchedule(g, kindOf(s), m.cron.getOrElse(""), enabled = m.cron.isDefined, m.config.budgetUsd,
          m.nextTickAt, it.filter(_.runId.isDefined).map(_.updatedAt), it.flatMap(_.runId),
          Some(note.take(500)))
      }
    }
