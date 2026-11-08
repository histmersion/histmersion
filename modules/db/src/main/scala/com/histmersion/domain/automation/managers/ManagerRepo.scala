package com.histmersion.domain.automation.managers
import com.histmersion.api.automation.Agents.{JobKind, PauseReason, RunState}
import com.histmersion.api.automation.Managers.*

import com.augustnagro.magnum.*
import com.histmersion.domain.automation.managers.SweepAcquireItem
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

/** The store of the process managers (V087, docs/research-process-managers.md §2): managers, their work items, hints
  * (and which runs they reached), events, and the agent lease. All state lives in these rows, so a restart picks up
  * where the last process left off. Every state change of an item is a compare-and-set on its status (and, for an
  * evaluation, on the run it evaluates), so a tick that runs twice — or two processes, should the lease ever fail —
  * cannot do a step twice.
  */
object ManagerRepo:
  type CancelReason = CancelReason.T
  object CancelReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventCursor = EventCursor.T
  object EventCursor extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressText = ProgressText.T
  object ProgressText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleReason = RestaleReason.T
  object RestaleReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export ManagerInfo.{TokensToday as SpendCount}

  export com.histmersion.domain.automation.Goal.{Slug as ArticleFacetsResultPart2}
  export ManagerInfo.{Id as CountEventsByInResultPart1}
  export ManagerEvent.{Kind as CountEventsByInKind, Kind as EventInKind, Text as EventInText, Kind as EventKind, Text as EventText, Kind as CountEventsKind, Kind as CountEventsAllKind, Kind as LastEventOfKind}

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: String) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

  private def raw(text: String): Sql = Sql.raw(Sql.RawText(text))
  private def of(text: String, ps: Param*): Sql = Sql.of(Sql.OfText(text), ps*)
  private def str(x: String): Param = Param.S(Param.S.V(x))
  private def strs(xs: List[String]): Param = Param.A(xs.map(x => Param.A.V(x)))
  private def lng(x: Long): Param = Param.L(Param.L.V(x))
  private def int(x: Int): Param = Param.I(Param.I.V(x))

  // the jsonb columns, through their DTOs' codecs ([[Jsonb]]); absent fields are left out when written
  private given configCodec: DbCodec[ManagerConfig] = Jsonb.codec(ManagerConfig(), dropNulls = com.histmersion.util.database.Jsonb.DropNulls(true))
  private given stateCodec: DbCodec[ItemState] = Jsonb.codec(ItemState(), dropNulls = com.histmersion.util.database.Jsonb.DropNulls(true))
  private given holdCodec: DbCodec[Option[Hold]] = Jsonb.optional[Hold](dropNulls = com.histmersion.util.database.Jsonb.DropNulls(true))
  private given effectCodec: DbCodec[Option[HintEffect]] = Jsonb.optional[HintEffect](dropNulls = com.histmersion.util.database.Jsonb.DropNulls(true))

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
    type Id = ManagerInfo.Id
    val Id: ManagerInfo.Id.type = ManagerInfo.Id
    type Kind = ManagerInfo.Kind
    val Kind: ManagerInfo.Kind.type = ManagerInfo.Kind
    type Goal = com.histmersion.api.automation.Goal.Slug
    val Goal: com.histmersion.api.automation.Goal.Slug.type = com.histmersion.api.automation.Goal.Slug
    type State = ManagerInfo.State
    val State: ManagerInfo.State.type = ManagerInfo.State
    type PauseRuns = ManagerInfo.PauseRuns
    val PauseRuns: ManagerInfo.PauseRuns.type = ManagerInfo.PauseRuns
    type StatusNote = ManagerInfo.StatusNote
    val StatusNote: ManagerInfo.StatusNote.type = ManagerInfo.StatusNote
    type Cron = ManagerInfo.Cron
    val Cron: ManagerInfo.Cron.type = ManagerInfo.Cron
    type NextTickAt = ManagerInfo.NextTickAt
    val NextTickAt: ManagerInfo.NextTickAt.type = ManagerInfo.NextTickAt
    type CreatedAt = ManagerInfo.CreatedAt
    val CreatedAt: ManagerInfo.CreatedAt.type = ManagerInfo.CreatedAt
    type UpdatedAt = ManagerInfo.UpdatedAt
    val UpdatedAt: ManagerInfo.UpdatedAt.type = ManagerInfo.UpdatedAt

  private val managerSelect =
    raw(s"""SELECT id, kind, goal_slug, state, pause_runs, config::text, status_note, cron, ${at("next_tick_at")},
              ${at("created_at")}, ${at("updated_at")} FROM agent_managers""")

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
  def spendToday(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): (BigDecimal, com.histmersion.domain.automation.managers.ManagerRepo.SpendCount) =
    sql"""SELECT coalesce(sum(s.cost_usd), 0), coalesce(sum(s.tokens_in + s.tokens_out), 0)::bigint
          FROM agent_steps s JOIN jobs j ON j.id = s.job_id
          WHERE j.manager_id = $id
            AND s.finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"""
      .query[(BigDecimal, SpendCount)]
      .run()
      .head

  def list()(using DbCon): List[ManagerInfo] =
    (managerSelect ++ of(" ORDER BY created_at, id")).frag
      .query[ManagerRow]
      .run()
      .toList
      .map(toInfo(_))

  def get(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): Option[ManagerInfo] =
    (managerSelect ++ of(" WHERE id = ?", str(id.value))).frag
      .query[ManagerRow]
      .run()
      .headOption
      .map(toInfo(_))

  /** A new manager (with its cron and first firing, if it has one); `false` when the id is taken. */
  def create(
      id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id,
      kind: Kind,
      goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Goal],
      state: State,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Cron] = None,
      nextTick: Option[java.time.Instant] = None
  )(using DbCon): Boolean =
    createIn(id, kind, goal, state, config, cron, nextTick)

  def createIn(
      id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id,
      kind: Kind,
      goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Goal],
      state: State,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Cron],
      nextTick: Option[java.time.Instant]
  )(using DbCon): Boolean =
    val next = nextTick.map(_.toString)
    sql"""INSERT INTO agent_managers (id, kind, goal_slug, state, config, cron, next_tick_at)
          VALUES ($id, ${kind.wire}, $goal, ${state.wire}, $config::jsonb, $cron, $next::timestamptz)
          ON CONFLICT (id) DO NOTHING""".update.run() > 0

  /** When its cron fires next (None: not scheduled). */
  def setNextTick(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, next: Option[java.time.Instant])(using DbCon): Boolean =
    val n = next.map(_.toString)
    (sql"""UPDATE agent_managers SET next_tick_at = $n::timestamptz WHERE id = $id""".update.run() > 0)

  def setState(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, state: State, pauseRuns: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.PauseRuns)(using DbCon): Boolean =
    sql"""UPDATE agent_managers SET state = ${state.wire}, pause_runs = $pauseRuns, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** Its settings and cron; `next` = when the cron fires next (None with no cron). */
  def setConfig(
      id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Cron],
      next: Option[java.time.Instant]
  )(using DbCon): Boolean =
    setConfigIn(id, config, cron, next)

  def setConfigIn(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, config: ManagerConfig, cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Cron], next: Option[java.time.Instant])(using
      DbCon
  ): Boolean =
    val n = next.map(_.toString)
    sql"""UPDATE agent_managers SET config = $config::jsonb, cron = $cron, next_tick_at = $n::timestamptz, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** The derived status line; written only when it changed (no `updated_at` churn every tick). */
  def setStatusNote(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, note: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.StatusNote)(using DbCon): Boolean =
    sql"""UPDATE agent_managers SET status_note = $note
          WHERE id = $id AND status_note IS DISTINCT FROM $note""".update.run() > 0

  def delete(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): Boolean =
    (sql"DELETE FROM agent_managers WHERE id = $id".update.run() > 0)

  def counts(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): ItemCounts =
    ItemCounts.of(
      sql"""SELECT status, count(*)::int FROM manager_items WHERE manager_id = $id GROUP BY status"""
        .query[(com.histmersion.api.automation.ItemStatus.Wire, ItemCounts.Count)]
        .run()
        .toList
    )

  /** Its runs not settled yet (queued, running, paused, waiting). */
  def inFlight(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): com.histmersion.RowCount =
    sql"""SELECT count(*)::int FROM jobs WHERE kind = $JobKind AND manager_id = $id
                      AND status IN ('queued', 'running', 'paused', 'waiting')""".query[com.histmersion.RowCount].run().head

  /** Its runs that run, or will run again without a person (what a manager's own `maxRuns` counts): queued, running,
    * and paused for a reason that resumes by itself — a restart's `interrupted` and a `stalled` pause (both carry
    * `resume_at`), an `allowance` or `plan-quota` pause. Counting only `queued`/`running` let a restart overshoot
    * (#1803): boot pauses the runs it interrupted, the first tick saw no run and started `maxRuns` new ones, and a
    * minute later the engine resumed the interrupted ones beside them. A pause a person must lift (`requested`,
    * `budget`, `provider`) frees its place — resuming it is that person's call — and so does a run WAITING for a
    * person's approval (#3762 proposal 1: one unanswered approval used to stop a whole manager).
    */
  def running(id: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): com.histmersion.RowCount =

    (of("SELECT count(*)::int FROM jobs WHERE kind = ? AND manager_id = ? AND ", str(JobKind), str(id.value)) ++
      occupyingOn("jobs")).frag.query[com.histmersion.RowCount].run().head

  /** Every agent run that occupies a place ([[running]]'s rule, a person's runs too): its pipeline, and the goal of the
    * manager that started it (None: a person's, or a library-wide manager's).
    */
  def occupyingRuns()(using DbCon): List[(com.histmersion.api.automation.Pipeline.Wire, Option[ManagerRow.Goal])] =

    (of(
      "SELECT j.pipeline, m.goal_slug FROM jobs j LEFT JOIN agent_managers m ON m.id = j.manager_id " +
        "WHERE j.kind = ? AND ",
      str(JobKind)
    ) ++ occupyingOn("j")).frag.query[(com.histmersion.api.automation.Pipeline.Wire, Option[ManagerRow.Goal])].run().toList

  /** The runs that occupy a place ([[running]]'s rule), as a predicate on table (alias) `t` of `jobs`. */
  private def occupyingOn(t: String): Sql =
    of(
      s"($t.status IN ('queued', 'running') OR ($t.status = 'paused' AND ($t.resume_at IS NOT NULL OR $t.error LIKE ? " +
        s"OR $t.error LIKE ?)))",
      str(s"${PauseReason.Allowance.wire.value}:%"),
      str(s"${PauseReason.PlanQuota.wire.value}:%")
    )

  /** The item kinds of the last `n` runs the managers started, newest first (what the scheduler's shares count).
    */
  def recentKinds(n: com.histmersion.PageSize)(using DbCon): List[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind] =
    sql"""SELECT i.kind FROM jobs j JOIN manager_items i ON i.id = j.manager_item
          WHERE j.kind = $JobKind ORDER BY j.created_at DESC, j.id DESC LIMIT ${math.max(0, n.value)}"""
      .query[ManagerItem.Kind]
      .run()
      .toList

  /** Per goal: when it last progressed — a run of one of its managers started, settled or was evaluated, a work was
    * ingested or swept for it, or a source was linked to it — as an ISO instant (UTC).
    */
  def lastProgress()(using DbCon): Map[ManagerRow.Goal, com.histmersion.domain.automation.managers.ManagerRepo.ProgressText] =
    val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
    of(
        s"""SELECT goal, to_char(max(at) AT TIME ZONE 'UTC', $ts) FROM (
                 SELECT m.goal_slug AS goal, max(e.at) AS at FROM manager_events e JOIN agent_managers m ON m.id = e.manager_id
                 WHERE m.goal_slug IS NOT NULL AND e.kind IN (?, ?, ?, ?, ?) GROUP BY m.goal_slug
                 UNION ALL
                 SELECT goal_slug, max(created_at) FROM goal_links WHERE kind = 'source' GROUP BY goal_slug) t
               GROUP BY goal""",
        str(Feed.Started),
        str(Feed.Settled),
        str(Feed.Evaluated),
        str(Feed.Ingested),
        str(Feed.Swept)
      )
      .frag
      .query[(ManagerRow.Goal, ProgressText)]
      .run()
      .toMap

  /** Per goal: its items a person holds (held, not for an approval and not by the focus). */
  def personHeldByGoal()(using DbCon): Map[com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.goal_slug IS NOT NULL AND i.status = 'held' AND i.hold->>'focus' IS NULL AND i.hold->>'untilApproval' IS NULL
          GROUP BY m.goal_slug""".query[(com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount)].run().toMap

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal()(using DbCon): Map[com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          JOIN jobs j ON j.id = i.run_id
          WHERE m.goal_slug IS NOT NULL AND i.status = 'active' AND j.status IN ('queued', 'running')
          GROUP BY m.goal_slug""".query[(com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount)].run().toMap

  /** Per goal: the outcome of its watcher's acquisition sweep when it is settled. */
  def sweepOutcomes()(using DbCon): Map[com.histmersion.domain.automation.Goal.Slug, com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Outcome] =
    sql"""SELECT m.goal_slug, coalesce(i.outcome, '') FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.goal_slug IS NOT NULL AND i.kind = $AcquisitionSweep AND i.status IN ('done', 'stopped', 'failed')"""
      .query[(com.histmersion.domain.automation.Goal.Slug, ManagerItem.Outcome)]
      .run()
      .toMap

  /** Items that wait for a person and hold no place: held (not by the focus: for an approval, or by a person), or
    * active with their run waiting for an approval — of managers that are not stopped.
    */
  def forPerson()(using DbCon): com.histmersion.RowCount =
    sql"""SELECT count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          LEFT JOIN jobs j ON j.id = i.run_id
          WHERE m.state <> 'stopped' AND ((i.status = 'held' AND i.hold->>'focus' IS NULL)
            OR (i.status = 'active' AND j.status = 'waiting'))""".query[com.histmersion.RowCount].run().head

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
      runId: Option[com.histmersion.api.automation.Agents.RunInfo.Id],
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
    type Id = ManagerItem.Id
    val Id: ManagerItem.Id.type = ManagerItem.Id
    type ManagerId = ManagerInfo.Id
    val ManagerId: ManagerInfo.Id.type = ManagerInfo.Id
    type Kind = ManagerItem.Kind
    val Kind: ManagerItem.Kind.type = ManagerItem.Kind
    type Target = ManagerItem.Target
    val Target: ManagerItem.Target.type = ManagerItem.Target
    type Status = ManagerItem.Status
    val Status: ManagerItem.Status.type = ManagerItem.Status
    type Priority = ManagerItem.Priority
    val Priority: ManagerItem.Priority.type = ManagerItem.Priority
    type Attempts = ManagerItem.Attempts
    val Attempts: ManagerItem.Attempts.type = ManagerItem.Attempts
    type Failures = ManagerItem.Failures
    val Failures: ManagerItem.Failures.type = ManagerItem.Failures
    type EvaluatedRun = com.histmersion.api.automation.Agents.RunInfo.Id
    val EvaluatedRun: com.histmersion.api.automation.Agents.RunInfo.Id.type = com.histmersion.api.automation.Agents.RunInfo.Id
    type NotBefore = ManagerItem.NotBefore
    val NotBefore: ManagerItem.NotBefore.type = ManagerItem.NotBefore
    type Outcome = ManagerItem.Outcome
    val Outcome: ManagerItem.Outcome.type = ManagerItem.Outcome
    type Origin = ManagerItem.Origin
    val Origin: ManagerItem.Origin.type = ManagerItem.Origin
    type CreatedAt = ManagerItem.CreatedAt
    val CreatedAt: ManagerItem.CreatedAt.type = ManagerItem.CreatedAt
    type UpdatedAt = ManagerItem.UpdatedAt
    val UpdatedAt: ManagerItem.UpdatedAt.type = ManagerItem.UpdatedAt

  private val itemSelect =
    raw(s"""SELECT id, manager_id, kind, target, status, priority, attempts, failures, run_id, evaluated_run,
              ${at("not_before")}, hold::text, state::text, outcome, origin, ${at("created_at")}, ${at("updated_at")}
       FROM manager_items""")

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

  def item(id: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id)(using DbCon): Option[ManagerItem] =
    (itemSelect ++ of(" WHERE id = ?", lng(id.value))).frag.query[ItemRow].run().headOption.map(toItem)

  def itemFor(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind, target: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Target)(using DbCon): Option[ManagerItem] =

    (itemSelect ++ of(
      " WHERE manager_id = ? AND kind = ? AND target = ?",
      str(managerId.value),
      str(kind.value),
      str(target.value)
    )).frag.query[ItemRow].run().headOption.map(toItem)

  /** The open items (pending, held, active) of any manager whose target is `target`, the newest first. */
  def openFor(target: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Target)(using DbCon): List[ManagerItem] =

    (itemSelect ++ of(
      " WHERE target = ? AND status IN ('pending', 'held', 'active') ORDER BY id DESC",
      str(target.value)
    )).frag.query[ItemRow].run().toList.map(toItem)

  /** [[items]] on the caller's connection. */
  def itemPage(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, status: Option[ItemStatus], offset: com.histmersion.Offset, limit: com.histmersion.PageSize)(using
      DbCon
  ): (List[ManagerItem], com.histmersion.RowCount) =
    val where = of(" WHERE manager_id = ?", str(managerId.value)) ++
      status.fold(Sql.empty)(s => of(" AND status = ?", str(s.wire.value)))
    val total = (of("SELECT count(*)::int FROM manager_items") ++ where).frag.query[com.histmersion.RowCount].run().head
    val rows = (itemSelect ++ where ++ of(
      """ ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'pending' THEN 1 WHEN 'held' THEN 2 ELSE 3 END,
             priority DESC, coalesce(not_before, '-infinity'::timestamptz), updated_at DESC, id
           OFFSET ? LIMIT ?""",
      int(math.max(0, offset.value)),
      int(math.max(1, limit.value))
    )).frag.query[ItemRow].run().toList.map(toItem)
    (rows, total)

  def withStatus(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, status: ItemStatus)(using DbCon): List[ManagerItem] =

    (itemSelect ++ of(
      " WHERE manager_id = ? AND status = ? ORDER BY priority DESC, id",
      str(managerId.value),
      str(status.wire.value)
    )).frag.query[ItemRow].run().toList.map(toItem)

  /** Add an item, or — when it exists — bring it back: `reopen` sets a settled one pending again, and the priority is
    * raised to `priority` (an active one keeps running). Returns the item's id and whether it was new.
    */
  def upsert(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id,
      kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind,
      target: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Target,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Priority,
      reopen: Boolean,
      initial: ItemState = ItemState()
  )(using DbCon): (com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, Boolean) =
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
          sql"""UPDATE manager_items SET
                  status = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 'pending' ELSE status END,
                  attempts = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 0 ELSE attempts END,
                  failures = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 0 ELSE failures END,
                  not_before = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN NULL ELSE not_before END,
                  outcome = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN NULL ELSE outcome END,
                  priority = greatest(priority, $priority), origin = ${origin.wire}, updated_at = now()
                WHERE id = $id""".update.run()
        (id, false)

  /** The pending items whose time has come, the most urgent first: (id, target, priority, not-before). */
  def pending(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): List[ManagerItem] =
    withStatus(managerId, ItemStatus.Pending)

  /** Dispatch, in the caller's transaction: pending → active with `run`, one more attempt, the open MAJORs before it in
    * its state. `false` = it was no longer pending (another tick took it).
    */
  def dispatchIn(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, run: com.histmersion.domain.automation.Run.Id, state: ItemState)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = ${ItemStatus.Active.wire}, run_id = $run, attempts = attempts + 1,
                                   state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ${ItemStatus.Pending.wire}""".update.run() > 0

  /** Active items whose run is terminal and not evaluated yet (or whose run is gone): the evaluation queue.
    */
  def toEvaluate(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): List[(ManagerItem, Option[(com.histmersion.api.automation.Agents.RunInfo.Status, Option[com.histmersion.api.automation.Agents.RunInfo.Error])])] =
    val rows =
      (itemSelect ++ of(" WHERE manager_id = ? AND status = 'active' ORDER BY id", str(managerId.value))).frag
        .query[ItemRow]
        .run()
        .toList
        .map(toItem)
    rows.flatMap { i =>
      val run = i.runId.flatMap(r =>
        (
          sql"SELECT status, error FROM jobs WHERE id = $r"
            .query[(com.histmersion.api.automation.Agents.RunInfo.Status, Option[com.histmersion.api.automation.Agents.RunInfo.Error])]
            .run()
            .headOption
        )
      )
      run match
        case None => List(i -> None) // its run was deleted
        case Some((st, err)) if RunState.parse(com.histmersion.api.automation.Run.State.Wire(st.value)).exists(_.isTerminal) && !i.evaluatedRun.contains(i.runId.get) =>
          List(i -> Some((st, err)))
        case _ => Nil
    }

  /** Record an evaluation (keyed by the run: a second evaluation of the same run changes nothing). `hold` = the hold it
    * is left in (`held`: waiting for an approval).
    */
  def evaluated(
      itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id,
      run: Option[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.EvaluatedRun],
      status: ItemStatus,
      notBefore: Option[java.time.Instant],
      failures: Option[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Failures],
      state: ItemState,
      outcome: Option[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Outcome],
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
  def hold(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, h: Hold)(using DbCon): Boolean =
    val hold = Option(h)
    sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
          WHERE id = $itemId AND status IN ('pending', 'held')""".update.run() > 0

  def release(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
          WHERE id = $itemId AND status = 'held'""".update.run() > 0

  // ── the focus's holds (docs/research-process-managers.md "As built: the focus") ─────────────────

  /** Hold a PENDING item for the focus (`h.focus` = its goal); false = it was not pending any more. */
  def focusHold(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, h: Hold)(using DbCon): Boolean =
    val hold = Option(h)
    sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
          WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** Release the focus holds among `ids` (a person's or an approval's hold is never touched); how many. */
  def focusRelease(ids: List[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id])(using DbCon): com.histmersion.RowCount =
    if ids.isEmpty then com.histmersion.RowCount(0)
    else
      val arr = ids.map(_.value.toString)
      com.histmersion.RowCount(
        sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
            WHERE id = ANY($arr::bigint[]) AND status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()
      )

  /** Release every focus hold (the focus is not in force any more); how many. */
  def focusReleaseAll()(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(
      sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
          WHERE status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()
    )

  /** The items the focus holds now. */
  def focusHeldCount()(using DbCon): com.histmersion.RowCount =

    sql"""SELECT count(*)::int FROM manager_items WHERE status = 'held' AND hold->>'focus' IS NOT NULL"""
      .query[com.histmersion.RowCount]
      .run()
      .head

  def setPriority(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, priority: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Priority)(using DbCon): Boolean =
    (
      sql"UPDATE manager_items SET priority = $priority, updated_at = now() WHERE id = $itemId".update.run() > 0
    )

  /** Settle an item that is not active (skip: stopped by a person; a manager's stop: cancelled). */
  def settle(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, status: ItemStatus, outcome: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Outcome, from: List[ItemStatus])(using DbCon): Boolean =
    val fs = from.map(_.wire)
    sql"""UPDATE manager_items SET status = ${status.wire}, outcome = $outcome, hold = NULL, updated_at = now()
          WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  /** Re-open a settled item: pending again, its run count and failures reset, how often recorded. */
  def reopen(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, outcome = NULL,
                                   hold = NULL,
                                   state = jsonb_set(state - 'history', '{reopened}',
                                     to_jsonb(coalesce((state->>'reopened')::int, 0) + 1)),
                                   updated_at = now()
          WHERE id = $itemId AND status IN ('done', 'stopped', 'failed', 'cancelled')""".update.run() > 0

  /** A converged item selected again because it went stale: pending at `priority` (exactly: the lowest), its runs
    * counted from zero, its run history dropped (a new cycle), why in its outcome.
    */
  def restale(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, priority: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Priority, why: com.histmersion.domain.automation.managers.ManagerRepo.RestaleReason)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, hold = NULL,
                                   priority = $priority, origin = ${ItemOrigin.Selection.wire}, outcome = $why,
                                   state = state - 'history', updated_at = now()
          WHERE id = $itemId AND status = 'done'""".update.run() > 0

  /** A manager's open items (pending, held, active) as (kind, target) — what
    * [[com.histmersion.domain.ManagerPolicy.waitsFor]] reads.
    */
  def openKeys(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): List[(com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind, com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Target)] =
    sql"""SELECT kind, target FROM manager_items WHERE manager_id = $managerId
          AND status IN ('pending', 'held', 'active')""".query[(ManagerItem.Kind, ManagerItem.Target)].run().toList

  /** Does manager `managerId` have (or ever had) an item of `kind` on `target`? */
  def hasItem(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind, target: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Target)(using DbCon): Boolean =
    sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
      .query[Int]
      .run()
      .nonEmpty

  /** Every open item of a manager cancelled (a stop); returns how many. */
  def cancelOpen(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, why: com.histmersion.domain.automation.managers.ManagerRepo.CancelReason)(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(
      sql"""UPDATE manager_items SET status = 'cancelled', outcome = $why, updated_at = now()
          WHERE manager_id = $managerId AND status IN ('pending', 'held', 'active')""".update.run()
    )

  /** Back to pending (an active item whose run vanished, or was never submitted). */
  def requeue(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, outcome: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Outcome)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = 'pending', run_id = NULL, outcome = $outcome, updated_at = now()
          WHERE id = $itemId AND status = 'active'""".update.run() > 0

  // ── selection: what the review manager picks by itself ─────────────────────────────────────────

  /** Articles with open MAJORs in their review ledger and no item of this manager yet, the most open MAJORs first;
    * `tags` (all of) and `goal` (linked to it) narrow them.
    */
  def openMajorArticles(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind, tags: List[com.histmersion.domain.EnrichSource.Tag], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.PageSize)(using
      DbCon
  ): List[(com.histmersion.domain.research.Slug, com.histmersion.RowCount)] =
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
          ORDER BY m.n DESC, a.slug LIMIT ${math.max(1, limit.value)}""".query[(com.histmersion.domain.research.Slug, com.histmersion.RowCount)].run().toList

  /** Articles never reviewed (no ledger, no review-article run) and with no item of this manager. */
  def unreviewedArticles(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind, tags: List[com.histmersion.domain.EnrichSource.Tag], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.PageSize)(using
      DbCon
  ): List[com.histmersion.domain.research.Slug] =
    sql"""SELECT a.slug FROM articles a
          WHERE a.kind <> 'index' AND a.tags @> $tags::text[]
            AND NOT EXISTS (SELECT 1 FROM review_ledgers l WHERE l.slug = a.slug)
            AND NOT EXISTS (SELECT 1 FROM jobs j WHERE j.kind = $JobKind AND j.pipeline = 'review-article'
                            AND j.target_id = a.slug)
            AND ($goal::text IS NULL OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $goal
                                                 AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind
                            AND i.target = a.slug)
          ORDER BY a.updated_at, a.slug LIMIT ${math.max(1, limit.value)}""".query[com.histmersion.domain.research.Slug].run().toList

  /** The review manager's stale candidates: its converged review items (by when they converged: their item's
    * `updated_at`, with the filter hits recorded then), then articles whose ledger holds no open MAJOR and that no item
    * of it drives (by the ledger's `updated_at`) — oldest first, `tags`/`goal` narrowing as in the selection. (slug,
    * converged at, filter hits then, the done item if any).
    */
  def staleCandidates(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind, tags: List[com.histmersion.domain.EnrichSource.Tag], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.PageSize)(using
      DbCon
  ): List[(com.histmersion.domain.research.Slug, ProgressText, Option[ItemState.FilterHits], Option[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id])] =
    val lim = math.max(1, limit.value)
    val g = goal.map(_.value.trim).getOrElse("")
    val done = of(
        s"""SELECT i.target, ${at("i.updated_at")}, (i.state->>'filterHits')::int, i.id FROM manager_items i
          JOIN articles a ON a.slug = i.target
          WHERE i.manager_id = ? AND i.kind = ? AND i.status = 'done' AND a.tags @> ?::text[]
            AND (? = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = ?
                                   AND g.kind = 'article' AND g.ref = a.slug))
          ORDER BY i.updated_at, i.id LIMIT ?""",
        str(managerId.value),
        str(kind.value),
        strs(tags.map(_.value)),
        str(g),
        str(g),
        int(lim)
      )
      .frag
      .query[(com.histmersion.domain.research.Slug, ProgressText, Option[ItemState.FilterHits], ItemRow.Id)]
      .run()
      .toList
      .map((s, t, n, id) => (s, t, n, Some(id)))
    val ledgers = of(
        s"""SELECT l.slug, ${at("l.updated_at")} FROM review_ledgers l JOIN articles a ON a.slug = l.slug
          WHERE a.kind <> 'index' AND a.tags @> ?::text[]
            AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
            AND (? = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = ?
                                   AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = ? AND i.kind = ? AND i.target = a.slug)
          ORDER BY l.updated_at, l.slug LIMIT ?""",
        strs(tags.map(_.value)),
        str(g),
        str(g),
        str(managerId.value),
        str(kind.value),
        int(lim)
      )
      .frag
      .query[(com.histmersion.domain.research.Slug, ProgressText)]
      .run()
      .toList
      .map((s, t) => (s, t, None, None))
    (done ++ ledgers).sortBy(_._2.value).take(lim)

  /** Articles that record a coverage filter and that no item of this manager has open (pending, held, active), with
    * when their last extension item settled (None: never extended) — the ones never extended first, then the longest
    * ago; `tags`/`goal` narrow as in the selection.
    */
  def extendCandidates(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, tags: List[com.histmersion.domain.EnrichSource.Tag], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.PageSize)(using
      DbCon
  ): List[(com.histmersion.domain.research.Slug, Option[ProgressText])] =
    val g = goal.map(_.value.trim).getOrElse("")
    of(
        s"""SELECT a.slug, ${at("x.updated_at")} FROM articles a
          LEFT JOIN manager_items x ON x.manager_id = ? AND x.kind = ? AND x.target = a.slug
          WHERE a.coverage_filter IS NOT NULL AND a.kind <> 'index' AND a.tags @> ?::text[]
            AND (? = '' OR EXISTS (SELECT 1 FROM goal_links gl WHERE gl.goal_slug = ?
                                   AND gl.kind = 'article' AND gl.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = ? AND i.target = a.slug
                            AND i.status IN ('pending', 'held', 'active'))
          ORDER BY x.updated_at NULLS FIRST, a.slug LIMIT ?""",
        str(managerId.value),
        str(ArticleExtend),
        strs(tags.map(_.value)),
        str(g),
        str(g),
        str(managerId.value),
        int(math.max(1, limit.value))
      )
      .frag
      .query[(com.histmersion.domain.research.Slug, Option[ProgressText])]
      .run()
      .toList

  // ── the autopilot's views (few queries for every manager at once) ──────────────────────────────

  def light()(using DbCon): List[ManagerLight] =
    of(
        s"""SELECT m.id, m.kind, m.goal_slug, m.state, m.cron, m.config::text, m.status_note,
                      EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = m.id AND i.kind = ?)
               FROM agent_managers m ORDER BY m.id""",
        str(PlanGoalItem)
      )
      .frag
      .query[ManagerLight]
      .run()
      .toList

  /** Per manager: its open items (pending, held, active), the items it settled since `since`, and when it last recorded
    * an event. A watcher's ingest backlog is not counted: it drains by the day's ingest caps, not at the pace of its
    * runs (it has its own estimate, [[com.histmersion.domain.IngestBacklog.daysToDrain]]).
    */
  def pace(since: java.time.Instant)(using DbCon): Map[ManagerRow.Id, (com.histmersion.RowCount, com.histmersion.RowCount, Option[ProgressText])] =
    val t = since.toString
    of(
        s"""SELECT m.id,
                      (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> ?
                         AND i.status IN ('pending', 'held', 'active')),
                      (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> ?
                         AND i.status IN ('done', 'stopped', 'failed') AND i.updated_at >= ?::timestamptz),
                      (SELECT ${at("max(e.at)")} FROM manager_events e WHERE e.manager_id = m.id)
               FROM agent_managers m""",
        str(OaIngest),
        str(OaIngest),
        str(t)
      )
      .frag
      .query[(ManagerRow.Id, com.histmersion.RowCount, com.histmersion.RowCount, Option[ProgressText])]
      .run()
      .toList
      .map((id, o, d, l) => id -> (o, d, l))
      .toMap

  /** Open items of `kind` in manager `id` (pending, held or active) — e.g. a goal's compiles. `focusHeld` = an item the
    * focus holds counts as held (false: as the pending item it really is).
    */
  def openOf(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id,
      kinds: List[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Kind],
      statuses: List[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Status],
      focusHeld: Boolean = true
  )(using DbCon): com.histmersion.RowCount =

    of(
        "SELECT count(*)::int FROM manager_items WHERE manager_id = ? AND kind = ANY(?::text[]) AND status = ANY(?::text[])" +
          (if focusHeld then "" else " AND NOT (status = 'held' AND hold->>'focus' IS NOT NULL)"),
        str(managerId.value),
        strs(kinds.map(_.value)),
        strs(statuses.map(_.value))
      )
      .frag
      .query[com.histmersion.RowCount]
      .run()
      .head

  /** Open items on `target` in manager `id`, any kind. */
  def openOn(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, target: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Target)(using DbCon): Boolean =
    (sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND target = $target
                      AND status IN ('pending', 'held', 'active') LIMIT 1""".query[Int].run().nonEmpty)

  /** An article's tags and the goals it is linked to (what a hint's effect matches). */
  def articleFacets(slug: com.histmersion.domain.research.Slug)(using DbCon): (List[com.histmersion.domain.EnrichSource.Tag], List[com.histmersion.domain.automation.managers.ManagerRepo.ArticleFacetsResultPart2]) =
    val tags = sql"SELECT tags FROM articles WHERE slug = $slug".query[List[com.histmersion.domain.EnrichSource.Tag]].run().headOption.getOrElse(Nil)
    val goals =
      sql"SELECT goal_slug FROM goal_links WHERE kind = 'article' AND ref = $slug".query[com.histmersion.domain.automation.Goal.Slug].run().toList
    (tags, goals)

  /** Is there a source with this citekey or DOI (a hold waiting for it is released)? */
  def sourceExists(ref: com.histmersion.domain.library.Citekey)(using DbCon): Boolean =
    val r = ref.value.trim
    val doi = r.toLowerCase.stripPrefix("https://doi.org/").stripPrefix("doi:")
    (
      sql"""SELECT 1 FROM sources WHERE citekey = $r OR lower(doi) = $doi LIMIT 1""".query[Int].run().nonEmpty
    )

  // ── the watchers' ingest backlog (`oa-ingest` items) ───────────────────────────────────────────

  def backlogPendingIn()(using DbCon): List[ManagerItem] =
    (itemSelect ++ of(
      """ WHERE kind = ? AND status = 'pending'
          AND manager_id IN (SELECT id FROM agent_managers WHERE kind = 'watch' AND state = 'active')
        ORDER BY priority DESC, id""",
      str(OaIngest)
    )).frag.query[ItemRow].run().toList.map(toItem)

  def backlogOpenIn()(using DbCon): List[ManagerItem] =
    (itemSelect ++ of(
      " WHERE kind = ? AND status IN ('pending', 'held', 'active') ORDER BY id",
      str(OaIngest)
    )).frag.query[ItemRow].run().toList.map(toItem)

  def backlogOpenForIn(doi: com.histmersion.domain.library.Doi)(using DbCon): Option[ManagerItem] =
    (itemSelect ++ of(
      " WHERE kind = ? AND target = ? AND status IN ('pending', 'held', 'active') ORDER BY id LIMIT 1",
      str(OaIngest),
      str(doi.value)
    )).frag.query[ItemRow].run().headOption.map(toItem)

  /** Per watcher: its backlog items by status (pending, held) — what the status lines count. */
  def backlogCounts()(using DbCon): Map[ManagerRow.Id, (com.histmersion.RowCount, com.histmersion.RowCount)] =
    sql"""SELECT manager_id, count(*) FILTER (WHERE status = 'pending')::int, count(*) FILTER (WHERE status = 'held')::int
          FROM manager_items WHERE kind = $OaIngest AND status IN ('pending', 'held') GROUP BY manager_id"""
      .query[(ManagerRow.Id, com.histmersion.RowCount, com.histmersion.RowCount)]
      .run()
      .toList
      .map((m, p, h) => m -> (p, h))
      .toMap

  def countEventsByIn(kind: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsByInKind, since: java.time.Instant)(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.CountEventsByInResultPart1, com.histmersion.RowCount] =
    val at = since.toString
    sql"""SELECT manager_id, count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz
          GROUP BY manager_id""".query[(CountEventsByInResultPart1, com.histmersion.RowCount)].run().toMap

  def setItemStateIn(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, state: ItemState, from: List[ItemStatus])(using DbCon): Boolean =
    val fs = from.map(_.wire)
    sql"""UPDATE manager_items SET state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  def backlogRetryIn(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id, failures: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Failures, notBefore: java.time.Instant, outcome: com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Outcome)(using
      DbCon
  ): Boolean =
    val nb = notBefore.toString
    sql"""UPDATE manager_items SET failures = $failures, attempts = attempts + 1, not_before = $nb::timestamptz,
                                   outcome = $outcome, updated_at = now()
          WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** The citekey of the source holding this DOI, if one does. */
  def citekeyOfDoi(doi: com.histmersion.domain.library.Doi)(using DbCon): Option[com.histmersion.domain.library.Citekey] =
    val d = doi.value.trim.toLowerCase
    (
      sql"SELECT citekey FROM sources WHERE lower(doi) = $d ORDER BY citekey LIMIT 1".query[com.histmersion.domain.library.Citekey].run().headOption
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
              WHERE j.kind = $JobKind AND j.pipeline = 'acquisition-sweep' AND j.manager_id LIKE 'watch:%')
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
  def openAcquireCount(using DbCon): com.histmersion.RowCount =
    (
      sql"SELECT count(*)::int FROM queue_items WHERE list = 'acquire' AND status = 'open'".query[com.histmersion.RowCount].run().head
    )

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  private given DbCodec[ManagerEvent] = DbCodec.derived

  private val eventSelect =
    raw(s"SELECT id, manager_id, item_id, run_id, ${at("at")}, kind, text FROM manager_events")

  def eventIn(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, itemId: Option[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id], runId: Option[com.histmersion.api.automation.Agents.RunInfo.Id], kind: com.histmersion.domain.automation.managers.ManagerRepo.EventInKind, text: com.histmersion.domain.automation.managers.ManagerRepo.EventInText)(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO manager_events (manager_id, item_id, run_id, kind, text)
                  VALUES ($managerId, $itemId, $runId, $kind, ${text.value.take(2000)})""".update.run()

  def event(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id,
      itemId: Option[com.histmersion.domain.automation.managers.ManagerRepo.ItemRow.Id],
      runId: Option[com.histmersion.api.automation.Agents.RunInfo.Id],
      kind: com.histmersion.domain.automation.managers.ManagerRepo.EventKind,
      text: com.histmersion.domain.automation.managers.ManagerRepo.EventText
  )(using DbCon): Unit =
    eventIn(managerId, itemId, runId, kind, text)

  /** Newest first; `before` = only events older than that id. */
  def events(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, before: Option[com.histmersion.domain.automation.managers.ManagerRepo.EventCursor], limit: com.histmersion.PageSize)(using DbCon): List[ManagerEvent] =
    (eventSelect ++ of(" WHERE manager_id = ?", str(managerId.value)) ++
      before.fold(Sql.empty)(b => of(" AND id < ?", lng(b.value))) ++
      of(" ORDER BY id DESC LIMIT ?", int(math.max(1, limit.value)))).frag.query[ManagerEvent].run().toList

  def lastEvent(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id)(using DbCon): Option[ManagerEvent] = events(managerId, None, com.histmersion.PageSize(1)).headOption

  /** How many events of `kind` a manager has recorded since `since` (a watcher's ingests today). */
  def countEvents(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsKind, since: java.time.Instant)(using DbCon): com.histmersion.RowCount =
    val at = since.toString
    (sql"""SELECT count(*)::int FROM manager_events WHERE manager_id = $managerId AND kind = $kind
                      AND at >= $at::timestamptz""".query[com.histmersion.RowCount].run().head)

  /** How many events of `kind` ALL managers recorded since `since` (every watcher's ingests today: the autopilot's
    * shared cap).
    */
  def countEventsAll(kind: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsAllKind, since: java.time.Instant)(using DbCon): com.histmersion.RowCount =
    val at = since.toString

    sql"""SELECT count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz"""
      .query[com.histmersion.RowCount]
      .run()
      .head

  /** The newest event of `kind` (a starved note is logged once per change). */
  def lastEventOf(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, kind: com.histmersion.domain.automation.managers.ManagerRepo.LastEventOfKind)(using DbCon): Option[ManagerEvent] =

    (eventSelect ++ of(
      " WHERE manager_id = ? AND kind = ? ORDER BY id DESC LIMIT 1",
      str(managerId.value),
      str(kind.value)
    )).frag.query[ManagerEvent].run().headOption

  def pruneEvents(days: com.histmersion.domain.CodexRetention.KeepDays)(using DbCon): com.histmersion.RowCount =
    if days.value <= 0 then com.histmersion.RowCount(0)
    else com.histmersion.RowCount(sql"DELETE FROM manager_events WHERE at < now() - make_interval(days => $days)".update.run())

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
    type Id = ManagerHint.Id
    val Id: ManagerHint.Id.type = ManagerHint.Id
    type ManagerId = ManagerInfo.Id
    val ManagerId: ManagerInfo.Id.type = ManagerInfo.Id
    type ItemId = ManagerHint.ItemId
    val ItemId: ManagerHint.ItemId.type = ManagerHint.ItemId
    type Text = ManagerHint.Text
    val Text: ManagerHint.Text.type = ManagerHint.Text
    type Author = ManagerHint.Author
    val Author: ManagerHint.Author.type = ManagerHint.Author
    type Standing = ManagerHint.Standing
    val Standing: ManagerHint.Standing.type = ManagerHint.Standing
    type CreatedAt = ManagerHint.CreatedAt
    val CreatedAt: ManagerHint.CreatedAt.type = ManagerHint.CreatedAt
    type RetiredAt = ManagerHint.RetiredAt
    val RetiredAt: ManagerHint.RetiredAt.type = ManagerHint.RetiredAt
    type Uses = ManagerHint.Uses
    val Uses: ManagerHint.Uses.type = ManagerHint.Uses
    type Proposal = ManagerHint.Proposal
    val Proposal: ManagerHint.Proposal.type = ManagerHint.Proposal

  /** A hint, with the newest OPEN `hint-effect` approval about it (a model's proposed action waiting for a person) and
    * that action.
    */
  private val hintSelect = raw(
    s"""SELECT h.id, h.manager_id, h.item_id, h.text, h.effect::text, h.author, h.standing, ${at("h.created_at")},
              ${at("h.retired_at")}, (SELECT count(*)::int FROM manager_hint_uses u WHERE u.hint_id = h.id),
              p.id, (p.proposal->'effect')::text
       FROM manager_hints h
       LEFT JOIN LATERAL (SELECT a.id, a.proposal FROM approvals a
                          WHERE a.kind = 'hint-effect' AND a.state = 'open' AND a.subject = h.manager_id
                            AND (a.proposal->>'hint')::bigint = h.id ORDER BY a.id DESC LIMIT 1) p ON true"""
  )

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

  def addHint(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.ManagerId,
      itemId: Option[com.histmersion.domain.automation.managers.ManagerRepo.HintRow.ItemId],
      text: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.Text,
      effect: Option[HintEffect],
      author: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.Author,
      standing: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.Standing
  )(using DbCon): ManagerHint =
    val id =
      sql"""INSERT INTO manager_hints (manager_id, item_id, text, effect, author, standing)
            VALUES ($managerId, $itemId, $text, $effect::jsonb, $author, $standing) RETURNING id"""
        .query[HintRow.Id]
        .run()
        .head
    hint(id).get

  def hint(id: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.Id)(using DbCon): Option[ManagerHint] =
    ((hintSelect ++ of(" WHERE h.id = ?", lng(id.value))).frag.query[HintRow].run().headOption.map(toHint))

  /** A manager's hints, oldest first; `live` = only the ones not retired. */
  def hints(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, live: Boolean)(using DbCon): List[ManagerHint] =
    (hintSelect ++ of(" WHERE h.manager_id = ?", str(managerId.value)) ++
      (if live then of(" AND h.retired_at IS NULL") else Sql.empty) ++
      of(" ORDER BY h.id")).frag.query[HintRow].run().toList.map(toHint)

  /** The action a hint carries now (a model's proposal a person approved). */
  def setHintEffect(id: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.Id, effect: Option[HintEffect])(using DbCon): Boolean =
    (sql"UPDATE manager_hints SET effect = $effect::jsonb WHERE id = $id".update.run() > 0)

  def retireHint(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ManagerRow.Id, id: com.histmersion.domain.automation.managers.ManagerRepo.HintRow.Id)(using DbCon): Boolean =
    sql"""UPDATE manager_hints SET retired_at = now() WHERE id = $id AND manager_id = $managerId
          AND retired_at IS NULL""".update.run() > 0

  /** In the dispatch transaction: the hints a run was given, and the once-hints among them retired. */
  def usedIn(hints: List[ManagerHint], runId: com.histmersion.domain.automation.Run.Id)(using DbCon): Unit =
    hints.foreach { h =>
      sql"""INSERT INTO manager_hint_uses (hint_id, run_id) VALUES (${h.id}, $runId) ON CONFLICT DO NOTHING""".update
        .run()
      if !h.standing.value then
        sql"UPDATE manager_hints SET retired_at = now() WHERE id = ${h.id} AND retired_at IS NULL".update.run()
    }
