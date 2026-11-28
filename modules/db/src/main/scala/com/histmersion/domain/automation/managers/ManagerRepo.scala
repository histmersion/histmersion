package com.histmersion
package domain.automation.managers

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson.*

import com.histmersion.api.automation.Agents.{PauseReason, RunState}
import com.histmersion.api.automation.AgentJobKinds
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Managers.*

import com.augustnagro.magnum.*
import com.histmersion.domain.automation.managers.SweepAcquireItem
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.util.database.Jsonb
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Pipeline
import com.histmersion.api.automation.Agents
import com.histmersion.domain.automation.Run
import com.histmersion.api.automation.Stopped
import com.histmersion.domain.EnrichSource
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.CodexRetention
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The store of the process managers (V087, docs/research-process-managers.md §2): managers, their work items, hints
  * (and which runs they reached), events, and the agent lease. All state lives in these rows, so a restart picks up
  * where the last process left off. Every state change of an item is a compare-and-set on its status (and, for an
  * evaluation, on the run it evaluates), so a tick that runs twice — or two processes, should the lease ever fail —
  * cannot do a step twice.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the manager tables and their row codecs")
object ManagerRepo:
  type EventCursor = EventCursor.T
  object EventCursor extends Newtype[Long, Pure]
  type ProgressText = ProgressText.T
  object ProgressText extends Newtype[String, Pure]
  export ManagerInfo.TokensToday as SpendCount

  export ManagerEvent.{
    Kind as CountEventsAllKind,
    Kind as CountEventsByInKind,
    Kind as CountEventsKind,
    Kind as EventInKind,
    Kind as EventKind,
    Kind as LastEventOfKind,
    Text as EventInText,
    Text as EventText
  }

  /** An agent run's `jobs.kind`, bound through its newtype's codec. */
  private val AgentRun = AgentJobKinds.Agent.kind

  // the jsonb columns, through their DTOs' codecs ([[Jsonb]]); absent fields are left out when written
  private given configCodec: DbCodec[ManagerConfig] =
    Jsonb.codec(ManagerConfig(), dropNulls = Jsonb.DropNulls.DropNulls)
  private given stateCodec: DbCodec[ItemState] = Jsonb.codec(ItemState(), dropNulls = Jsonb.DropNulls.DropNulls)
  private given holdCodec: DbCodec[Option[Hold]] = Jsonb.optional[Hold](dropNulls = Jsonb.DropNulls.DropNulls)
  private given statusCodec: DbCodec[Option[Manager.Status]] =
    DbCodec[Option[String]].biMap(_.map(Manager.Status.parse), _.map(_.render))
  private given effectCodec: DbCodec[Option[HintEffect]] =
    Jsonb.optional[HintEffect](dropNulls = Jsonb.DropNulls.DropNulls)

  // ── managers ───────────────────────────────────────────────────────────────────────────────────

  final private case class ManagerRow(
      id: ManagerRow.Id,
      kind: ManagerRow.Kind,
      goal: Option[ManagerRow.Goal],
      state: ManagerRow.State,
      pauseRuns: ManagerRow.PauseRuns,
      config: ManagerConfig,
      statusNote: Option[ManagerRow.StatusNote],
      cron: Option[ManagerRow.Cron],
      nextTickAt: Option[ManagerRow.NextTickAt],
      createdAt: ManagerRow.CreatedAt,
      updatedAt: ManagerRow.UpdatedAt
  ) derives DbCodec

  object ManagerRow:
    export ManagerInfo.Id
    export Manager.Kind.Wire as Kind
    export com.histmersion.api.automation.Goal.Slug as Goal
    export Manager.State.Wire as State
    export ManagerInfo.PauseRuns
    type StatusNote = Manager.Status
    export ManagerInfo.{CreatedAt, Cron, NextTickAt, UpdatedAt}

  private val managerSelect =
    sql"""SELECT id, kind, goal_slug, state, pause_runs, config::text, status_note, cron, ${Timestamps.isoSeconds(
        sql"next_tick_at"
      )},
              ${Timestamps.isoSeconds(sql"created_at")}, ${Timestamps.isoSeconds(
        sql"updated_at"
      )} FROM agent_managers"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def toInfo(r: ManagerRow)(using DbCon): ManagerInfo =
    val (usd, tokens) = spendToday(r.id)
    ManagerInfo(
      r.id,
      r.kind,
      r.goal,
      r.state,
      r.pauseRuns,
      r.config,
      ManagerConfig(),
      r.statusNote,
      r.cron,
      r.nextTickAt,
      r.createdAt,
      r.updatedAt,
      counts(r.id),
      ManagerInfo.InFlight(inFlight(r.id).value),
      lastEvent(r.id),
      ManagerInfo.SpentTodayUsd(usd.toDouble),
      tokens
    )

  /** What a manager's runs spent this UTC day (the steps that finished since midnight UTC): metered USD — a plan-billed
    * call is recorded at 0 — and tokens in + out, every call's.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def spendToday(id: ManagerRow.Id)(using DbCon): (BigDecimal, ManagerRepo.SpendCount) =
    sql"""SELECT coalesce(sum(s.cost_usd), 0), coalesce(sum(s.tokens_in + s.tokens_out), 0)::bigint
          FROM agent_steps s JOIN jobs j ON j.id = s.job_id
          WHERE j.manager_id = $id
            AND s.finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"""
      .query[(BigDecimal, SpendCount)]
      .run()
      .head

  def list()(using DbCon): List[ManagerInfo] =
    sql"$managerSelect ORDER BY created_at, id"
      .query[ManagerRow]
      .run()
      .toList
      .map(toInfo(_))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(id: ManagerRow.Id)(using DbCon): Option[ManagerInfo] =
    sql"$managerSelect WHERE id = $id"
      .query[ManagerRow]
      .run()
      .headOption
      .map(toInfo(_))

  /** A new manager (with its cron and first firing, if it has one); `false` when the id is taken. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def create(
      id: ManagerRow.Id,
      kind: Kind,
      goal: Option[ManagerRow.Goal],
      state: State,
      config: ManagerConfig,
      cron: Option[ManagerRow.Cron] = None,
      nextTick: Option[java.time.Instant] = None
  )(using DbCon)(using Creator.New): Boolean =
    createIn(id, kind, goal, state, config, cron, nextTick)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def createIn(
      id: ManagerRow.Id,
      kind: Kind,
      goal: Option[ManagerRow.Goal],
      state: State,
      config: ManagerConfig,
      cron: Option[ManagerRow.Cron],
      nextTick: Option[java.time.Instant]
  )(using DbCon)(using creator: Creator.New): Boolean =
    val next = nextTick.map(_.toString)
    sql"""INSERT INTO agent_managers (id, kind, goal_slug, state, config, cron, next_tick_at, creator)
          VALUES ($id, ${kind.wire}, $goal, ${state.wire}, $config::jsonb, $cron, $next::timestamptz, ${StoredCreator
        .column(creator)}::jsonb)
          ON CONFLICT (id) DO NOTHING""".update.run() > 0

  /** When its cron fires next (None: not scheduled). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setNextTick(id: ManagerRow.Id, next: Option[java.time.Instant])(using DbCon): Boolean =
    val n = next.map(_.toString)
    (sql"""UPDATE agent_managers SET next_tick_at = $n::timestamptz WHERE id = $id""".update.run() > 0)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setState(id: ManagerRow.Id, state: State, pauseRuns: ManagerRow.PauseRuns)(using DbCon): Boolean =
    sql"""UPDATE agent_managers SET state = ${state.wire}, pause_runs = ${pauseRuns.pauseRuns}, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** Its settings and cron; `next` = when the cron fires next (None with no cron). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setConfig(
      id: ManagerRow.Id,
      config: ManagerConfig,
      cron: Option[ManagerRow.Cron],
      next: Option[java.time.Instant]
  )(using DbCon): Boolean =
    setConfigIn(id, config, cron, next)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setConfigIn(
      id: ManagerRow.Id,
      config: ManagerConfig,
      cron: Option[ManagerRow.Cron],
      next: Option[java.time.Instant]
  )(using
      DbCon
  ): Boolean =
    val n = next.map(_.toString)
    sql"""UPDATE agent_managers SET config = $config::jsonb, cron = $cron, next_tick_at = $n::timestamptz, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** The derived status line; written only when it changed (no `updated_at` churn every tick). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setStatusNote(id: ManagerRow.Id, note: ManagerRow.StatusNote)(using DbCon): Boolean =
    val text = note.render
    sql"""UPDATE agent_managers SET status_note = $text
          WHERE id = $id AND status_note IS DISTINCT FROM $text""".update.run() > 0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def delete(id: ManagerRow.Id)(using DbCon): Boolean =
    (sql"DELETE FROM agent_managers WHERE id = $id".update.run() > 0)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def counts(id: ManagerRow.Id)(using DbCon): ItemCounts =
    ItemCounts.of(
      sql"""SELECT status, count(*)::int FROM manager_items WHERE manager_id = $id GROUP BY status"""
        .query[(com.histmersion.api.automation.ItemStatus.Wire, ItemCounts.Count)]
        .run()
        .toList
    )

  /** Its runs not settled yet (queued, running, paused, waiting). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def inFlight(id: ManagerRow.Id)(using DbCon): RowCount =
    sql"""SELECT count(*)::int FROM jobs WHERE kind = $AgentRun AND manager_id = $id
                      AND status IN ('queued', 'running', 'paused', 'waiting')""".query[RowCount].run().head

  /** Its runs that run, or will run again without a person (what a manager's own `maxRuns` counts): queued, running,
    * and paused for a reason that resumes by itself — a restart's `interrupted` and a `stalled` pause (both carry
    * `resume_at`), an `allowance` or `plan-quota` pause. Counting only `queued`/`running` let a restart overshoot
    * (#1803): boot pauses the runs it interrupted, the first tick saw no run and started `maxRuns` new ones, and a
    * minute later the engine resumed the interrupted ones beside them. A pause a person must lift (`requested`,
    * `budget`, `provider`) frees its place — resuming it is that person's call — and so does a run WAITING for a
    * person's approval (#3762 proposal 1: one unanswered approval used to stop a whole manager).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def running(id: ManagerRow.Id)(using DbCon): RowCount =

    sql"SELECT count(*)::int FROM jobs WHERE kind = $AgentRun AND manager_id = $id AND ${occupyingOn(sql"jobs")}"
      .query[RowCount]
      .run()
      .head

  /** Every agent run that occupies a place ([[running]]'s rule, a person's runs too): its pipeline, and the goal of the
    * manager that started it (None: a person's, or a library-wide manager's).
    */
  def occupyingRuns()(using DbCon): List[(Pipeline.Wire, Option[ManagerRow.Goal])] =

    sql"SELECT j.pipeline, m.goal_slug FROM jobs j LEFT JOIN agent_managers m ON m.id = j.manager_id WHERE j.kind = $AgentRun AND ${occupyingOn(sql"j")}"
      .query[(Pipeline.Wire, Option[ManagerRow.Goal])]
      .run()
      .toList

  /** The runs that occupy a place ([[running]]'s rule), as a predicate on table (alias) `t` of `jobs`. A pause for an
    * allowance or a plan pool is told by its problem's case and cause (`error @>` the pause's entry), not by its words.
    */
  private def occupyingOn(t: Frag): Frag =
    sql"($t.status IN ('queued', 'running') OR ($t.status = 'paused' AND ($t.resume_at IS NOT NULL OR $t.error @> ${Stopped.pausedMatch(PauseReason.Allowance).noSpaces}::jsonb OR $t.error @> ${Stopped.pausedMatch(PauseReason.PlanQuota).noSpaces}::jsonb)))"

  /** The item kinds of the last `n` runs the managers started, newest first (what the scheduler's shares count).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def recentKinds(n: PageSize)(using DbCon): List[ItemRow.Kind] =
    sql"""SELECT i.kind FROM jobs j JOIN manager_items i ON i.id = j.manager_item
          WHERE j.kind = $AgentRun ORDER BY j.created_at DESC, j.id DESC LIMIT ${math.max(0, n.value)}"""
      .query[ManagerItem.Kind]
      .run()
      .toList

  /** Per goal: when it last progressed — a run of one of its managers started, settled or was evaluated, a work was
    * ingested or swept for it, or a source was linked to it — as an ISO instant (UTC).
    */
  def lastProgress()(using DbCon): Map[ManagerRow.Goal, ProgressText] =
    sql"""SELECT goal, to_char(max(at) AT TIME ZONE 'UTC', ${Timestamps.IsoSecondsFormat}) FROM (
                 SELECT m.goal_slug AS goal, max(e.at) AS at FROM manager_events e JOIN agent_managers m ON m.id = e.manager_id
                 WHERE m.goal_slug IS NOT NULL AND e.kind IN (${Feed.Started}, ${Feed.Settled}, ${Feed.Evaluated}, ${Feed.Ingested}, ${Feed.Swept}) GROUP BY m.goal_slug
                 UNION ALL
                 SELECT goal_slug, max(created_at) FROM goal_links WHERE kind = 'source' GROUP BY goal_slug) t
               GROUP BY goal"""
      .query[(ManagerRow.Goal, ProgressText)]
      .run()
      .toMap

  /** Per goal: its items a person holds (held, not for an approval and not by the focus). */
  def personHeldByGoal()(using DbCon): Map[Goal.Slug, RowCount] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.goal_slug IS NOT NULL AND i.status = 'held' AND i.hold->>'focus' IS NULL AND i.hold->>'untilApproval' IS NULL
          GROUP BY m.goal_slug""".query[(Goal.Slug, RowCount)].run().toMap

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal()(using DbCon): Map[Goal.Slug, RowCount] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          JOIN jobs j ON j.id = i.run_id
          WHERE m.goal_slug IS NOT NULL AND i.status = 'active' AND j.status IN ('queued', 'running')
          GROUP BY m.goal_slug""".query[(Goal.Slug, RowCount)].run().toMap

  /** Per goal: the outcome of its watcher's acquisition sweep when it is settled. */
  def sweepOutcomes()(using DbCon): Map[Goal.Slug, ItemRow.Outcome] =
    sql"""SELECT m.goal_slug, coalesce(i.outcome, '') FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.goal_slug IS NOT NULL AND i.kind = $AcquisitionSweep AND i.status IN ('done', 'stopped', 'failed')"""
      .query[(Goal.Slug, ManagerItem.Outcome)]
      .run()
      .toMap

  /** Items that wait for a person and hold no place: held (not by the focus: for an approval, or by a person), or
    * active with their run waiting for an approval — of managers that are not stopped.
    */
  def forPerson()(using DbCon): RowCount =
    sql"""SELECT count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          LEFT JOIN jobs j ON j.id = i.run_id
          WHERE m.state <> 'stopped' AND ((i.status = 'held' AND i.hold->>'focus' IS NULL)
            OR (i.status = 'active' AND j.status = 'waiting'))""".query[RowCount].run().head

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  final private case class ItemRow(
      id: ItemRow.Id,
      managerId: ItemRow.ManagerId,
      kind: ItemRow.Kind,
      target: ItemRow.Target,
      status: ItemRow.Status,
      priority: ItemRow.Priority,
      attempts: ItemRow.Attempts,
      failures: ItemRow.Failures,
      runId: Option[Agents.RunInfo.Id],
      evaluatedRun: Option[ItemRow.EvaluatedRun],
      notBefore: Option[ItemRow.NotBefore],
      hold: Option[Hold],
      state: ItemState,
      outcome: Option[ItemRow.Outcome],
      origin: ItemRow.Origin,
      createdAt: ItemRow.CreatedAt,
      updatedAt: ItemRow.UpdatedAt
  ) derives DbCodec

  object ItemRow:
    export ManagerItem.Id
    export ManagerInfo.Id as ManagerId
    export ManagerItem.{Kind, Target}
    export com.histmersion.api.automation.ItemStatus.Wire as Status
    export ManagerItem.{Attempts, Failures, Priority}
    export Agents.RunInfo.Id as EvaluatedRun
    export ManagerItem.{NotBefore, Outcome}
    export com.histmersion.api.automation.Managers.ItemOrigin.Wire as Origin
    export ManagerItem.{CreatedAt, UpdatedAt}

  private val itemSelect =
    sql"""SELECT id, manager_id, kind, target, status, priority, attempts, failures, run_id, evaluated_run,
              ${Timestamps.isoSeconds(sql"not_before")}, hold::text, state::text, outcome, origin, ${Timestamps
        .isoSeconds(sql"created_at")}, ${Timestamps.isoSeconds(sql"updated_at")}
       FROM manager_items"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def toItem(r: ItemRow): ManagerItem =
    ManagerItem(
      r.id,
      r.managerId,
      r.kind,
      r.target,
      r.status,
      r.priority,
      r.attempts,
      r.failures,
      r.runId,
      r.evaluatedRun,
      r.notBefore,
      r.hold,
      r.state,
      r.outcome,
      r.origin,
      r.createdAt,
      r.updatedAt
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def item(id: ItemRow.Id)(using DbCon): Option[ManagerItem] =
    sql"$itemSelect WHERE id = $id".query[ItemRow].run().headOption.map(toItem)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def itemFor(managerId: ManagerRow.Id, kind: ItemRow.Kind, target: ItemRow.Target)(using DbCon): Option[ManagerItem] =

    sql"$itemSelect WHERE manager_id = $managerId AND kind = $kind AND target = $target"
      .query[ItemRow]
      .run()
      .headOption
      .map(toItem)

  /** The open items (pending, held, active) of any manager whose target is `target`, the newest first. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def openFor(target: ItemRow.Target)(using DbCon): List[ManagerItem] =

    sql"$itemSelect WHERE target = $target AND status IN ('pending', 'held', 'active') ORDER BY id DESC"
      .query[ItemRow]
      .run()
      .toList
      .map(toItem)

  /** [[items]] on the caller's connection. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def itemPage(managerId: ManagerRow.Id, status: Option[ItemStatus], offset: Offset, limit: PageSize)(using
      DbCon
  ): (List[ManagerItem], RowCount) =
    val where = sql" WHERE manager_id = $managerId${status.fold(sql"")(s => sql" AND status = ${s.wire}")}"
    val total = sql"SELECT count(*)::int FROM manager_items$where".query[RowCount].run().head
    val rows =
      sql"""$itemSelect$where ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'pending' THEN 1 WHEN 'held' THEN 2 ELSE 3 END,
             priority DESC, coalesce(not_before, '-infinity'::timestamptz), updated_at DESC, id
           OFFSET ${math.max(0, offset.value)} LIMIT ${math.max(1, limit.value)}"""
        .query[ItemRow]
        .run()
        .toList
        .map(toItem)
    (rows, total)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def withStatus(managerId: ManagerRow.Id, status: ItemStatus)(using DbCon): List[ManagerItem] =

    sql"$itemSelect WHERE manager_id = $managerId AND status = ${status.wire} ORDER BY priority DESC, id"
      .query[ItemRow]
      .run()
      .toList
      .map(toItem)

  /** Add an item, or — when it exists — bring it back: `reopen` sets a settled one pending again, and the priority is
    * raised to `priority` (an active one keeps running). Returns the item's id and whether it was new.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def upsert(
      managerId: ManagerRow.Id,
      kind: ItemRow.Kind,
      target: ItemRow.Target,
      origin: ItemOrigin,
      priority: ItemRow.Priority,
      reopen: Boolean,
      initial: ItemState = ItemState()
  )(using DbCon): (ItemRow.Id, Boolean) =
    sql"""INSERT INTO manager_items (manager_id, kind, target, origin, priority, state)
          VALUES ($managerId, $kind, $target, ${origin.wire}, $priority, $initial::jsonb)
          ON CONFLICT (manager_id, kind, target) DO NOTHING RETURNING id""".query[ItemRow.Id].run().headOption match
      case Some(id) => (id, true)
      case None     =>
        val id =
          sql"""SELECT id FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
            .query[ItemRow.Id]
            .run()
            .head
        if reopen then
          Unused(
            sql"""UPDATE manager_items SET
                  status = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 'pending' ELSE status END,
                  attempts = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 0 ELSE attempts END,
                  failures = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 0 ELSE failures END,
                  not_before = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN NULL ELSE not_before END,
                  outcome = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN NULL ELSE outcome END,
                  priority = greatest(priority, $priority), origin = ${origin.wire}, updated_at = now()
                WHERE id = $id""".update.run(),
            Unused.Why.Held
          )
        (id, false)

  /** The pending items whose time has come, the most urgent first: (id, target, priority, not-before). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def pending(managerId: ManagerRow.Id)(using DbCon): List[ManagerItem] =
    withStatus(managerId, ItemStatus.Pending)

  /** Dispatch, in the caller's transaction: pending → active with `run`, one more attempt, the open MAJORs before it in
    * its state. `false` = it was no longer pending (another tick took it).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def dispatchIn(itemId: ItemRow.Id, run: Run.Id, state: ItemState)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = ${ItemStatus.Active.wire}, run_id = $run, attempts = attempts + 1,
                                   state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ${ItemStatus.Pending.wire}""".update.run() > 0

  /** Active items whose run is terminal and not evaluated yet (or whose run is gone): the evaluation queue.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def toEvaluate(managerId: ManagerRow.Id)(using
      DbCon
  ): List[(ManagerItem, Option[(Agents.RunInfo.Status, Option[Problem])])] =
    val rows =
      sql"$itemSelect WHERE manager_id = $managerId AND status = 'active' ORDER BY id"
        .query[ItemRow]
        .run()
        .toList
        .map(toItem)
    rows.flatMap { i =>
      val run = i.runId.flatMap(r =>
        (
          sql"SELECT status, error FROM jobs WHERE id = $r"
            .query[(Agents.RunInfo.Status, Option[NonEmptyVector[Problem]])]
            .run()
            .headOption
            .map((status, error) => (status, error.map(Problems.asOne)))
        )
      )
      run match
        case None => List(i -> None) // its run was deleted
        case Some((st, err))
            if RunState
              .parse(st)
              .exists(_.isTerminal) && !i.evaluatedRun.contains(i.runId.get) =>
          List(i -> Some((st, err)))
        case _ => Nil
    }

  /** Record an evaluation (keyed by the run: a second evaluation of the same run changes nothing). `hold` = the hold it
    * is left in (`held`: waiting for an approval).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def evaluated(
      itemId: ItemRow.Id,
      run: Option[ItemRow.EvaluatedRun],
      status: ItemStatus,
      notBefore: Option[java.time.Instant],
      failures: Option[ItemRow.Failures],
      state: ItemState,
      outcome: Option[ItemRow.Outcome],
      hold: Option[Hold] = None
  )(using DbCon): Boolean =
    val nb = notBefore.map(_.toString)
    val key = run.map(_.value).getOrElse("")
    sql"""UPDATE manager_items SET status = ${status.wire}, evaluated_run = coalesce($run, evaluated_run),
                                   not_before = $nb::timestamptz, failures = coalesce($failures, failures),
                                   state = $state::jsonb, outcome = coalesce($outcome, outcome), hold = $hold::jsonb,
                                   updated_at = now()
          WHERE id = $itemId AND status = 'active'
            AND coalesce(run_id, '') = $key AND evaluated_run IS DISTINCT FROM $run""".update.run() > 0

  /** A person's (or a hint's) change of one item. Each is a CAS on the statuses it may change. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def hold(itemId: ItemRow.Id, h: Hold)(using DbCon): Boolean =
    val hold = Option(h)
    sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
          WHERE id = $itemId AND status IN ('pending', 'held')""".update.run() > 0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def release(itemId: ItemRow.Id)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
          WHERE id = $itemId AND status = 'held'""".update.run() > 0

  // ── the focus's holds (docs/research-process-managers.md "As built: the focus") ─────────────────

  /** Hold a PENDING item for the focus (`h.focus` = its goal); false = it was not pending any more. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def focusHold(itemId: ItemRow.Id, h: Hold)(using DbCon): Boolean =
    val hold = Option(h)
    sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
          WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** Release the focus holds among `ids` (a person's or an approval's hold is never touched); how many. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def focusRelease(ids: List[ItemRow.Id])(using DbCon): RowCount =
    if ids.isEmpty then RowCount(0)
    else
      val arr = ids.map(_.value.toString)
      RowCount(
        sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
            WHERE id = ANY($arr::bigint[]) AND status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()
      )

  /** Release every focus hold (the focus is not in force any more); how many. */
  def focusReleaseAll()(using DbCon): RowCount =
    RowCount(
      sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
          WHERE status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()
    )

  /** The items the focus holds now. */
  def focusHeldCount()(using DbCon): RowCount =

    sql"""SELECT count(*)::int FROM manager_items WHERE status = 'held' AND hold->>'focus' IS NOT NULL"""
      .query[RowCount]
      .run()
      .head

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setPriority(itemId: ItemRow.Id, priority: ItemRow.Priority)(using DbCon): Boolean =
    (
      sql"UPDATE manager_items SET priority = $priority, updated_at = now() WHERE id = $itemId".update.run() > 0
    )

  /** Settle an item that is not active (skip: stopped by a person; a manager's stop: cancelled). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def settle(itemId: ItemRow.Id, status: ItemStatus, outcome: ItemRow.Outcome, from: List[ItemStatus])(using
      DbCon
  ): Boolean =
    val fs = from.map(_.wire)
    sql"""UPDATE manager_items SET status = ${status.wire}, outcome = $outcome, hold = NULL, updated_at = now()
          WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  /** Re-open a settled item: pending again, its run count and failures reset, how often recorded. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def reopen(itemId: ItemRow.Id)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, outcome = NULL,
                                   hold = NULL,
                                   state = jsonb_set(state - 'history', '{reopened}',
                                     to_jsonb(coalesce((state->>'reopened')::int, 0) + 1)),
                                   updated_at = now()
          WHERE id = $itemId AND status IN ('done', 'stopped', 'failed', 'cancelled')""".update.run() > 0

  /** A converged item selected again because it went stale: pending at `priority` (exactly: the lowest), its runs
    * counted from zero, its run history dropped (a new cycle), why in its outcome.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def restale(itemId: ItemRow.Id, priority: ItemRow.Priority, why: ItemRow.Outcome)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, hold = NULL,
                                   priority = $priority, origin = ${ItemOrigin.Selection.wire}, outcome = $why,
                                   state = state - 'history', updated_at = now()
          WHERE id = $itemId AND status = 'done'""".update.run() > 0

  /** A manager's open items (pending, held, active) as (kind, target) — what
    * [[com.histmersion.domain.ManagerPolicy.waitsFor]] reads.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def openKeys(managerId: ManagerRow.Id)(using DbCon): List[(ItemRow.Kind, ItemRow.Target)] =
    sql"""SELECT kind, target FROM manager_items WHERE manager_id = $managerId
          AND status IN ('pending', 'held', 'active')""".query[(ManagerItem.Kind, ManagerItem.Target)].run().toList

  /** Does manager `managerId` have (or ever had) an item of `kind` on `target`? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def hasItem(managerId: ManagerRow.Id, kind: ItemRow.Kind, target: ItemRow.Target)(using DbCon): Boolean =
    sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
      .query[Int]
      .run()
      .nonEmpty

  /** Every open item of a manager cancelled (a stop), its outcome line `why`'s sentence (the outcome is a line of text,
    * as every other outcome an item keeps); returns how many.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def cancelOpen(managerId: ManagerRow.Id, why: Problem)(using DbCon): RowCount =
    val line = why.sentence
    RowCount(
      sql"""UPDATE manager_items SET status = 'cancelled', outcome = $line, updated_at = now()
          WHERE manager_id = $managerId AND status IN ('pending', 'held', 'active')""".update.run()
    )

  /** Back to pending (an active item whose run vanished, or was never submitted). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def requeue(itemId: ItemRow.Id, outcome: ItemRow.Outcome)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', run_id = NULL, outcome = $outcome, updated_at = now()
          WHERE id = $itemId AND status = 'active'""".update.run() > 0

  // ── selection: what the review manager picks by itself ─────────────────────────────────────────

  /** Articles with open MAJORs in their review ledger and no item of this manager yet, the most open MAJORs first;
    * `tags` (all of) and `goal` (linked to it) narrow them.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def openMajorArticles(
      managerId: ManagerRow.Id,
      kind: ItemRow.Kind,
      tags: List[EnrichSource.Tag],
      goal: Option[Goal.Slug],
      limit: PageSize
  )(using
      DbCon
  ): List[(Slug, RowCount)] =
    sql"""SELECT a.slug, m.n FROM articles a
          JOIN review_ledgers l ON l.slug = a.slug
          CROSS JOIN LATERAL (
            SELECT count(*)::int AS n FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major') m
          WHERE m.n > 0 AND a.kind <> 'index'
            AND a.tags @> $tags::text[]
            AND ($goal::text IS NULL OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $goal
                                                 AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind
                            AND i.target = a.slug)
          ORDER BY m.n DESC, a.slug LIMIT ${math.max(1, limit.value)}""".query[(Slug, RowCount)].run().toList

  /** Articles never reviewed (no ledger, no review-article run) and with no item of this manager. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def unreviewedArticles(
      managerId: ManagerRow.Id,
      kind: ItemRow.Kind,
      tags: List[EnrichSource.Tag],
      goal: Option[Goal.Slug],
      limit: PageSize
  )(using
      DbCon
  ): List[Slug] =
    sql"""SELECT a.slug FROM articles a
          WHERE a.kind <> 'index' AND a.tags @> $tags::text[]
            AND NOT EXISTS (SELECT 1 FROM review_ledgers l WHERE l.slug = a.slug)
            AND NOT EXISTS (SELECT 1 FROM jobs j WHERE j.kind = $AgentRun AND j.pipeline = 'review-article'
                            AND j.target_id = a.slug)
            AND ($goal::text IS NULL OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $goal
                                                 AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind
                            AND i.target = a.slug)
          ORDER BY a.updated_at, a.slug LIMIT ${math.max(1, limit.value)}""".query[Slug].run().toList

  /** The review manager's stale candidates: its converged review items (by when they converged: their item's
    * `updated_at`, with the filter hits recorded then), then articles whose ledger holds no open MAJOR and that no item
    * of it drives (by the ledger's `updated_at`) — oldest first, `tags`/`goal` narrowing as in the selection. (slug,
    * converged at, filter hits then, the done item if any).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def staleCandidates(
      managerId: ManagerRow.Id,
      kind: ItemRow.Kind,
      tags: List[EnrichSource.Tag],
      goal: Option[Goal.Slug],
      limit: PageSize
  )(using
      DbCon
  ): List[(Slug, ProgressText, Option[ItemState.FilterHits], Option[ItemRow.Id])] =
    val lim = math.max(1, limit.value)
    val g = goal.map(_.value.trim).getOrElse("")
    val done =
      sql"""SELECT i.target, ${Timestamps.isoSeconds(
          sql"i.updated_at"
        )}, (i.state->>'filterHits')::int, i.id FROM manager_items i
          JOIN articles a ON a.slug = i.target
          WHERE i.manager_id = $managerId AND i.kind = $kind AND i.status = 'done' AND a.tags @> $tags::text[]
            AND ($g = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $g
                                   AND g.kind = 'article' AND g.ref = a.slug))
          ORDER BY i.updated_at, i.id LIMIT $lim"""
        .query[(Slug, ProgressText, Option[ItemState.FilterHits], ItemRow.Id)]
        .run()
        .toList
        .map((s, t, n, id) => (s, t, n, Some(id)))
    val ledgers =
      sql"""SELECT l.slug, ${Timestamps.isoSeconds(
          sql"l.updated_at"
        )} FROM review_ledgers l JOIN articles a ON a.slug = l.slug
          WHERE a.kind <> 'index' AND a.tags @> $tags::text[]
            AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
            AND ($g = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $g
                                   AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind AND i.target = a.slug)
          ORDER BY l.updated_at, l.slug LIMIT $lim"""
        .query[(Slug, ProgressText)]
        .run()
        .toList
        .map((s, t) => (s, t, None, None))
    (done ++ ledgers).sortBy(_._2.value).take(lim)

  /** Articles that record a coverage filter and that no item of this manager has open (pending, held, active), with
    * when their last extension item settled (None: never extended) — the ones never extended first, then the longest
    * ago; `tags`/`goal` narrow as in the selection.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def extendCandidates(
      managerId: ManagerRow.Id,
      tags: List[EnrichSource.Tag],
      goal: Option[Goal.Slug],
      limit: PageSize
  )(using
      DbCon
  ): List[(Slug, Option[ProgressText])] =
    val g = goal.map(_.value.trim).getOrElse("")
    sql"""SELECT a.slug, ${Timestamps.isoSeconds(sql"x.updated_at")} FROM articles a
          LEFT JOIN manager_items x ON x.manager_id = $managerId AND x.kind = $ArticleExtend AND x.target = a.slug
          WHERE a.coverage_filter IS NOT NULL AND a.kind <> 'index' AND a.tags @> $tags::text[]
            AND ($g = '' OR EXISTS (SELECT 1 FROM goal_links gl WHERE gl.goal_slug = $g
                                   AND gl.kind = 'article' AND gl.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.target = a.slug
                            AND i.status IN ('pending', 'held', 'active'))
          ORDER BY x.updated_at NULLS FIRST, a.slug LIMIT ${math.max(1, limit.value)}"""
      .query[(Slug, Option[ProgressText])]
      .run()
      .toList

  // ── the autopilot's views (few queries for every manager at once) ──────────────────────────────

  def light()(using DbCon): List[ManagerLight] =
    sql"""SELECT m.id, m.kind, m.goal_slug, m.state, m.cron, m.config::text, m.status_note,
                      EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = m.id AND i.kind = $PlanGoalItem)
               FROM agent_managers m ORDER BY m.id"""
      .query[ManagerLight]
      .run()
      .toList

  /** Per manager: its open items (pending, held, active), the items it settled since `since`, and when it last recorded
    * an event. A watcher's ingest backlog is not counted: it drains by the day's ingest caps, not at the pace of its
    * runs (it has its own estimate, [[com.histmersion.domain.IngestBacklog.daysToDrain]]).
    */
  def pace(since: java.time.Instant)(using DbCon): Map[ManagerRow.Id, (RowCount, RowCount, Option[ProgressText])] =
    val t = since.toString
    sql"""SELECT m.id,
                      (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> $OaIngest
                         AND i.status IN ('pending', 'held', 'active')),
                      (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> $OaIngest
                         AND i.status IN ('done', 'stopped', 'failed') AND i.updated_at >= $t::timestamptz),
                      (SELECT ${Timestamps.isoSeconds(sql"max(e.at)")} FROM manager_events e WHERE e.manager_id = m.id)
               FROM agent_managers m"""
      .query[(ManagerRow.Id, RowCount, RowCount, Option[ProgressText])]
      .run()
      .toList
      .map((id, o, d, l) => id -> (o, d, l))
      .toMap

  /** Open items of `kind` in manager `id` (pending, held or active) — e.g. a goal's compiles. `focusHeld` = an item the
    * focus holds counts as held (false: as the pending item it really is).
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def openOf(
      managerId: ManagerRow.Id,
      kinds: List[ItemRow.Kind],
      statuses: List[ItemRow.Status],
      focusHeld: Boolean = true
  )(using DbCon): RowCount =

    sql"SELECT count(*)::int FROM manager_items WHERE manager_id = $managerId AND kind = ANY($kinds::text[]) AND status = ANY($statuses::text[])${
        if focusHeld then sql"" else sql" AND NOT (status = 'held' AND hold->>'focus' IS NOT NULL)"
      }"
      .query[RowCount]
      .run()
      .head

  /** Open items on `target` in manager `id`, any kind. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def openOn(managerId: ManagerRow.Id, target: ItemRow.Target)(using DbCon): Boolean =
    (sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND target = $target
                      AND status IN ('pending', 'held', 'active') LIMIT 1""".query[Int].run().nonEmpty)

  /** An article's tags and the goals it is linked to (what a hint's effect matches). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def articleFacets(slug: Slug)(using
      DbCon
  ): (List[EnrichSource.Tag], List[Goal.Slug]) =
    val tags =
      sql"SELECT tags FROM articles WHERE slug = $slug".query[List[EnrichSource.Tag]].run().headOption.getOrElse(Nil)
    val goals =
      sql"SELECT goal_slug FROM goal_links WHERE kind = 'article' AND ref = $slug"
        .query[Goal.Slug]
        .run()
        .toList
    (tags, goals)

  /** Is there a source with this citekey or DOI (a hold waiting for it is released)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def sourceExists(ref: Citekey)(using DbCon): Boolean =
    val r = ref.value.trim
    val doi = r.toLowerCase.stripPrefix("https://doi.org/").stripPrefix("doi:")
    (
      sql"""SELECT 1 FROM sources WHERE citekey = $r OR lower(doi) = $doi LIMIT 1""".query[Int].run().nonEmpty
    )

  // ── the watchers' ingest backlog (`oa-ingest` items) ───────────────────────────────────────────

  def backlogPendingIn()(using DbCon): List[ManagerItem] =
    sql"""$itemSelect WHERE kind = $OaIngest AND status = 'pending'
          AND manager_id IN (SELECT id FROM agent_managers WHERE kind = 'watch' AND state = 'active')
        ORDER BY priority DESC, id""".query[ItemRow].run().toList.map(toItem)

  def backlogOpenIn()(using DbCon): List[ManagerItem] =
    sql"$itemSelect WHERE kind = $OaIngest AND status IN ('pending', 'held', 'active') ORDER BY id"
      .query[ItemRow]
      .run()
      .toList
      .map(toItem)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def backlogOpenForIn(doi: Doi)(using DbCon): Option[ManagerItem] =
    sql"$itemSelect WHERE kind = $OaIngest AND target = $doi AND status IN ('pending', 'held', 'active') ORDER BY id LIMIT 1"
      .query[ItemRow]
      .run()
      .headOption
      .map(toItem)

  /** Per watcher: its backlog items by status (pending, held) — what the status lines count. */
  def backlogCounts()(using DbCon): Map[ManagerRow.Id, (RowCount, RowCount)] =
    sql"""SELECT manager_id, count(*) FILTER (WHERE status = 'pending')::int, count(*) FILTER (WHERE status = 'held')::int
          FROM manager_items WHERE kind = $OaIngest AND status IN ('pending', 'held') GROUP BY manager_id"""
      .query[(ManagerRow.Id, RowCount, RowCount)]
      .run()
      .toList
      .map((m, p, h) => m -> (p, h))
      .toMap

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def countEventsByIn(kind: ManagerRepo.CountEventsByInKind, since: java.time.Instant)(using
      DbCon
  ): Map[ManagerInfo.Id, RowCount] =
    val at = since.toString
    sql"""SELECT manager_id, count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz
          GROUP BY manager_id""".query[(ManagerInfo.Id, RowCount)].run().toMap

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setItemStateIn(itemId: ItemRow.Id, state: ItemState, from: List[ItemStatus])(using DbCon): Boolean =
    val fs = from.map(_.wire)
    sql"""UPDATE manager_items SET state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def backlogRetryIn(
      itemId: ItemRow.Id,
      failures: ItemRow.Failures,
      notBefore: java.time.Instant,
      outcome: ItemRow.Outcome
  )(using
      DbCon
  ): Boolean =
    val nb = notBefore.toString
    sql"""UPDATE manager_items SET failures = $failures, attempts = attempts + 1, not_before = $nb::timestamptz,
                                   outcome = $outcome, updated_at = now()
          WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** The citekey of the source holding this DOI, if one does. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def citekeyOfDoi(doi: Doi)(using DbCon): Option[Citekey] =
    val d = doi.value.trim.toLowerCase
    (
      sql"SELECT citekey FROM sources WHERE lower(doi) = $d ORDER BY citekey LIMIT 1".query[Citekey].run().headOption
    )

  /** One open acquisition item a WATCHER'S SWEEP created, found by structure, not by its wording: an
    * `acquisition-sweep` run dispatched by a watcher (`jobs.manager_id`) picked the DOI (its `rank` step's checked
    * answer), the item was created while that run's `queue` step ran (the only step that adds acquisition items), and
    * the run linked it to its goal by that DOI (`goal_links` kind `queue-item`, note = the DOI). A person's item, a
    * plan's, a currency check's match none of the three. `isOa` = the ranking marked the work open access.
    */

  def sweepAcquireItemsIn()(using DbCon): List[SweepAcquireItem] =
    sql"""WITH picks AS (
              SELECT j.id AS job, j.target_id AS goal, j.manager_id, lower(p->'candidate'->>'doi') AS doi,
                     coalesce((p->'candidate'->>'isOa')::boolean, false) AS oa, p->'candidate'->>'title' AS title,
                     (p->'candidate'->>'year')::int AS year,
                     ARRAY(SELECT jsonb_array_elements_text(coalesce(p->'candidate'->'authors', '[]'::jsonb))) AS authors,
                     p->'candidate'->>'venue' AS venue, p->>'why' AS why
              FROM jobs j
              JOIN agent_steps s ON s.job_id = j.id AND s.name = 'rank' AND s.status = 'done'
              CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(s.output) = 'array' THEN s.output
                                                            ELSE '[]'::jsonb END) p
              WHERE j.kind = $AgentRun AND j.pipeline = 'acquisition-sweep' AND j.manager_id LIKE 'watch:%')
            SELECT DISTINCT ON (q.id, p.goal) q.id, q.text, q.topic, p.goal, p.manager_id, p.job, p.doi, p.oa, p.title,
                   p.year, p.authors, p.venue, p.why
            FROM queue_items q
            JOIN goal_links gl ON gl.kind = 'queue-item' AND gl.ref = q.id::text
            JOIN picks p ON p.goal = gl.goal_slug AND lower(gl.note) = p.doi
            JOIN agent_steps qs ON qs.job_id = p.job AND qs.name = 'queue' AND qs.finished_at IS NOT NULL
                               AND q.created_at BETWEEN qs.started_at - interval '2 seconds'
                                                    AND qs.finished_at + interval '2 seconds'
            WHERE q.list = 'acquire' AND q.status = 'open'
            ORDER BY q.id, p.goal, p.oa DESC, p.job""".query[SweepAcquireItem].run().toList

  /** How many acquisition items are open. */
  def openAcquireCount(using DbCon): RowCount =
    (
      sql"SELECT count(*)::int FROM queue_items WHERE list = 'acquire' AND status = 'open'".query[RowCount].run().head
    )

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[ManagerEvent] = DbCodec.derived

  private val eventSelect =
    sql"SELECT id, manager_id, item_id, run_id, ${Timestamps.isoSeconds(sql"at")}, kind, text FROM manager_events"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def eventIn(
      managerId: ManagerRow.Id,
      itemId: Option[ItemRow.Id],
      runId: Option[Agents.RunInfo.Id],
      kind: ManagerRepo.EventInKind,
      text: ManagerRepo.EventInText
  )(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO manager_events (manager_id, item_id, run_id, kind, text)
                  VALUES ($managerId, $itemId, $runId, $kind, ${text.value.take(2000)})""".update.run()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def event(
      managerId: ManagerRow.Id,
      itemId: Option[ItemRow.Id],
      runId: Option[Agents.RunInfo.Id],
      kind: ManagerRepo.EventKind,
      text: ManagerRepo.EventText
  )(using DbCon): Unit =
    eventIn(managerId, itemId, runId, kind, text)

  /** Newest first; `before` = only events older than that id. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def events(managerId: ManagerRow.Id, before: Option[EventCursor], limit: PageSize)(using DbCon): List[ManagerEvent] =
    sql"$eventSelect WHERE manager_id = $managerId${before.fold(sql"")(b => sql" AND id < $b")} ORDER BY id DESC LIMIT ${math.max(1, limit.value)}"
      .query[ManagerEvent]
      .run()
      .toList

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def lastEvent(managerId: ManagerRow.Id)(using DbCon): Option[ManagerEvent] =
    events(managerId, None, PageSize(1)).headOption

  /** How many events of `kind` a manager has recorded since `since` (a watcher's ingests today). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def countEvents(managerId: ManagerRow.Id, kind: ManagerRepo.CountEventsKind, since: java.time.Instant)(using
      DbCon
  ): RowCount =
    val at = since.toString
    (sql"""SELECT count(*)::int FROM manager_events WHERE manager_id = $managerId AND kind = $kind
                      AND at >= $at::timestamptz""".query[RowCount].run().head)

  /** How many events of `kind` ALL managers recorded since `since` (every watcher's ingests today: the autopilot's
    * shared cap).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def countEventsAll(kind: ManagerRepo.CountEventsAllKind, since: java.time.Instant)(using DbCon): RowCount =
    val at = since.toString

    sql"""SELECT count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz"""
      .query[RowCount]
      .run()
      .head

  /** The newest event of `kind` (a starved note is logged once per change). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def lastEventOf(managerId: ManagerRow.Id, kind: ManagerRepo.LastEventOfKind)(using DbCon): Option[ManagerEvent] =

    sql"$eventSelect WHERE manager_id = $managerId AND kind = $kind ORDER BY id DESC LIMIT 1"
      .query[ManagerEvent]
      .run()
      .headOption

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pruneEvents(days: CodexRetention.KeepDays)(using DbCon): RowCount =
    if days.value <= 0 then RowCount(0)
    else RowCount(sql"DELETE FROM manager_events WHERE at < now() - make_interval(days => $days)".update.run())

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  final private case class HintRow(
      id: HintRow.Id,
      managerId: HintRow.ManagerId,
      itemId: Option[HintRow.ItemId],
      text: HintRow.Text,
      effect: Option[HintEffect],
      author: HintRow.Author,
      standing: HintRow.Standing,
      createdAt: HintRow.CreatedAt,
      retiredAt: Option[HintRow.RetiredAt],
      uses: HintRow.Uses,
      proposal: Option[HintRow.Proposal],
      proposed: Option[HintEffect]
  ) derives DbCodec

  object HintRow:
    export ManagerHint.Id
    export ManagerInfo.Id as ManagerId
    export ManagerHint.{Author, CreatedAt, ItemId, Proposal, RetiredAt, Standing, Text, Uses}

  /** A hint, with the newest OPEN `hint-effect` approval about it (a model's proposed action waiting for a person) and
    * that action.
    */
  private val hintSelect =
    sql"""SELECT h.id, h.manager_id, h.item_id, h.text, h.effect::text, h.author, h.standing, ${Timestamps.isoSeconds(
        sql"h.created_at"
      )},
              ${Timestamps.isoSeconds(
        sql"h.retired_at"
      )}, (SELECT count(*)::int FROM manager_hint_uses u WHERE u.hint_id = h.id),
              p.id, (p.proposal->'effect')::text
       FROM manager_hints h
       LEFT JOIN LATERAL (SELECT a.id, a.proposal FROM approvals a
                          WHERE a.kind = 'hint-effect' AND a.state = 'open' AND a.subject = h.manager_id
                            AND (a.proposal->>'hint')::bigint = h.id ORDER BY a.id DESC LIMIT 1) p ON true"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def toHint(r: HintRow): ManagerHint =
    ManagerHint(
      r.id,
      r.managerId,
      r.itemId,
      r.text,
      r.effect,
      r.author,
      r.standing,
      r.createdAt,
      r.retiredAt,
      r.uses,
      r.proposal,
      r.proposed
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def addHint(
      managerId: HintRow.ManagerId,
      itemId: Option[HintRow.ItemId],
      text: HintRow.Text,
      effect: Option[HintEffect],
      author: HintRow.Author,
      standing: HintRow.Standing
  )(using DbCon)(using creator: Creator.New): ManagerHint =
    val id =
      sql"""INSERT INTO manager_hints (manager_id, item_id, text, effect, author, standing, creator)
            VALUES ($managerId, $itemId, $text, $effect::jsonb, $author, ${standing.standing}, ${StoredCreator.column(
          creator
        )}::jsonb) RETURNING id"""
        .query[HintRow.Id]
        .run()
        .head
    hint(id).get

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def hint(id: HintRow.Id)(using DbCon): Option[ManagerHint] =
    (sql"$hintSelect WHERE h.id = $id".query[HintRow].run().headOption.map(toHint))

  /** A manager's hints, oldest first; `live` = only the ones not retired. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def hints(managerId: ManagerRow.Id, live: Boolean)(using DbCon): List[ManagerHint] =
    sql"$hintSelect WHERE h.manager_id = $managerId${
        if live then sql" AND h.retired_at IS NULL" else sql""
      } ORDER BY h.id".query[HintRow].run().toList.map(toHint)

  /** The action a hint carries now (a model's proposal a person approved). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setHintEffect(id: HintRow.Id, effect: Option[HintEffect])(using DbCon): Boolean =
    (sql"UPDATE manager_hints SET effect = $effect::jsonb WHERE id = $id".update.run() > 0)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def retireHint(managerId: ManagerRow.Id, id: HintRow.Id)(using DbCon): Boolean =
    sql"""UPDATE manager_hints SET retired_at = now() WHERE id = $id AND manager_id = $managerId
          AND retired_at IS NULL""".update.run() > 0

  /** In the dispatch transaction: the hints a run was given, and the once-hints among them retired. */
  def usedIn(hints: List[ManagerHint], runId: Run.Id)(using DbCon): Unit =
    hints.foreach { h =>
      Unused(
        sql"INSERT INTO manager_hint_uses (hint_id, run_id) VALUES (${h.id}, $runId) ON CONFLICT DO NOTHING".update
          .run(),
        Unused.Why.Idempotent
      )
      if !h.standing.standing then
        Unused(
          sql"UPDATE manager_hints SET retired_at = now() WHERE id = ${h.id} AND retired_at IS NULL".update.run(),
          Unused.Why.Idempotent
        )
    }
