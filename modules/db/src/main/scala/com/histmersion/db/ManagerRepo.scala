package com.histmersion.db

import com.histmersion.core.Agents.{JobKind, PauseReason, RunState}
import com.histmersion.core.Managers.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The store of the process managers (V087, docs/research-process-managers.md §2): managers, their
  * work items, hints (and which runs they reached), events, and the agent lease. All state lives in
  * these rows, so a restart picks up where the last process left off. Every state change of an item
  * is a compare-and-set on its status (and, for an evaluation, on the run it evaluates), so a tick
  * that runs twice — or two processes, should the lease ever fail — cannot do a step twice. */
object ManagerRepo:

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: String) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

  // the jsonb columns, through their DTOs' codecs ([[Jsonb]]); absent fields are left out when written
  private given configCodec: DbCodec[ManagerConfig]      = Jsonb.codec(ManagerConfig(), dropNulls = true)
  private given stateCodec: DbCodec[ItemState]           = Jsonb.codec(ItemState(), dropNulls = true)
  private given holdCodec: DbCodec[Option[Hold]]         = Jsonb.optional[Hold](dropNulls = true)
  private given effectCodec: DbCodec[Option[HintEffect]] = Jsonb.optional[HintEffect](dropNulls = true)

  // ── managers ───────────────────────────────────────────────────────────────────────────────────

  private final case class ManagerRow(id: String, kind: String, goal: Option[String], state: String, pauseRuns: Boolean,
      config: ManagerConfig, statusNote: Option[String], cron: Option[String], nextTickAt: Option[String], createdAt: String,
      updatedAt: String)
  private given DbCodec[ManagerRow] = DbCodec.derived

  private val managerSelect = Sql.raw(
    s"""SELECT id, kind, goal_slug, state, pause_runs, config::text, status_note, cron, ${at("next_tick_at")},
              ${at("created_at")}, ${at("updated_at")} FROM agent_managers""")

  private def toInfo(ds: DataSource, r: ManagerRow): ManagerInfo =
    ManagerInfo(r.id, r.kind, r.goal, r.state, r.pauseRuns, r.config,
      ManagerConfig(), r.statusNote, r.cron, r.nextTickAt, r.createdAt, r.updatedAt, counts(ds, r.id),
      inFlight(ds, r.id), lastEvent(ds, r.id))

  def list(ds: DataSource): List[ManagerInfo] =
    connect(ds)((managerSelect ++ Sql.of(" ORDER BY created_at, id")).frag.query[ManagerRow].run().toList).map(toInfo(ds, _))

  def get(ds: DataSource, id: String): Option[ManagerInfo] =
    connect(ds)((managerSelect ++ Sql.of(" WHERE id = ?", Param.S(id))).frag.query[ManagerRow].run().headOption)
      .map(toInfo(ds, _))

  /** A new manager; `false` when the id is taken. */
  def create(ds: DataSource, id: String, kind: Kind, goal: Option[String], state: State, config: ManagerConfig): Boolean =
    transact(ds):
      sql"""INSERT INTO agent_managers (id, kind, goal_slug, state, config)
            VALUES ($id, ${kind.wire}, $goal, ${state.wire}, $config::jsonb) ON CONFLICT (id) DO NOTHING""".update.run() > 0

  def setState(ds: DataSource, id: String, state: State, pauseRuns: Boolean): Boolean =
    transact(ds):
      sql"""UPDATE agent_managers SET state = ${state.wire}, pause_runs = $pauseRuns, updated_at = now()
            WHERE id = $id""".update.run() > 0

  def setConfig(ds: DataSource, id: String, config: ManagerConfig, cron: Option[String]): Boolean =
    transact(ds):
      sql"""UPDATE agent_managers SET config = $config::jsonb, cron = $cron, updated_at = now() WHERE id = $id""".update.run() > 0

  /** The derived status line; written only when it changed (no `updated_at` churn every tick). */
  def setStatusNote(ds: DataSource, id: String, note: String): Boolean =
    transact(ds):
      sql"""UPDATE agent_managers SET status_note = $note
            WHERE id = $id AND status_note IS DISTINCT FROM $note""".update.run() > 0

  def delete(ds: DataSource, id: String): Boolean =
    transact(ds)(sql"DELETE FROM agent_managers WHERE id = $id".update.run() > 0)

  def counts(ds: DataSource, id: String): ItemCounts =
    ItemCounts.of(connect(ds)(sql"""SELECT status, count(*)::int FROM manager_items WHERE manager_id = $id GROUP BY status"""
      .query[(String, Int)].run().toList))

  /** Its runs not settled yet (queued, running, paused, waiting). */
  def inFlight(ds: DataSource, id: String): Int =
    connect(ds)(sql"""SELECT count(*)::int FROM jobs WHERE kind = $JobKind AND manager_id = $id
                      AND status IN ('queued', 'running', 'paused', 'waiting')""".query[Int].run().head)

  /** Its runs that run, or will run again without a person (what `maxRuns` counts): queued, running,
    * waiting for an approval, and paused for a reason that resumes by itself — a restart's
    * `interrupted` and a `stalled` pause (both carry `resume_at`), an `allowance` or `plan-quota` pause.
    * Counting only `queued`/`running` let a restart overshoot (#1803): boot pauses the runs it
    * interrupted, the first tick saw no run and started `maxRuns` new ones, and a minute later the
    * engine resumed the interrupted ones beside them. Only a pause a person must lift (`requested`,
    * `budget`, `provider`) frees its place — resuming it is that person's call. */
  def running(ds: DataSource, id: String): Int =
    val (allowance, quota) = (s"${PauseReason.Allowance.wire}:%", s"${PauseReason.PlanQuota.wire}:%")
    connect(ds)(sql"""SELECT count(*)::int FROM jobs WHERE kind = $JobKind AND manager_id = $id
                      AND (status IN ('queued', 'running', 'waiting')
                           OR (status = 'paused' AND (resume_at IS NOT NULL OR error LIKE $allowance OR error LIKE $quota)))"""
      .query[Int].run().head)

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  private final case class ItemRow(id: Long, managerId: String, kind: String, target: String, status: String, priority: Int,
      attempts: Int, failures: Int, runId: Option[String], evaluatedRun: Option[String], notBefore: Option[String],
      hold: Option[Hold], state: ItemState, outcome: Option[String], origin: String, createdAt: String, updatedAt: String)
  private given DbCodec[ItemRow] = DbCodec.derived

  private val itemSelect = Sql.raw(
    s"""SELECT id, manager_id, kind, target, status, priority, attempts, failures, run_id, evaluated_run,
              ${at("not_before")}, hold::text, state::text, outcome, origin, ${at("created_at")}, ${at("updated_at")}
       FROM manager_items""")

  private def toItem(r: ItemRow): ManagerItem =
    ManagerItem(r.id, r.managerId, r.kind, r.target, r.status, r.priority, r.attempts, r.failures, r.runId, r.evaluatedRun,
      r.notBefore, r.hold, r.state, r.outcome,
      r.origin, r.createdAt, r.updatedAt)

  def item(ds: DataSource, id: Long): Option[ManagerItem] =
    connect(ds)((itemSelect ++ Sql.of(" WHERE id = ?", Param.L(id))).frag.query[ItemRow].run().headOption.map(toItem))

  def itemFor(ds: DataSource, managerId: String, kind: String, target: String): Option[ManagerItem] =
    connect(ds)((itemSelect ++ Sql.of(" WHERE manager_id = ? AND kind = ? AND target = ?", Param.S(managerId), Param.S(kind),
      Param.S(target))).frag.query[ItemRow].run().headOption.map(toItem))

  /** A page of a manager's items: by `status` (none = all), the most urgent first. */
  def items(ds: DataSource, managerId: String, status: Option[ItemStatus], offset: Int, limit: Int): (List[ManagerItem], Int) =
    val where = Sql.of(" WHERE manager_id = ?", Param.S(managerId)) ++
      status.fold(Sql.empty)(s => Sql.of(" AND status = ?", Param.S(s.wire)))
    connect(ds):
      val total = (Sql.of("SELECT count(*)::int FROM manager_items") ++ where).frag.query[Int].run().head
      val rows = (itemSelect ++ where ++ Sql.of(
        """ ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'pending' THEN 1 WHEN 'held' THEN 2 ELSE 3 END,
             priority DESC, coalesce(not_before, '-infinity'::timestamptz), updated_at DESC, id
           OFFSET ? LIMIT ?""", Param.I(math.max(0, offset)), Param.I(math.max(1, limit))))
        .frag.query[ItemRow].run().toList.map(toItem)
      (rows, total)

  def withStatus(ds: DataSource, managerId: String, status: ItemStatus): List[ManagerItem] =
    connect(ds)((itemSelect ++ Sql.of(" WHERE manager_id = ? AND status = ? ORDER BY priority DESC, id",
      Param.S(managerId), Param.S(status.wire))).frag.query[ItemRow].run().toList.map(toItem))

  /** Add an item, or — when it exists — bring it back: `reopen` sets a settled one pending again, and
    * the priority is raised to `priority` (an active one keeps running). Returns the item's id and
    * whether it was new. */
  def upsert(ds: DataSource, managerId: String, kind: String, target: String, origin: ItemOrigin, priority: Int,
      reopen: Boolean): (Long, Boolean) =
    transact(ds):
      sql"""INSERT INTO manager_items (manager_id, kind, target, origin, priority)
            VALUES ($managerId, $kind, $target, ${origin.wire}, $priority)
            ON CONFLICT (manager_id, kind, target) DO NOTHING RETURNING id""".query[Long].run().headOption match
        case Some(id) => (id, true)
        case None =>
          val id = sql"""SELECT id FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
            .query[Long].run().head
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

  /** Dispatch, in the caller's transaction: pending → active with `run`, one more attempt, the open
    * MAJORs before it in its state. `false` = it was no longer pending (another tick took it). */
  def dispatchIn(itemId: Long, run: String, state: ItemState)(using DbCon): Boolean =
    sql"""UPDATE manager_items SET status = ${ItemStatus.Active.wire}, run_id = $run, attempts = attempts + 1,
                                   state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ${ItemStatus.Pending.wire}""".update.run() > 0

  /** Active items whose run is terminal and not evaluated yet (or whose run is gone): the evaluation
    * queue. */
  def toEvaluate(ds: DataSource, managerId: String): List[(ManagerItem, Option[(String, Option[String])])] =
    val rows = connect(ds):
      (itemSelect ++ Sql.of(" WHERE manager_id = ? AND status = 'active' ORDER BY id", Param.S(managerId)))
        .frag.query[ItemRow].run().toList.map(toItem)
    rows.flatMap { i =>
      val run = i.runId.flatMap(r => connect(ds)(sql"SELECT status, error FROM jobs WHERE id = $r"
        .query[(String, Option[String])].run().headOption))
      run match
        case None => List(i -> None) // its run was deleted
        case Some((st, err)) if RunState.parse(st).exists(_.isTerminal) && !i.evaluatedRun.contains(i.runId.get) =>
          List(i -> Some((st, err)))
        case _ => Nil
    }

  /** Record an evaluation (keyed by the run: a second evaluation of the same run changes nothing). */
  def evaluated(ds: DataSource, itemId: Long, run: Option[String], status: ItemStatus, notBefore: Option[java.time.Instant],
      failures: Option[Int], state: ItemState, outcome: Option[String]): Boolean =
    val nb  = notBefore.map(_.toString)
    val key = run.getOrElse("")
    transact(ds):
      sql"""UPDATE manager_items SET status = ${status.wire}, evaluated_run = coalesce($run, evaluated_run),
                                     not_before = $nb::timestamptz, failures = coalesce($failures, failures),
                                     state = $state::jsonb, outcome = coalesce($outcome, outcome), updated_at = now()
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

  def setPriority(ds: DataSource, itemId: Long, priority: Int): Boolean =
    transact(ds)(sql"UPDATE manager_items SET priority = $priority, updated_at = now() WHERE id = $itemId".update.run() > 0)

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

  /** Articles with open MAJORs in their review ledger and no item of this manager yet, the most open
    * MAJORs first; `tags` (all of) and `goal` (linked to it) narrow them. */
  def openMajorArticles(ds: DataSource, managerId: String, kind: String, tags: List[String], goal: Option[String],
      limit: Int): List[(String, Int)] =
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
  def unreviewedArticles(ds: DataSource, managerId: String, kind: String, tags: List[String], goal: Option[String],
      limit: Int): List[String] =
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

  /** An article's tags and the goals it is linked to (what a hint's effect matches). */
  def articleFacets(ds: DataSource, slug: String): (List[String], List[String]) =
    connect(ds):
      val tags = sql"SELECT tags FROM articles WHERE slug = $slug".query[List[String]].run().headOption.getOrElse(Nil)
      val goals = sql"SELECT goal_slug FROM goal_links WHERE kind = 'article' AND ref = $slug".query[String].run().toList
      (tags, goals)

  /** Is there a source with this citekey or DOI (a hold waiting for it is released)? */
  def sourceExists(ds: DataSource, ref: String): Boolean =
    val r = ref.trim
    val doi = r.toLowerCase.stripPrefix("https://doi.org/").stripPrefix("doi:")
    connect(ds)(sql"""SELECT 1 FROM sources WHERE citekey = $r OR lower(doi) = $doi LIMIT 1""".query[Int].run().nonEmpty)

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  private given DbCodec[ManagerEvent] = DbCodec.derived

  private val eventSelect = Sql.raw(s"SELECT id, manager_id, item_id, run_id, ${at("at")}, kind, text FROM manager_events")

  def eventIn(managerId: String, itemId: Option[Long], runId: Option[String], kind: String, text: String)(using DbCon): Unit =
    val _ = sql"""INSERT INTO manager_events (manager_id, item_id, run_id, kind, text)
                  VALUES ($managerId, $itemId, $runId, $kind, ${text.take(2000)})""".update.run()

  def event(ds: DataSource, managerId: String, itemId: Option[Long], runId: Option[String], kind: String, text: String): Unit =
    transact(ds)(eventIn(managerId, itemId, runId, kind, text))

  /** Newest first; `before` = only events older than that id. */
  def events(ds: DataSource, managerId: String, before: Option[Long], limit: Int): List[ManagerEvent] =
    connect(ds):
      (eventSelect ++ Sql.of(" WHERE manager_id = ?", Param.S(managerId)) ++
        before.fold(Sql.empty)(b => Sql.of(" AND id < ?", Param.L(b))) ++
        Sql.of(" ORDER BY id DESC LIMIT ?", Param.I(math.max(1, limit)))).frag.query[ManagerEvent].run().toList

  def lastEvent(ds: DataSource, managerId: String): Option[ManagerEvent] = events(ds, managerId, None, 1).headOption

  /** The newest event of `kind` (a starved note is logged once per change). */
  def lastEventOf(ds: DataSource, managerId: String, kind: String): Option[ManagerEvent] =
    connect(ds)((eventSelect ++ Sql.of(" WHERE manager_id = ? AND kind = ? ORDER BY id DESC LIMIT 1", Param.S(managerId),
      Param.S(kind))).frag.query[ManagerEvent].run().headOption)

  def pruneEvents(ds: DataSource, days: Int): Int =
    if days <= 0 then 0
    else transact(ds)(sql"DELETE FROM manager_events WHERE at < now() - make_interval(days => $days)".update.run())

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  private final case class HintRow(id: Long, managerId: String, itemId: Option[Long], text: String, effect: Option[HintEffect],
      author: String, standing: Boolean, createdAt: String, retiredAt: Option[String], uses: Int)
  private given DbCodec[HintRow] = DbCodec.derived

  private val hintSelect = Sql.raw(
    s"""SELECT h.id, h.manager_id, h.item_id, h.text, h.effect::text, h.author, h.standing, ${at("h.created_at")},
              ${at("h.retired_at")}, (SELECT count(*)::int FROM manager_hint_uses u WHERE u.hint_id = h.id)
       FROM manager_hints h""")

  private def toHint(r: HintRow): ManagerHint =
    ManagerHint(r.id, r.managerId, r.itemId, r.text, r.effect, r.author, r.standing,
      r.createdAt, r.retiredAt, r.uses)

  def addHint(ds: DataSource, managerId: String, itemId: Option[Long], text: String, effect: Option[HintEffect],
      author: String, standing: Boolean): ManagerHint =
    val id = transact(ds):
      sql"""INSERT INTO manager_hints (manager_id, item_id, text, effect, author, standing)
            VALUES ($managerId, $itemId, $text, $effect::jsonb, $author, $standing) RETURNING id""".query[Long].run().head
    hint(ds, id).get

  def hint(ds: DataSource, id: Long): Option[ManagerHint] =
    connect(ds)((hintSelect ++ Sql.of(" WHERE h.id = ?", Param.L(id))).frag.query[HintRow].run().headOption.map(toHint))

  /** A manager's hints, oldest first; `live` = only the ones not retired. */
  def hints(ds: DataSource, managerId: String, live: Boolean): List[ManagerHint] =
    connect(ds):
      (hintSelect ++ Sql.of(" WHERE h.manager_id = ?", Param.S(managerId)) ++
        (if live then Sql.of(" AND h.retired_at IS NULL") else Sql.empty) ++ Sql.of(" ORDER BY h.id"))
        .frag.query[HintRow].run().toList.map(toHint)

  def retireHint(ds: DataSource, managerId: String, id: Long): Boolean =
    transact(ds):
      sql"""UPDATE manager_hints SET retired_at = now() WHERE id = $id AND manager_id = $managerId
            AND retired_at IS NULL""".update.run() > 0

  /** In the dispatch transaction: the hints a run was given, and the once-hints among them retired. */
  def usedIn(hints: List[ManagerHint], runId: String)(using DbCon): Unit =
    hints.foreach { h =>
      sql"""INSERT INTO manager_hint_uses (hint_id, run_id) VALUES (${h.id}, $runId) ON CONFLICT DO NOTHING""".update.run()
      if !h.standing then sql"UPDATE manager_hints SET retired_at = now() WHERE id = ${h.id} AND retired_at IS NULL".update.run()
    }

  // ── the agent lease ────────────────────────────────────────────────────────────────────────────

  /** Claim (or renew) lease `name` for `owner` for `ttlSeconds`: it is taken when free, expired, or
    * already ours. `true` = we hold it now. */
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
      Sql.of(s"SELECT owner, ${at("until")}, until < now() FROM agent_leases WHERE name = ?", Param.S(name))
        .frag.query[(String, String, Boolean)].run().headOption
