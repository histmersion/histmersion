package com.histmersion.db

import com.histmersion.core.*
import com.histmersion.core.GoalPlans.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** What hangs off a goal (migrations V046, V049): its links (sources, articles, search queries, queue
  * items), its approved plan and run cap, and its schedules. Stateless, like every repo. */
object GoalLinkRepo:

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  private final case class LinkRow(kind: String, ref: String, note: Option[String], title: Option[String], createdAt: String)
  private given DbCodec[LinkRow] = DbCodec.derived

  /** A goal's links, each with the title of what it points at (`None` = it no longer exists; a query
    * has no title). Sources, then articles, queries, queue items; oldest first within a kind. */
  def links(ds: DataSource, goal: String): List[GoalLink] =
    connect(ds):
      sql"""SELECT l.kind, l.ref, l.note,
                   CASE l.kind WHEN 'source'  THEN (SELECT s.title FROM sources s WHERE s.citekey = l.ref)
                               WHEN 'article' THEN (SELECT a.title FROM articles a WHERE a.slug = l.ref)
                               WHEN 'queue-item' THEN (SELECT q.text FROM queue_items q WHERE q.id::text = l.ref)
                               ELSE NULL END,
                   to_char(l.created_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)})
            FROM goal_links l WHERE l.goal_slug = $goal
            ORDER BY CASE l.kind WHEN 'source' THEN 0 WHEN 'article' THEN 1 WHEN 'query' THEN 2 ELSE 3 END,
                     l.created_at, l.ref""".query[LinkRow].run().toList.flatMap { r =>
        LinkKind.parse(r.kind).map(k => GoalLink(k, r.ref, r.note, r.title, r.createdAt))
      }

  /** The refs of one kind (a sweep's queries, a currency check's sources). */
  def refs(ds: DataSource, goal: String, kind: LinkKind): List[String] =
    connect(ds):
      sql"SELECT ref FROM goal_links WHERE goal_slug = $goal AND kind = ${kind.wire} ORDER BY created_at, ref"
        .query[String].run().toList

  /** Link (idempotent: an existing link keeps its note unless a new one is given). `false` = the goal
    * does not exist. */
  def link(ds: DataSource, goal: String, kind: LinkKind, ref: String, note: Option[String]): Boolean =
    transact(ds):
      val exists = sql"SELECT 1 FROM goals WHERE slug = $goal".query[Int].run().nonEmpty
      if exists then
        sql"""INSERT INTO goal_links (goal_slug, kind, ref, note) VALUES ($goal, ${kind.wire}, $ref, $note)
              ON CONFLICT (goal_slug, kind, ref) DO UPDATE SET note = coalesce(EXCLUDED.note, goal_links.note)"""
          .update.run()
      exists

  def unlink(ds: DataSource, goal: String, kind: LinkKind, ref: String): Boolean =
    transact(ds)(sql"DELETE FROM goal_links WHERE goal_slug = $goal AND kind = ${kind.wire} AND ref = $ref".update.run() > 0)

  /** A goal's direct sub-goals. */
  def children(ds: DataSource, goal: String): List[Goal] =
    GoalRepo.children(ds, goal)

  /** Every goal's links counted by kind: (goal, kind) → n. Article links count only articles that
    * exist. */
  def linkCounts(ds: DataSource): Map[(String, String), Int] =
    connect(ds):
      sql"""SELECT l.goal_slug, l.kind, count(*)::int FROM goal_links l
            WHERE l.kind <> 'article' OR EXISTS (SELECT 1 FROM articles a WHERE a.slug = l.ref)
            GROUP BY l.goal_slug, l.kind""".query[(String, String, Int)].run().map((g, k, n) => (g, k) -> n).toMap

  /** The goals that record an approved plan. */
  def planned(ds: DataSource): Set[String] =
    connect(ds)(sql"SELECT slug FROM goals WHERE plan IS NOT NULL".query[String].run().toSet)

  /** Every goal's linked articles that exist: goal → slugs. */
  def articlesByGoal(ds: DataSource): Map[String, List[String]] =
    connect(ds):
      sql"""SELECT l.goal_slug, l.ref FROM goal_links l JOIN articles a ON a.slug = l.ref
            WHERE l.kind = 'article' ORDER BY l.goal_slug, l.ref""".query[(String, String)].run().toList
        .groupMap(_._1)(_._2)

  // ── plan and run cap ───────────────────────────────────────────────────────────────────────────

  /** The approved plan (its JSON) and the goal's run cap. `None` = no such goal. */
  def planAndBudget(ds: DataSource, goal: String): Option[(Option[String], Option[BigDecimal])] =
    connect(ds):
      sql"SELECT plan::text, budget_usd FROM goals WHERE slug = $goal"
        .query[(Option[String], Option[BigDecimal])].run().headOption

  /** Record an approved plan, and when it was approved (V054: two collections' plans for one goal
    * are merged by "the one approved later wins"). */
  def setPlan(ds: DataSource, goal: String, planJson: String): Unit =
    val _ = transact(ds)(sql"""UPDATE goals SET plan = $planJson::jsonb, plan_approved_at = now(), updated_at = now()
                               WHERE slug = $goal""".update.run())

  def setBudget(ds: DataSource, goal: String, usd: Option[BigDecimal]): Unit =
    val _ = transact(ds)(sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $goal".update.run())

  // ── schedules ──────────────────────────────────────────────────────────────────────────────────

  private final case class ScheduleRow(goal: String, kind: String, cron: String, enabled: Boolean,
      budgetUsd: Option[BigDecimal], nextRunAt: Option[String], lastRunAt: Option[String], lastJob: Option[String],
      lastNote: Option[String])
  private given DbCodec[ScheduleRow] = DbCodec.derived

  private val scheduleSelect = Sql.raw(
    s"""SELECT goal_slug, kind, cron, enabled, budget_usd,
              to_char(next_run_at AT TIME ZONE 'UTC', $ts), to_char(last_run_at AT TIME ZONE 'UTC', $ts),
              last_job, last_note
       FROM goal_schedules""")

  private def toSchedule(r: ScheduleRow): Option[GoalSchedule] =
    ScheduleKind.parse(r.kind).map(k =>
      GoalSchedule(r.goal, k, r.cron, r.enabled, r.budgetUsd.map(_.toDouble), r.nextRunAt, r.lastRunAt, r.lastJob, r.lastNote))

  /** Every schedule (or one goal's), the next due first. */
  def schedules(ds: DataSource, goal: Option[String] = None): List[GoalSchedule] =
    val where = goal.fold(Sql.empty)(g => Sql.of(" WHERE goal_slug = ?", Param.S(g)))
    connect(ds):
      (scheduleSelect ++ where ++ " ORDER BY next_run_at NULLS LAST, goal_slug, kind").frag
        .query[ScheduleRow].run().toList.flatMap(toSchedule)

  /** One row here (a bundle's import, before the managers move it into its goal's watcher). */
  def schedule(ds: DataSource, goal: String, kind: ScheduleKind): Option[GoalSchedule] =
    connect(ds):
      (scheduleSelect ++ Sql.of(" WHERE goal_slug = ? AND kind = ?", Param.S(goal), Param.S(kind.wire))).frag
        .query[ScheduleRow].run().headOption.flatMap(toSchedule)

  /** A row here (tests, a bundle's import) before the managers move it into its goal's watcher. */
  def setSchedule(ds: DataSource, goal: String, kind: ScheduleKind, cron: String, enabled: Boolean,
      budgetUsd: Option[BigDecimal], next: Option[java.time.Instant]): Unit =
    val at = next.map(_.toString)
    val _ = transact(ds):
      sql"""INSERT INTO goal_schedules (goal_slug, kind, cron, enabled, budget_usd, next_run_at)
            VALUES ($goal, ${kind.wire}, $cron, $enabled, $budgetUsd, $at::timestamptz)
            ON CONFLICT (goal_slug, kind) DO UPDATE SET cron = EXCLUDED.cron, enabled = EXCLUDED.enabled,
              budget_usd = EXCLUDED.budget_usd, next_run_at = EXCLUDED.next_run_at, updated_at = now()""".update.run()

  def removeSchedule(ds: DataSource, goal: String, kind: ScheduleKind): Boolean =
    transact(ds)(sql"DELETE FROM goal_schedules WHERE goal_slug = $goal AND kind = ${kind.wire}".update.run() > 0)

  /** The goals that still have rows here: schedules the managers have not moved into a watcher yet
    * (since phase 3 a goal's schedules ARE its `watch:<goal>` manager; a bundle's import still writes
    * rows here, and the next managers' tick moves them). */
  def scheduleGoals(ds: DataSource): List[String] =
    connect(ds)(sql"SELECT DISTINCT goal_slug FROM goal_schedules ORDER BY goal_slug".query[String].run().toList)

  /** Take a goal's schedules OUT of the table, in the caller's transaction (the migration into its
    * watcher): two servers migrating at once cannot both get them. */
  def takeSchedulesIn(goal: String)(using DbCon): List[GoalSchedule] =
    Sql.of(s"""DELETE FROM goal_schedules WHERE goal_slug = ? RETURNING goal_slug, kind, cron, enabled, budget_usd,
              to_char(next_run_at AT TIME ZONE 'UTC', $ts), to_char(last_run_at AT TIME ZONE 'UTC', $ts),
              last_job, last_note""", Param.S(goal)).frag.query[ScheduleRow].run().toList.flatMap(toSchedule)
