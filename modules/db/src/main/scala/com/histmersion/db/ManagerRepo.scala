package com.histmersion.db

import com.histmersion.api.Agents.{JobKind, PauseReason, RunState}
import com.histmersion.api.Managers.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The store of the process managers (V087, docs/research-process-managers.md §2): managers, their work items, hints
  * (and which runs they reached), events, and the agent lease. All state lives in these rows, so a restart picks up
  * where the last process left off. Every state change of an item is a compare-and-set on its status (and, for an
  * evaluation, on the run it evaluates), so a tick that runs twice — or two processes, should the lease ever fail —
  * cannot do a step twice.
  */
object ManagerRepo:

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: String) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

  // the jsonb columns, through their DTOs' codecs ([[Jsonb]]); absent fields are left out when written
  private given configCodec: DbCodec[ManagerConfig] = Jsonb.codec(ManagerConfig(), dropNulls = true)
  private given stateCodec: DbCodec[ItemState] = Jsonb.codec(ItemState(), dropNulls = true)
  private given holdCodec: DbCodec[Option[Hold]] = Jsonb.optional[Hold](dropNulls = true)
  private given effectCodec: DbCodec[Option[HintEffect]] = Jsonb.optional[HintEffect](dropNulls = true)

  // ── managers ───────────────────────────────────────────────────────────────────────────────────

  final private case class ManagerRow(
      id: String,
      kind: String,
      goal: Option[String],
      state: String,
      pauseRuns: Boolean,
      config: ManagerConfig,
      statusNote: Option[String],
      cron: Option[String],
      nextTickAt: Option[String],
      createdAt: String,
      updatedAt: String
  )
  private given DbCodec[ManagerRow] = DbCodec.derived

  private val managerSelect =
    Sql.raw(s"""SELECT id, kind, goal_slug, state, pause_runs, config::text, status_note, cron, ${at("next_tick_at")},
              ${at("created_at")}, ${at("updated_at")} FROM agent_managers""")

  private def toInfo(ds: DataSource, r: ManagerRow): ManagerInfo =
    val (usd, tokens) = spendToday(ds, r.id)
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
      counts(ds, r.id),
      inFlight(ds, r.id),
      lastEvent(ds, r.id),
      usd.toDouble,
      tokens
    )

  /** What a manager's runs spent this UTC day (the steps that finished since midnight UTC): metered USD — a plan-billed
    * call is recorded at 0 — and tokens in + out, every call's.
    */
  def spendToday(ds: DataSource, id: String): (BigDecimal, Long) =
    connect(ds):
      sql"""SELECT coalesce(sum(s.cost_usd), 0), coalesce(sum(s.tokens_in + s.tokens_out), 0)::bigint
            FROM agent_steps s JOIN jobs j ON j.id = s.job_id
            WHERE j.manager_id = $id
              AND s.finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"""
        .query[(BigDecimal, Long)]
        .run()
        .head

  def list(ds: DataSource): List[ManagerInfo] =
    connect(ds)((managerSelect ++ Sql.of(" ORDER BY created_at, id")).frag.query[ManagerRow].run().toList)
      .map(toInfo(ds, _))

  def get(ds: DataSource, id: String): Option[ManagerInfo] =
    connect(ds)((managerSelect ++ Sql.of(" WHERE id = ?", Param.S(id))).frag.query[ManagerRow].run().headOption)
      .map(toInfo(ds, _))

  /** A new manager (with its cron and first firing, if it has one); `false` when the id is taken. */
  def create(
      ds: DataSource,
      id: String,
      kind: Kind,
      goal: Option[String],
      state: State,
      config: ManagerConfig,
      cron: Option[String] = None,
      nextTick: Option[java.time.Instant] = None
  ): Boolean =
    transact(ds)(createIn(id, kind, goal, state, config, cron, nextTick))

  def createIn(
      id: String,
      kind: Kind,
      goal: Option[String],
      state: State,
      config: ManagerConfig,
      cron: Option[String],
      nextTick: Option[java.time.Instant]
  )(using DbCon): Boolean =
    val next = nextTick.map(_.toString)
    sql"""INSERT INTO agent_managers (id, kind, goal_slug, state, config, cron, next_tick_at)
          VALUES ($id, ${kind.wire}, $goal, ${state.wire}, $config::jsonb, $cron, $next::timestamptz)
          ON CONFLICT (id) DO NOTHING""".update.run() > 0

  /** When its cron fires next (None: not scheduled). */
  def setNextTick(ds: DataSource, id: String, next: Option[java.time.Instant]): Boolean =
    val n = next.map(_.toString)
    transact(ds)(sql"""UPDATE agent_managers SET next_tick_at = $n::timestamptz WHERE id = $id""".update.run() > 0)

  def setState(ds: DataSource, id: String, state: State, pauseRuns: Boolean): Boolean =
    transact(ds):
      sql"""UPDATE agent_managers SET state = ${state.wire}, pause_runs = $pauseRuns, updated_at = now()
            WHERE id = $id""".update.run() > 0

  /** Its settings and cron; `next` = when the cron fires next (None with no cron). */
  def setConfig(
      ds: DataSource,
      id: String,
      config: ManagerConfig,
      cron: Option[String],
      next: Option[java.time.Instant]
  ): Boolean =
    transact(ds)(setConfigIn(id, config, cron, next))

  def setConfigIn(id: String, config: ManagerConfig, cron: Option[String], next: Option[java.time.Instant])(using
      DbCon
  ): Boolean =
    val n = next.map(_.toString)
    sql"""UPDATE agent_managers SET config = $config::jsonb, cron = $cron, next_tick_at = $n::timestamptz, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** The derived status line; written only when it changed (no `updated_at` churn every tick). */
  def setStatusNote(ds: DataSource, id: String, note: String): Boolean =
    transact(ds):
      sql"""UPDATE agent_managers SET status_note = $note
            WHERE id = $id AND status_note IS DISTINCT FROM $note""".update.run() > 0

  def delete(ds: DataSource, id: String): Boolean =
    transact(ds)(sql"DELETE FROM agent_managers WHERE id = $id".update.run() > 0)

  def counts(ds: DataSource, id: String): ItemCounts =
    ItemCounts.of(
      connect(ds)(
        sql"""SELECT status, count(*)::int FROM manager_items WHERE manager_id = $id GROUP BY status"""
          .query[(String, Int)]
          .run()
          .toList
      )
    )

  /** Its runs not settled yet (queued, running, paused, waiting). */
  def inFlight(ds: DataSource, id: String): Int =
    connect(ds)(sql"""SELECT count(*)::int FROM jobs WHERE kind = $JobKind AND manager_id = $id
                      AND status IN ('queued', 'running', 'paused', 'waiting')""".query[Int].run().head)

  /** Its runs that run, or will run again without a person (what a manager's own `maxRuns` counts): queued, running,
    * and paused for a reason that resumes by itself — a restart's `interrupted` and a `stalled` pause (both carry
    * `resume_at`), an `allowance` or `plan-quota` pause. Counting only `queued`/`running` let a restart overshoot
    * (#1803): boot pauses the runs it interrupted, the first tick saw no run and started `maxRuns` new ones, and a
    * minute later the engine resumed the interrupted ones beside them. A pause a person must lift (`requested`,
    * `budget`, `provider`) frees its place — resuming it is that person's call — and so does a run WAITING for a
    * person's approval (#3762 proposal 1: one unanswered approval used to stop a whole manager).
    */
  def running(ds: DataSource, id: String): Int =
    connect(ds)(
      (Sql.of("SELECT count(*)::int FROM jobs WHERE kind = ? AND manager_id = ? AND ", Param.S(JobKind), Param.S(id)) ++
        occupyingOn("jobs")).frag.query[Int].run().head
    )

  /** Every agent run that occupies a place ([[running]]'s rule, a person's runs too): its pipeline, and the goal of the
    * manager that started it (None: a person's, or a library-wide manager's).
    */
  def occupyingRuns(ds: DataSource): List[(String, Option[String])] =
    connect(ds)(
      (Sql.of(
        "SELECT j.pipeline, m.goal_slug FROM jobs j LEFT JOIN agent_managers m ON m.id = j.manager_id " +
          "WHERE j.kind = ? AND ",
        Param.S(JobKind)
      ) ++ occupyingOn("j")).frag.query[(String, Option[String])].run().toList
    )

  /** The runs that occupy a place ([[running]]'s rule), as a predicate on table (alias) `t` of `jobs`. */
  private def occupyingOn(t: String): Sql =
    Sql.of(
      s"($t.status IN ('queued', 'running') OR ($t.status = 'paused' AND ($t.resume_at IS NOT NULL OR $t.error LIKE ? " +
        s"OR $t.error LIKE ?)))",
      Param.S(s"${PauseReason.Allowance.wire}:%"),
      Param.S(s"${PauseReason.PlanQuota.wire}:%")
    )

  /** The item kinds of the last `n` runs the managers started, newest first (what the scheduler's shares count).
    */
  def recentKinds(ds: DataSource, n: Int): List[String] =
    connect(ds):
      sql"""SELECT i.kind FROM jobs j JOIN manager_items i ON i.id = j.manager_item
            WHERE j.kind = $JobKind ORDER BY j.created_at DESC, j.id DESC LIMIT ${math.max(0, n)}"""
        .query[String]
        .run()
        .toList

  /** Per goal: when it last progressed — a run of one of its managers started, settled or was evaluated, a work was
    * ingested or swept for it, or a source was linked to it — as an ISO instant (UTC).
    */
  def lastProgress(ds: DataSource): Map[String, String] =
    val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
    connect(ds):
      Sql
        .of(
          s"""SELECT goal, to_char(max(at) AT TIME ZONE 'UTC', $ts) FROM (
                   SELECT m.goal_slug AS goal, max(e.at) AS at FROM manager_events e JOIN agent_managers m ON m.id = e.manager_id
                   WHERE m.goal_slug IS NOT NULL AND e.kind IN (?, ?, ?, ?, ?) GROUP BY m.goal_slug
                   UNION ALL
                   SELECT goal_slug, max(created_at) FROM goal_links WHERE kind = 'source' GROUP BY goal_slug) t
                 GROUP BY goal""",
          Param.S(Feed.Started),
          Param.S(Feed.Settled),
          Param.S(Feed.Evaluated),
          Param.S(Feed.Ingested),
          Param.S(Feed.Swept)
        )
        .frag
        .query[(String, String)]
        .run()
        .toMap

  /** Per goal: its items a person holds (held, not for an approval and not by the focus). */
  def personHeldByGoal(ds: DataSource): Map[String, Int] =
    connect(ds):
      sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
            WHERE m.goal_slug IS NOT NULL AND i.status = 'held' AND i.hold->>'focus' IS NULL AND i.hold->>'untilApproval' IS NULL
            GROUP BY m.goal_slug""".query[(String, Int)].run().toMap

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal(ds: DataSource): Map[String, Int] =
    connect(ds):
      sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
            JOIN jobs j ON j.id = i.run_id
            WHERE m.goal_slug IS NOT NULL AND i.status = 'active' AND j.status IN ('queued', 'running')
            GROUP BY m.goal_slug""".query[(String, Int)].run().toMap

  /** Per goal: the outcome of its watcher's acquisition sweep when it is settled. */
  def sweepOutcomes(ds: DataSource): Map[String, String] =
    connect(ds):
      sql"""SELECT m.goal_slug, coalesce(i.outcome, '') FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
            WHERE m.goal_slug IS NOT NULL AND i.kind = $AcquisitionSweep AND i.status IN ('done', 'stopped', 'failed')"""
        .query[(String, String)]
        .run()
        .toMap

  /** Items that wait for a person and hold no place: held (not by the focus: for an approval, or by a person), or
    * active with their run waiting for an approval — of managers that are not stopped.
    */
  def forPerson(ds: DataSource): Int =
    connect(ds):
      sql"""SELECT count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
            LEFT JOIN jobs j ON j.id = i.run_id
            WHERE m.state <> 'stopped' AND ((i.status = 'held' AND i.hold->>'focus' IS NULL)
              OR (i.status = 'active' AND j.status = 'waiting'))""".query[Int].run().head

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  final private case class ItemRow(
      id: Long,
      managerId: String,
      kind: String,
      target: String,
      status: String,
      priority: Int,
      attempts: Int,
      failures: Int,
      runId: Option[String],
      evaluatedRun: Option[String],
      notBefore: Option[String],
      hold: Option[Hold],
      state: ItemState,
      outcome: Option[String],
      origin: String,
      createdAt: String,
      updatedAt: String
  )
  private given DbCodec[ItemRow] = DbCodec.derived

  private val itemSelect =
    Sql.raw(s"""SELECT id, manager_id, kind, target, status, priority, attempts, failures, run_id, evaluated_run,
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

  def item(ds: DataSource, id: Long): Option[ManagerItem] =
    connect(ds)((itemSelect ++ Sql.of(" WHERE id = ?", Param.L(id))).frag.query[ItemRow].run().headOption.map(toItem))

  def itemFor(ds: DataSource, managerId: String, kind: String, target: String): Option[ManagerItem] =
    connect(ds)(
      (itemSelect ++ Sql.of(
        " WHERE manager_id = ? AND kind = ? AND target = ?",
        Param.S(managerId),
        Param.S(kind),
        Param.S(target)
      )).frag.query[ItemRow].run().headOption.map(toItem)
    )

  /** The open items (pending, held, active) of any manager whose target is `target`, the newest first. */
  def openFor(ds: DataSource, target: String): List[ManagerItem] =
    connect(ds)(
      (itemSelect ++ Sql.of(
        " WHERE target = ? AND status IN ('pending', 'held', 'active') ORDER BY id DESC",
        Param.S(target)
      )).frag.query[ItemRow].run().toList.map(toItem)
    )

  /** A page of a manager's items: by `status` (none = all), the most urgent first. */
  def items(
      ds: DataSource,
      managerId: String,
      status: Option[ItemStatus],
      offset: Int,
      limit: Int
  ): (List[ManagerItem], Int) =
    val where = Sql.of(" WHERE manager_id = ?", Param.S(managerId)) ++
      status.fold(Sql.empty)(s => Sql.of(" AND status = ?", Param.S(s.wire)))
    connect(ds):
      val total = (Sql.of("SELECT count(*)::int FROM manager_items") ++ where).frag.query[Int].run().head
      val rows = (itemSelect ++ where ++ Sql.of(
        """ ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'pending' THEN 1 WHEN 'held' THEN 2 ELSE 3 END,
             priority DESC, coalesce(not_before, '-infinity'::timestamptz), updated_at DESC, id
           OFFSET ? LIMIT ?""",
        Param.I(math.max(0, offset)),
        Param.I(math.max(1, limit))
      )).frag.query[ItemRow].run().toList.map(toItem)
      (rows, total)

  def withStatus(ds: DataSource, managerId: String, status: ItemStatus): List[ManagerItem] =
    connect(ds)(
      (itemSelect ++ Sql.of(
        " WHERE manager_id = ? AND status = ? ORDER BY priority DESC, id",
        Param.S(managerId),
        Param.S(status.wire)
      )).frag.query[ItemRow].run().toList.map(toItem)
    )

  /** Add an item, or — when it exists — bring it back: `reopen` sets a settled one pending again, and the priority is
    * raised to `priority` (an active one keeps running). Returns the item's id and whether it was new.
    */
  def upsert(
      ds: DataSource,
      managerId: String,
      kind: String,
      target: String,
      origin: ItemOrigin,
      priority: Int,
      reopen: Boolean,
      initial: ItemState = ItemState()
  ): (Long, Boolean) =
    transact(ds):
      sql"""INSERT INTO manager_items (manager_id, kind, target, origin, priority, state)
            VALUES ($managerId, $kind, $target, ${origin.wire}, $priority, $initial::jsonb)
            ON CONFLICT (manager_id, kind, target) DO NOTHING RETURNING id""".query[Long].run().headOption match
        case Some(id) => (id, true)
        case None     =>
          val id =
            sql"""SELECT id FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
              .query[Long]
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
  def pending(ds: DataSource, managerId: String): List[ManagerItem] =
    withStatus(ds, managerId, ItemStatus.Pending)

  /** Dispatch, in the caller's transaction: pending → active with `run`, one more attempt, the open MAJORs before it in
    * its state. `false` = it was no longer pending (another tick took it).
    */
  def dispatchIn(itemId: Long, run: String, state: ItemState)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = ${ItemStatus.Active.wire}, run_id = $run, attempts = attempts + 1,
                                   state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ${ItemStatus.Pending.wire}""".update.run() > 0

  /** Active items whose run is terminal and not evaluated yet (or whose run is gone): the evaluation queue.
    */
  def toEvaluate(ds: DataSource, managerId: String): List[(ManagerItem, Option[(String, Option[String])])] =
    val rows = connect(ds):
      (itemSelect ++ Sql.of(" WHERE manager_id = ? AND status = 'active' ORDER BY id", Param.S(managerId))).frag
        .query[ItemRow]
        .run()
        .toList
        .map(toItem)
    rows.flatMap { i =>
      val run = i.runId.flatMap(r =>
        connect(ds)(
          sql"SELECT status, error FROM jobs WHERE id = $r"
            .query[(String, Option[String])]
            .run()
            .headOption
        )
      )
      run match
        case None => List(i -> None) // its run was deleted
        case Some((st, err)) if RunState.parse(st).exists(_.isTerminal) && !i.evaluatedRun.contains(i.runId.get) =>
          List(i -> Some((st, err)))
        case _ => Nil
    }

  /** Record an evaluation (keyed by the run: a second evaluation of the same run changes nothing). `hold` = the hold it
    * is left in (`held`: waiting for an approval).
    */
  def evaluated(
      ds: DataSource,
      itemId: Long,
      run: Option[String],
      status: ItemStatus,
      notBefore: Option[java.time.Instant],
      failures: Option[Int],
      state: ItemState,
      outcome: Option[String],
      hold: Option[Hold] = None
  ): Boolean =
    val nb = notBefore.map(_.toString)
    val key = run.getOrElse("")
    transact(ds):
      sql"""UPDATE manager_items SET status = ${status.wire}, evaluated_run = coalesce($run, evaluated_run),
                                     not_before = $nb::timestamptz, failures = coalesce($failures, failures),
                                     state = $state::jsonb, outcome = coalesce($outcome, outcome), hold = $hold::jsonb,
                                     updated_at = now()
            WHERE id = $itemId AND status = 'active'
              AND coalesce(run_id, '') = $key AND evaluated_run IS DISTINCT FROM $run""".update.run() > 0

  /** A person's (or a hint's) change of one item. Each is a CAS on the statuses it may change. */
  def hold(ds: DataSource, itemId: Long, h: Hold): Boolean =
    val hold = Option(h)
    transact(ds):
      sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
            WHERE id = $itemId AND status IN ('pending', 'held')""".update.run() > 0

  def release(ds: DataSource, itemId: Long): Boolean =
    transact(ds):
      sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
            WHERE id = $itemId AND status = 'held'""".update.run() > 0

  // ── the focus's holds (docs/research-process-managers.md "As built: the focus") ─────────────────

  /** Hold a PENDING item for the focus (`h.focus` = its goal); false = it was not pending any more. */
  def focusHold(ds: DataSource, itemId: Long, h: Hold): Boolean =
    val hold = Option(h)
    transact(ds):
      sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
            WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** Release the focus holds among `ids` (a person's or an approval's hold is never touched); how many. */
  def focusRelease(ds: DataSource, ids: List[Long]): Int =
    if ids.isEmpty then 0
    else
      val arr = ids.map(_.toString)
      transact(ds):
        sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
              WHERE id = ANY($arr::bigint[]) AND status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()

  /** Release every focus hold (the focus is not in force any more); how many. */
  def focusReleaseAll(ds: DataSource): Int =
    transact(ds):
      sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
            WHERE status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()

  /** The items the focus holds now. */
  def focusHeldCount(ds: DataSource): Int =
    connect(ds)(
      sql"""SELECT count(*)::int FROM manager_items WHERE status = 'held' AND hold->>'focus' IS NOT NULL"""
        .query[Int]
        .run()
        .head
    )

  def setPriority(ds: DataSource, itemId: Long, priority: Int): Boolean =
    transact(ds)(
      sql"UPDATE manager_items SET priority = $priority, updated_at = now() WHERE id = $itemId".update.run() > 0
    )

  /** Settle an item that is not active (skip: stopped by a person; a manager's stop: cancelled). */
  def settle(ds: DataSource, itemId: Long, status: ItemStatus, outcome: String, from: List[ItemStatus]): Boolean =
    val fs = from.map(_.wire)
    transact(ds):
      sql"""UPDATE manager_items SET status = ${status.wire}, outcome = $outcome, hold = NULL, updated_at = now()
            WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  /** Re-open a settled item: pending again, its run count and failures reset, how often recorded. */
  def reopen(ds: DataSource, itemId: Long): Boolean =
    transact(ds):
      sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, outcome = NULL,
                                     hold = NULL,
                                     state = jsonb_set(state - 'history', '{reopened}',
                                       to_jsonb(coalesce((state->>'reopened')::int, 0) + 1)),
                                     updated_at = now()
            WHERE id = $itemId AND status IN ('done', 'stopped', 'failed', 'cancelled')""".update.run() > 0

  /** A converged item selected again because it went stale: pending at `priority` (exactly: the lowest), its runs
    * counted from zero, its run history dropped (a new cycle), why in its outcome.
    */
  def restale(ds: DataSource, itemId: Long, priority: Int, why: String): Boolean =
    transact(ds):
      sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, hold = NULL,
                                     priority = $priority, origin = ${ItemOrigin.Selection.wire}, outcome = $why,
                                     state = state - 'history', updated_at = now()
            WHERE id = $itemId AND status = 'done'""".update.run() > 0

  /** A manager's open items (pending, held, active) as (kind, target) — what
    * [[com.histmersion.domain.ManagerPolicy.waitsFor]] reads.
    */
  def openKeys(ds: DataSource, managerId: String): List[(String, String)] =
    connect(ds):
      sql"""SELECT kind, target FROM manager_items WHERE manager_id = $managerId
            AND status IN ('pending', 'held', 'active')""".query[(String, String)].run().toList

  /** Does manager `managerId` have (or ever had) an item of `kind` on `target`? */
  def hasItem(ds: DataSource, managerId: String, kind: String, target: String): Boolean =
    connect(ds):
      sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
        .query[Int]
        .run()
        .nonEmpty

  /** Every open item of a manager cancelled (a stop); returns how many. */
  def cancelOpen(ds: DataSource, managerId: String, why: String): Int =
    transact(ds):
      sql"""UPDATE manager_items SET status = 'cancelled', outcome = $why, updated_at = now()
            WHERE manager_id = $managerId AND status IN ('pending', 'held', 'active')""".update.run()

  /** Back to pending (an active item whose run vanished, or was never submitted). */
  def requeue(ds: DataSource, itemId: Long, outcome: String): Boolean =
    transact(ds):
      sql"""UPDATE manager_items SET status = 'pending', run_id = NULL, outcome = $outcome, updated_at = now()
            WHERE id = $itemId AND status = 'active'""".update.run() > 0

  // ── selection: what the review manager picks by itself ─────────────────────────────────────────

  /** Articles with open MAJORs in their review ledger and no item of this manager yet, the most open MAJORs first;
    * `tags` (all of) and `goal` (linked to it) narrow them.
    */
  def openMajorArticles(
      ds: DataSource,
      managerId: String,
      kind: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[(String, Int)] =
    connect(ds):
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
            ORDER BY m.n DESC, a.slug LIMIT ${math.max(1, limit)}""".query[(String, Int)].run().toList

  /** Articles never reviewed (no ledger, no review-article run) and with no item of this manager. */
  def unreviewedArticles(
      ds: DataSource,
      managerId: String,
      kind: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[String] =
    connect(ds):
      sql"""SELECT a.slug FROM articles a
            WHERE a.kind <> 'index' AND a.tags @> $tags::text[]
              AND NOT EXISTS (SELECT 1 FROM review_ledgers l WHERE l.slug = a.slug)
              AND NOT EXISTS (SELECT 1 FROM jobs j WHERE j.kind = $JobKind AND j.pipeline = 'review-article'
                              AND j.target_id = a.slug)
              AND ($goal::text IS NULL OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $goal
                                                   AND g.kind = 'article' AND g.ref = a.slug))
              AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind
                              AND i.target = a.slug)
            ORDER BY a.updated_at, a.slug LIMIT ${math.max(1, limit)}""".query[String].run().toList

  /** The review manager's stale candidates: its converged review items (by when they converged: their item's
    * `updated_at`, with the filter hits recorded then), then articles whose ledger holds no open MAJOR and that no item
    * of it drives (by the ledger's `updated_at`) — oldest first, `tags`/`goal` narrowing as in the selection. (slug,
    * converged at, filter hits then, the done item if any).
    */
  def staleCandidates(
      ds: DataSource,
      managerId: String,
      kind: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[(String, String, Option[Int], Option[Long])] =
    val lim = math.max(1, limit)
    val g = goal.map(_.trim).getOrElse("")
    connect(ds):
      val done = Sql
        .of(
          s"""SELECT i.target, ${at("i.updated_at")}, (i.state->>'filterHits')::int, i.id FROM manager_items i
            JOIN articles a ON a.slug = i.target
            WHERE i.manager_id = ? AND i.kind = ? AND i.status = 'done' AND a.tags @> ?::text[]
              AND (? = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = ?
                                     AND g.kind = 'article' AND g.ref = a.slug))
            ORDER BY i.updated_at, i.id LIMIT ?""",
          Param.S(managerId),
          Param.S(kind),
          Param.A(tags),
          Param.S(g),
          Param.S(g),
          Param.I(lim)
        )
        .frag
        .query[(String, String, Option[Int], Long)]
        .run()
        .toList
        .map((s, t, n, id) => (s, t, n, Some(id)))
      val ledgers = Sql
        .of(
          s"""SELECT l.slug, ${at("l.updated_at")} FROM review_ledgers l JOIN articles a ON a.slug = l.slug
            WHERE a.kind <> 'index' AND a.tags @> ?::text[]
              AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                              WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
              AND (? = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = ?
                                     AND g.kind = 'article' AND g.ref = a.slug))
              AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = ? AND i.kind = ? AND i.target = a.slug)
            ORDER BY l.updated_at, l.slug LIMIT ?""",
          Param.A(tags),
          Param.S(g),
          Param.S(g),
          Param.S(managerId),
          Param.S(kind),
          Param.I(lim)
        )
        .frag
        .query[(String, String)]
        .run()
        .toList
        .map((s, t) => (s, t, None, None))
      (done ++ ledgers).sortBy(_._2).take(lim)

  /** Articles that record a coverage filter and that no item of this manager has open (pending, held, active), with
    * when their last extension item settled (None: never extended) — the ones never extended first, then the longest
    * ago; `tags`/`goal` narrow as in the selection.
    */
  def extendCandidates(
      ds: DataSource,
      managerId: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[(String, Option[String])] =
    val g = goal.map(_.trim).getOrElse("")
    connect(ds):
      Sql
        .of(
          s"""SELECT a.slug, ${at("x.updated_at")} FROM articles a
            LEFT JOIN manager_items x ON x.manager_id = ? AND x.kind = ? AND x.target = a.slug
            WHERE a.coverage_filter IS NOT NULL AND a.kind <> 'index' AND a.tags @> ?::text[]
              AND (? = '' OR EXISTS (SELECT 1 FROM goal_links gl WHERE gl.goal_slug = ?
                                     AND gl.kind = 'article' AND gl.ref = a.slug))
              AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = ? AND i.target = a.slug
                              AND i.status IN ('pending', 'held', 'active'))
            ORDER BY x.updated_at NULLS FIRST, a.slug LIMIT ?""",
          Param.S(managerId),
          Param.S(ArticleExtend),
          Param.A(tags),
          Param.S(g),
          Param.S(g),
          Param.S(managerId),
          Param.I(math.max(1, limit))
        )
        .frag
        .query[(String, Option[String])]
        .run()
        .toList

  // ── the autopilot's views (few queries for every manager at once) ──────────────────────────────

  /** A manager without its counts: what the autopilot's ensure and status read for every manager. `planItem` = it has a
    * plan-goal item (any status).
    */
  final case class Light(
      id: String,
      kind: String,
      goal: Option[String],
      state: String,
      cron: Option[String],
      config: ManagerConfig,
      statusNote: Option[String],
      planItem: Boolean
  )
  private given DbCodec[Light] = DbCodec.derived

  def light(ds: DataSource): List[Light] =
    connect(ds):
      Sql
        .of(
          s"""SELECT m.id, m.kind, m.goal_slug, m.state, m.cron, m.config::text, m.status_note,
                        EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = m.id AND i.kind = ?)
                 FROM agent_managers m ORDER BY m.id""",
          Param.S(PlanGoalItem)
        )
        .frag
        .query[Light]
        .run()
        .toList

  /** Per manager: its open items (pending, held, active), the items it settled since `since`, and when it last recorded
    * an event. A watcher's ingest backlog is not counted: it drains by the day's ingest caps, not at the pace of its
    * runs (it has its own estimate, [[com.histmersion.domain.IngestBacklog.daysToDrain]]).
    */
  def pace(ds: DataSource, since: java.time.Instant): Map[String, (Int, Int, Option[String])] =
    val t = since.toString
    connect(ds):
      Sql
        .of(
          s"""SELECT m.id,
                        (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> ?
                           AND i.status IN ('pending', 'held', 'active')),
                        (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> ?
                           AND i.status IN ('done', 'stopped', 'failed') AND i.updated_at >= ?::timestamptz),
                        (SELECT ${at("max(e.at)")} FROM manager_events e WHERE e.manager_id = m.id)
                 FROM agent_managers m""",
          Param.S(OaIngest),
          Param.S(OaIngest),
          Param.S(t)
        )
        .frag
        .query[(String, Int, Int, Option[String])]
        .run()
        .toList
        .map((id, o, d, l) => id -> (o, d, l))
        .toMap

  /** Open items of `kind` in manager `id` (pending, held or active) — e.g. a goal's compiles. `focusHeld` = an item the
    * focus holds counts as held (false: as the pending item it really is).
    */
  def openOf(
      ds: DataSource,
      managerId: String,
      kinds: List[String],
      statuses: List[String],
      focusHeld: Boolean = true
  ): Int =
    connect(ds)(
      Sql
        .of(
          "SELECT count(*)::int FROM manager_items WHERE manager_id = ? AND kind = ANY(?::text[]) AND status = ANY(?::text[])" +
            (if focusHeld then "" else " AND NOT (status = 'held' AND hold->>'focus' IS NOT NULL)"),
          Param.S(managerId),
          Param.A(kinds),
          Param.A(statuses)
        )
        .frag
        .query[Int]
        .run()
        .head
    )

  /** The articles that passed their review: a review ledger with no open MAJOR, and no open item of review manager
    * `reviewId` (a review queued or running is not passed yet).
    */
  def passedReview(ds: DataSource, reviewId: String): Set[String] =
    connect(ds):
      sql"""SELECT l.slug FROM review_ledgers l
            WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                              WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
              AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $reviewId AND i.target = l.slug
                              AND i.status IN ('pending', 'held', 'active'))""".query[String].run().toSet

  /** Open items on `target` in manager `id`, any kind. */
  def openOn(ds: DataSource, managerId: String, target: String): Boolean =
    connect(ds)(sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND target = $target
                      AND status IN ('pending', 'held', 'active') LIMIT 1""".query[Int].run().nonEmpty)

  /** An article's tags and the goals it is linked to (what a hint's effect matches). */
  def articleFacets(ds: DataSource, slug: String): (List[String], List[String]) =
    connect(ds):
      val tags = sql"SELECT tags FROM articles WHERE slug = $slug".query[List[String]].run().headOption.getOrElse(Nil)
      val goals =
        sql"SELECT goal_slug FROM goal_links WHERE kind = 'article' AND ref = $slug".query[String].run().toList
      (tags, goals)

  /** Is there a source with this citekey or DOI (a hold waiting for it is released)? */
  def sourceExists(ds: DataSource, ref: String): Boolean =
    val r = ref.trim
    val doi = r.toLowerCase.stripPrefix("https://doi.org/").stripPrefix("doi:")
    connect(ds)(
      sql"""SELECT 1 FROM sources WHERE citekey = $r OR lower(doi) = $doi LIMIT 1""".query[Int].run().nonEmpty
    )

  // ── the watchers' ingest backlog (`oa-ingest` items) ───────────────────────────────────────────

  /** Every pending backlog item of an ACTIVE watcher, the most urgent first (priority, then the oldest). */
  def backlogPending(ds: DataSource): List[ManagerItem] =
    connect(ds)(
      (itemSelect ++ Sql.of(
        """ WHERE kind = ? AND status = 'pending'
            AND manager_id IN (SELECT id FROM agent_managers WHERE kind = 'watch' AND state = 'active')
          ORDER BY priority DESC, id""",
        Param.S(OaIngest)
      )).frag.query[ItemRow].run().toList.map(toItem)
    )

  /** The open (pending, held, active) backlog items of every watcher — the dedupe across watchers. */
  def backlogOpen(ds: DataSource): List[ManagerItem] =
    connect(ds)(
      (itemSelect ++ Sql.of(
        " WHERE kind = ? AND status IN ('pending', 'held', 'active') ORDER BY id",
        Param.S(OaIngest)
      )).frag.query[ItemRow].run().toList.map(toItem)
    )

  /** The open backlog item of any watcher for this DOI, if one exists (the oldest). */
  def backlogOpenFor(ds: DataSource, doi: String): Option[ManagerItem] =
    connect(ds)(
      (itemSelect ++ Sql.of(
        " WHERE kind = ? AND target = ? AND status IN ('pending', 'held', 'active') ORDER BY id LIMIT 1",
        Param.S(OaIngest),
        Param.S(doi)
      )).frag.query[ItemRow].run().headOption.map(toItem)
    )

  /** Per watcher: its backlog items by status (pending, held) — what the status lines count. */
  def backlogCounts(ds: DataSource): Map[String, (Int, Int)] =
    connect(ds):
      sql"""SELECT manager_id, count(*) FILTER (WHERE status = 'pending')::int, count(*) FILTER (WHERE status = 'held')::int
            FROM manager_items WHERE kind = $OaIngest AND status IN ('pending', 'held') GROUP BY manager_id"""
        .query[(String, Int, Int)]
        .run()
        .toList
        .map((m, p, h) => m -> (p, h))
        .toMap

  /** Per manager: its events of `kind` since `since` (every watcher's ingests today, in one query). */
  def countEventsBy(ds: DataSource, kind: String, since: java.time.Instant): Map[String, Int] =
    val at = since.toString
    connect(ds)(sql"""SELECT manager_id, count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz
                      GROUP BY manager_id""".query[(String, Int)].run().toMap)

  /** An item's state replaced (a backlog entry gained a goal); a CAS on the statuses it may be in. */
  def setItemState(ds: DataSource, itemId: Long, state: ItemState, from: List[ItemStatus]): Boolean =
    val fs = from.map(_.wire)
    transact(ds):
      sql"""UPDATE manager_items SET state = $state::jsonb, updated_at = now()
            WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  /** A pending backlog item tried again later: one more failure, not before `notBefore`, why. */
  def backlogRetry(
      ds: DataSource,
      itemId: Long,
      failures: Int,
      notBefore: java.time.Instant,
      outcome: String
  ): Boolean =
    val nb = notBefore.toString
    transact(ds):
      sql"""UPDATE manager_items SET failures = $failures, attempts = attempts + 1, not_before = $nb::timestamptz,
                                     outcome = $outcome, updated_at = now()
            WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** The citekey of the source holding this DOI, if one does. */
  def citekeyOfDoi(ds: DataSource, doi: String): Option[String] =
    val d = doi.trim.toLowerCase
    connect(ds)(
      sql"SELECT citekey FROM sources WHERE lower(doi) = $d ORDER BY citekey LIMIT 1".query[String].run().headOption
    )

  /** One open acquisition item a WATCHER'S SWEEP created, found by structure, not by its wording: an
    * `acquisition-sweep` run dispatched by a watcher (`jobs.manager_id`) picked the DOI (its `rank` step's checked
    * answer), the item was created while that run's `queue` step ran (the only step that adds acquisition items), and
    * the run linked it to its goal by that DOI (`goal_links` kind `queue-item`, note = the DOI). A person's item, a
    * plan's, a currency check's match none of the three. `isOa` = the ranking marked the work open access.
    */
  final case class SweepAcquireItem(
      item: Int,
      text: String,
      topic: Option[String],
      goal: String,
      managerId: String,
      job: String,
      doi: String,
      isOa: Boolean,
      title: Option[String],
      year: Option[Int],
      authors: List[String],
      venue: Option[String],
      why: Option[String]
  )
  private given DbCodec[SweepAcquireItem] = DbCodec.derived

  def sweepAcquireItems(ds: DataSource): List[SweepAcquireItem] =
    connect(ds):
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
  def openAcquireCount(ds: DataSource): Int =
    connect(ds)(
      sql"SELECT count(*)::int FROM queue_items WHERE list = 'acquire' AND status = 'open'".query[Int].run().head
    )

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  private given DbCodec[ManagerEvent] = DbCodec.derived

  private val eventSelect =
    Sql.raw(s"SELECT id, manager_id, item_id, run_id, ${at("at")}, kind, text FROM manager_events")

  def eventIn(managerId: String, itemId: Option[Long], runId: Option[String], kind: String, text: String)(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO manager_events (manager_id, item_id, run_id, kind, text)
                  VALUES ($managerId, $itemId, $runId, $kind, ${text.take(2000)})""".update.run()

  def event(
      ds: DataSource,
      managerId: String,
      itemId: Option[Long],
      runId: Option[String],
      kind: String,
      text: String
  ): Unit =
    transact(ds)(eventIn(managerId, itemId, runId, kind, text))

  /** Newest first; `before` = only events older than that id. */
  def events(ds: DataSource, managerId: String, before: Option[Long], limit: Int): List[ManagerEvent] =
    connect(ds):
      (eventSelect ++ Sql.of(" WHERE manager_id = ?", Param.S(managerId)) ++
        before.fold(Sql.empty)(b => Sql.of(" AND id < ?", Param.L(b))) ++
        Sql.of(" ORDER BY id DESC LIMIT ?", Param.I(math.max(1, limit)))).frag.query[ManagerEvent].run().toList

  def lastEvent(ds: DataSource, managerId: String): Option[ManagerEvent] = events(ds, managerId, None, 1).headOption

  /** How many events of `kind` a manager has recorded since `since` (a watcher's ingests today). */
  def countEvents(ds: DataSource, managerId: String, kind: String, since: java.time.Instant): Int =
    val at = since.toString
    connect(ds)(sql"""SELECT count(*)::int FROM manager_events WHERE manager_id = $managerId AND kind = $kind
                      AND at >= $at::timestamptz""".query[Int].run().head)

  /** How many events of `kind` ALL managers recorded since `since` (every watcher's ingests today: the autopilot's
    * shared cap).
    */
  def countEventsAll(ds: DataSource, kind: String, since: java.time.Instant): Int =
    val at = since.toString
    connect(ds)(
      sql"""SELECT count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz"""
        .query[Int]
        .run()
        .head
    )

  /** The newest event of `kind` (a starved note is logged once per change). */
  def lastEventOf(ds: DataSource, managerId: String, kind: String): Option[ManagerEvent] =
    connect(ds)(
      (eventSelect ++ Sql.of(
        " WHERE manager_id = ? AND kind = ? ORDER BY id DESC LIMIT 1",
        Param.S(managerId),
        Param.S(kind)
      )).frag.query[ManagerEvent].run().headOption
    )

  def pruneEvents(ds: DataSource, days: Int): Int =
    if days <= 0 then 0
    else transact(ds)(sql"DELETE FROM manager_events WHERE at < now() - make_interval(days => $days)".update.run())

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  final private case class HintRow(
      id: Long,
      managerId: String,
      itemId: Option[Long],
      text: String,
      effect: Option[HintEffect],
      author: String,
      standing: Boolean,
      createdAt: String,
      retiredAt: Option[String],
      uses: Int,
      proposal: Option[Long],
      proposed: Option[HintEffect]
  )
  private given DbCodec[HintRow] = DbCodec.derived

  /** A hint, with the newest OPEN `hint-effect` approval about it (a model's proposed action waiting for a person) and
    * that action.
    */
  private val hintSelect = Sql.raw(
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
      ds: DataSource,
      managerId: String,
      itemId: Option[Long],
      text: String,
      effect: Option[HintEffect],
      author: String,
      standing: Boolean
  ): ManagerHint =
    val id = transact(ds):
      sql"""INSERT INTO manager_hints (manager_id, item_id, text, effect, author, standing)
            VALUES ($managerId, $itemId, $text, $effect::jsonb, $author, $standing) RETURNING id"""
        .query[Long]
        .run()
        .head
    hint(ds, id).get

  def hint(ds: DataSource, id: Long): Option[ManagerHint] =
    connect(ds)((hintSelect ++ Sql.of(" WHERE h.id = ?", Param.L(id))).frag.query[HintRow].run().headOption.map(toHint))

  /** A manager's hints, oldest first; `live` = only the ones not retired. */
  def hints(ds: DataSource, managerId: String, live: Boolean): List[ManagerHint] =
    connect(ds):
      (hintSelect ++ Sql.of(" WHERE h.manager_id = ?", Param.S(managerId)) ++
        (if live then Sql.of(" AND h.retired_at IS NULL") else Sql.empty) ++ Sql
          .of(" ORDER BY h.id")).frag.query[HintRow].run().toList.map(toHint)

  /** The action a hint carries now (a model's proposal a person approved). */
  def setHintEffect(ds: DataSource, id: Long, effect: Option[HintEffect]): Boolean =
    transact(ds)(sql"UPDATE manager_hints SET effect = $effect::jsonb WHERE id = $id".update.run() > 0)

  def retireHint(ds: DataSource, managerId: String, id: Long): Boolean =
    transact(ds):
      sql"""UPDATE manager_hints SET retired_at = now() WHERE id = $id AND manager_id = $managerId
            AND retired_at IS NULL""".update.run() > 0

  /** In the dispatch transaction: the hints a run was given, and the once-hints among them retired. */
  def usedIn(hints: List[ManagerHint], runId: String)(using DbCon): Unit =
    hints.foreach { h =>
      sql"""INSERT INTO manager_hint_uses (hint_id, run_id) VALUES (${h.id}, $runId) ON CONFLICT DO NOTHING""".update
        .run()
      if !h.standing then
        sql"UPDATE manager_hints SET retired_at = now() WHERE id = ${h.id} AND retired_at IS NULL".update.run()
    }

  // ── the agent lease ────────────────────────────────────────────────────────────────────────────

  /** Claim (or renew) lease `name` for `owner` for `ttlSeconds`: it is taken when free, expired, or already ours.
    * `true` = we hold it now.
    */
  def claim(ds: DataSource, name: String, owner: String, ttlSeconds: Int): Boolean =
    transact(ds):
      sql"""INSERT INTO agent_leases (name, owner, until) VALUES ($name, $owner, now() + make_interval(secs => $ttlSeconds))
            ON CONFLICT (name) DO UPDATE SET owner = EXCLUDED.owner, until = EXCLUDED.until
            WHERE agent_leases.until < now() OR agent_leases.owner = EXCLUDED.owner
            RETURNING owner""".query[String].run().nonEmpty

  /** Give it up (a clean stop), so the next server need not wait for it to expire. */
  def release(ds: DataSource, name: String, owner: String): Unit =
    val _ = transact(ds)(sql"DELETE FROM agent_leases WHERE name = $name AND owner = $owner".update.run())

  /** Who holds it, until when, and whether that is in the past. */
  def holder(ds: DataSource, name: String): Option[(String, String, Boolean)] =
    connect(ds):
      Sql
        .of(s"SELECT owner, ${at("until")}, until < now() FROM agent_leases WHERE name = ?", Param.S(name))
        .frag
        .query[(String, String, Boolean)]
        .run()
        .headOption
