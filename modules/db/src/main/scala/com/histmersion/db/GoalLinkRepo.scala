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

  def schedule(ds: DataSource, goal: String, kind: ScheduleKind): Option[GoalSchedule] =
    connect(ds):
      (scheduleSelect ++ Sql.of(" WHERE goal_slug = ? AND kind = ?", Param.S(goal), Param.S(kind.wire))).frag
        .query[ScheduleRow].run().headOption.flatMap(toSchedule)

  /** Create or change a schedule, with its next firing (computed by the caller from the cron). */
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

  /** Enabled schedules whose next firing is at or before `now`. */
  def due(ds: DataSource, now: java.time.Instant): List[GoalSchedule] =
    val at = now.toString
    connect(ds):
      (scheduleSelect ++ Sql.of(" WHERE enabled AND next_run_at IS NOT NULL AND next_run_at <= ?::timestamptz", Param.S(at)) ++
        " ORDER BY next_run_at, goal_slug, kind").frag.query[ScheduleRow].run().toList.flatMap(toSchedule)

  /** After a tick: what it did (`note`), the run it started (if any), and the next firing. `ran` =
    * a run was started now (`last_run_at`). `next = None` with `advance = false` leaves the schedule
    * due (deferred to the next tick). */
  def ticked(ds: DataSource, goal: String, kind: ScheduleKind, note: String, job: Option[String],
      next: Option[java.time.Instant], advance: Boolean): Unit =
    val at = next.map(_.toString)
    val _ = transact(ds):
      sql"""UPDATE goal_schedules SET last_note = $note,
                   last_job = coalesce($job, last_job),
                   last_run_at = CASE WHEN $job::text IS NULL THEN last_run_at ELSE now() END,
                   next_run_at = CASE WHEN $advance THEN $at::timestamptz ELSE next_run_at END,
                   updated_at = now()
            WHERE goal_slug = $goal AND kind = ${kind.wire}""".update.run()
