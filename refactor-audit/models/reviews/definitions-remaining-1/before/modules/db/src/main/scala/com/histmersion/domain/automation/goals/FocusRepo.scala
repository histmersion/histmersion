package com.histmersion.domain.automation.goals
import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql
import com.histmersion.util.database.given

/** The autopilot's focus (V098, docs/research-process-managers.md "As built: the focus"): one `goal_focus` row per goal
  * it touched or a person pinned (active now, since and why; when and why it last left; a person's pin and priority), a
  * person's override of the cap, and the few aggregate reads the focus needs for every goal at once. The items' holds
  * themselves are in [[ManagerRepo]] (they are items).
  */
object FocusRepo:

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: String) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

  /** A goal with what the focus recorded about it. */
  final case class GoalRow(
      slug: String,
      title: String,
      parent: Option[String],
      status: String,
      createdAt: String,
      active: Boolean,
      pinned: Boolean,
      priority: Int,
      since: Option[String],
      why: Option[String],
      leftAt: Option[String],
      leftWhy: Option[String]
  ) derives DbCodec

  def goals()(using DbCon): List[GoalRow] =
    Sql
      .raw(s"""SELECT g.slug, g.title, g.parent_slug, g.status, ${at("g.created_at")},
                       coalesce(f.active, false), coalesce(f.pinned, false), coalesce(f.priority, 0),
                       ${at("f.since")}, f.why, ${at("f.left_at")}, f.left_why
                FROM goals g LEFT JOIN goal_focus f ON f.slug = g.slug ORDER BY g.slug""")
      .frag
      .query[GoalRow]
      .run()
      .toList

  /** One open model-using item of a seeding manager, a watcher or the review manager: `goals` = its manager's goal, or
    * (a review item) the goals its article is linked to; `personHeld` = held by a person (not for an approval, not by
    * the focus) or its manager paused; `approval` = the OPEN approval it waits for (held for it, or its run waits for
    * it), its kind, and the manager's `autoApprove` setting.
    */
  final case class WorkRow(
      id: Long,
      managerId: String,
      managerKind: String,
      kind: String,
      target: String,
      status: String,
      person: Boolean,
      focusHeld: Boolean,
      personHeld: Boolean,
      approval: Option[Long],
      approvalKind: Option[String],
      autoApprove: Option[String],
      goals: List[String]
  ) derives DbCodec

  def work(using DbCon): List[WorkRow] =
    sql"""SELECT i.id, i.manager_id, m.kind, i.kind, i.target, i.status, i.origin = 'person',
                 i.status = 'held' AND i.hold->>'focus' IS NOT NULL,
                 m.state = 'paused' OR (i.status = 'held' AND i.hold->>'focus' IS NULL AND i.hold->>'untilApproval' IS NULL),
                 a.id, a.kind, m.config->>'autoApprove',
                 CASE WHEN m.goal_slug IS NOT NULL THEN ARRAY[m.goal_slug]
                      ELSE ARRAY(SELECT l.goal_slug FROM goal_links l WHERE l.kind = 'article' AND l.ref = i.target
                                 ORDER BY l.goal_slug) END
          FROM manager_items i
          JOIN agent_managers m ON m.id = i.manager_id
          LEFT JOIN jobs j ON j.id = i.run_id
          LEFT JOIN LATERAL (
            SELECT a.id, a.kind FROM approvals a
            WHERE a.state = 'open'
              AND ((i.status = 'held' AND a.id::text = i.hold->>'untilApproval')
                   OR (i.status = 'active' AND j.status = 'waiting' AND a.job_id = i.run_id))
            ORDER BY a.id DESC LIMIT 1) a ON true
          WHERE i.status IN ('pending', 'held', 'active') AND m.state <> 'stopped'
            AND m.kind IN ('seed', 'watch', 'review') AND i.kind NOT IN ('oa-ingest', 'currency-check')
          ORDER BY i.id""".query[WorkRow].run().toList

  /** Per goal: its seeding manager's approved articles not compiled yet (pending, held, active). */
  def compileOpen(using DbCon): Map[String, Int] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.kind = 'seed' AND m.goal_slug IS NOT NULL AND i.kind = 'article-compile'
            AND i.status IN ('pending', 'held', 'active')
          GROUP BY m.goal_slug""".query[(String, Int)].run().toMap

  /** Per goal: the outcome of its LAST settled model-using item when that one stopped or failed (a goal whose newest
    * settled item is done has no stop to report).
    */
  def lastStops()(using DbCon): Map[String, String] =
    sql"""SELECT goal, outcome FROM (
            SELECT DISTINCT ON (m.goal_slug) m.goal_slug AS goal, i.status, coalesce(i.outcome, i.status) AS outcome
            FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
            WHERE m.goal_slug IS NOT NULL AND i.kind NOT IN ('oa-ingest', 'currency-check')
              AND i.status IN ('done', 'stopped', 'failed')
            ORDER BY m.goal_slug, i.updated_at DESC, i.id DESC) t
          WHERE status IN ('stopped', 'failed')""".query[(String, String)].run().toMap

  /** Open goal-plan approvals (what a person has to decide). */
  def openGoalPlans(using DbCon): Int =
    (
      sql"SELECT count(*)::int FROM approvals WHERE kind = 'goal-plan' AND state = 'open'".query[Int].run().head
    )

  /** Per goal: its sources (goal links of kind source). */
  def sourceCounts()(using DbCon): Map[String, Int] =
    sql"""SELECT goal_slug, count(*)::int FROM goal_links WHERE kind = 'source' GROUP BY goal_slug"""
      .query[(String, Int)]
      .run()
      .toMap

  /** The goals linked to each of `refs` by a link of `kind` (source: a citekey, article: a slug). */
  def goalsOf(kind: String, refs: List[String])(using DbCon): Map[String, List[String]] =
    if refs.isEmpty then Map.empty
    else
      sql"""SELECT ref, goal_slug FROM goal_links WHERE kind = $kind AND ref = ANY($refs::text[]) ORDER BY ref, goal_slug"""
        .query[(String, String)]
        .run()
        .toList
        .groupMap(_._1)(_._2)

  /** The targets of a manager's newest runs, newest first (what the fair order counts on from). */
  def recentTargets(managerId: String, n: Int)(using DbCon): List[String] =
    sql"""SELECT target_id FROM jobs WHERE kind = 'agent' AND manager_id = $managerId AND target_id IS NOT NULL
          ORDER BY created_at DESC, id DESC LIMIT ${math.max(0, n)}""".query[String].run().toList

  // ── writes ─────────────────────────────────────────────────────────────────────────────────────

  /** The goal became active now, `why`. */
  def joined(slug: String, why: String, now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    val _ =
      sql"""INSERT INTO goal_focus (slug, active, since, why, updated_at) VALUES ($slug, true, $t::timestamptz, $why, now())
            ON CONFLICT (slug) DO UPDATE SET active = true, since = $t::timestamptz, why = $why, updated_at = now()""".update
        .run()

  /** The goal left the active set now, `why`. */
  def left(slug: String, why: String, now: java.time.Instant)(using DbCon): Unit =
    val t = now.toString
    val _ =
      sql"""UPDATE goal_focus SET active = false, left_at = $t::timestamptz, left_why = $why, updated_at = now()
            WHERE slug = $slug""".update.run()

  /** A person's pin. False = no such goal. */
  def pin(slug: String, pinned: Boolean)(using DbCon): Boolean =
    sql"""INSERT INTO goal_focus (slug, pinned) SELECT slug, $pinned FROM goals WHERE slug = $slug
          ON CONFLICT (slug) DO UPDATE SET pinned = $pinned, updated_at = now()""".update.run() > 0

  /** A person's priority (higher first). False = no such goal. */
  def priority(slug: String, n: Int)(using DbCon): Boolean =
    sql"""INSERT INTO goal_focus (slug, priority) SELECT slug, $n FROM goals WHERE slug = $slug
          ON CONFLICT (slug) DO UPDATE SET priority = $n, updated_at = now()""".update.run() > 0

  /** Every goal leaves the active set (the focus is not in force any more): how many did. */
  def clearActive(why: String, now: java.time.Instant)(using DbCon): Int =
    val t = now.toString
    sql"""UPDATE goal_focus SET active = false, left_at = $t::timestamptz, left_why = $why, updated_at = now()
          WHERE active""".update.run()

  /** A person's cap (None = the config's). */
  def maxOverride(using DbCon): Option[Int] =
    (sql"SELECT max_active_goals FROM agent_autopilot WHERE id".query[Option[Int]].run().headOption.flatten)

  def setMaxOverride(n: Option[Int])(using DbCon): Unit =
    val _ =
      sql"INSERT INTO agent_autopilot (id) VALUES (true) ON CONFLICT DO NOTHING".update.run()
      sql"UPDATE agent_autopilot SET max_active_goals = $n WHERE id".update.run()
