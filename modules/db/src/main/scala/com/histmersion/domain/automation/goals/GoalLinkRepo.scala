package com.histmersion
package domain.automation.goals
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** What hangs off a goal (migrations V046, V049): its links (sources, articles, search queries, queue items), its
  * approved plan and run cap, and its schedules. Stateless, like every repo.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the goal link tables and their row codecs")
object GoalLinkRepo:
  type ItemId = ItemId.T
  object ItemId extends Newtype[Int, Pure]

  private val ts = sql"""'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  final private case class LinkRow(
      kind: Goal.LinkKind.Wire,
      ref: GoalLink.Ref,
      note: Option[GoalLink.Note],
      title: Option[GoalLink.Title],
      createdAt: GoalLink.CreatedAt,
      queue: Option[GoalLink.Queue]
  ) derives DbCodec

  /** A goal's links, each with the title of what it points at (`None` = it no longer exists; a query has no title).
    * Sources, then articles, queries, queue items; oldest first within a kind.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def links(goal: domain.automation.Goal.Slug)(using DbCon): List[GoalLink] =
    sql"""SELECT l.kind, l.ref, l.note,
                 CASE l.kind WHEN 'source'  THEN (SELECT s.title FROM sources s WHERE s.citekey = l.ref)
                             WHEN 'article' THEN (SELECT a.title FROM articles a WHERE a.slug = l.ref)
                             WHEN 'queue-item' THEN (SELECT q.text FROM queue_items q WHERE q.id::text = l.ref)
                             ELSE NULL END,
                 to_char(l.created_at AT TIME ZONE 'UTC', $ts),
                 CASE l.kind WHEN 'queue-item' THEN (SELECT q.list FROM queue_items q WHERE q.id::text = l.ref) END
          FROM goal_links l WHERE l.goal_slug = $goal
          ORDER BY CASE l.kind WHEN 'source' THEN 0 WHEN 'article' THEN 1 WHEN 'query' THEN 2 ELSE 3 END,
                   l.created_at, l.ref, l.kind""".query[LinkRow].run().toList.flatMap { r =>
      LinkKind.parse(r.kind).map(k => GoalLink(k, r.ref, r.note, r.title, r.createdAt, r.queue))
    }

  /** [[goalsOfItems]] on the caller's connection. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def goalsOfItemsIn(
      items: List[ItemId]
  )(using DbCon): List[(ItemId, domain.automation.Goal.Slug, BGoalRow.Title)] =
    if items.isEmpty then Nil
    else
      locally:
        val refs = items.map(_.value.toString)
        sql"""SELECT l.ref, g.slug, g.title FROM goal_links l JOIN goals g ON g.slug = l.goal_slug
            WHERE l.kind = ${LinkKind.QueueItem.wire} AND l.ref = ANY($refs::text[])
            ORDER BY g.title, g.slug"""
          .query[(String, domain.automation.Goal.Slug, BGoalRow.Title)]
          .run()
          .toList
          .flatMap((ref, slug, title) => ref.toIntOption.map(i => (ItemId(i), slug, title)))

  /** [[refs]] on the caller's connection. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def refsIn(goal: domain.automation.Goal.Slug, kind: LinkKind)(using DbCon): List[GoalLink.Ref] =
    sql"SELECT ref FROM goal_links WHERE goal_slug = $goal AND kind = ${kind.wire} ORDER BY created_at, ref"
      .query[GoalLink.Ref]
      .run()
      .toList

  /** Link (idempotent: an existing link keeps its note unless a new one is given). `false` = the goal does not exist.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def link(
      goal: domain.automation.Goal.Slug,
      kind: LinkKind,
      ref: GoalLink.Ref,
      note: Option[GoalLink.Note]
  )(using DbCon)(using creator: Creator.New): Boolean =
    val exists = sql"SELECT 1 FROM goals WHERE slug = $goal".query[Int].run().nonEmpty
    if exists then
      sql"""INSERT INTO goal_links (goal_slug, kind, ref, note, creator) VALUES ($goal, ${kind.wire}, $ref, $note, ${StoredCreator
          .column(creator)}::jsonb)
            ON CONFLICT (goal_slug, kind, ref) DO UPDATE SET note = coalesce(EXCLUDED.note, goal_links.note)""".update
        .run()
    exists

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def unlink(goal: domain.automation.Goal.Slug, kind: LinkKind, ref: GoalLink.Ref)(using
      DbCon
  ): Boolean =

    sql"DELETE FROM goal_links WHERE goal_slug = $goal AND kind = ${kind.wire} AND ref = $ref".update.run() > 0

  /** Every goal's links counted by kind: (goal, kind) → n. Article links count only articles that exist.
    */
  def linkCounts()(using
      DbCon
  ): Map[
    (domain.automation.Goal.Slug, domain.automation.Goal.LinkKind.Wire),
    RowCount
  ] =
    sql"""SELECT l.goal_slug, l.kind, count(*)::int FROM goal_links l
          WHERE l.kind <> 'article' OR EXISTS (SELECT 1 FROM articles a WHERE a.slug = l.ref)
          GROUP BY l.goal_slug, l.kind"""
      .query[
        (domain.automation.Goal.Slug, domain.automation.Goal.LinkKind.Wire, RowCount)
      ]
      .run()
      .map((g, k, n) => (g, k) -> n)
      .toMap

  /** The goals that record an approved plan. */
  def planned()(using DbCon): Set[domain.automation.Goal.Slug] =
    sql"SELECT slug FROM goals WHERE plan IS NOT NULL".query[domain.automation.Goal.Slug].run().toSet

  // ── plan and run cap ───────────────────────────────────────────────────────────────────────────

  /** The approved plan (its JSON) and the goal's run cap. `None` = no such goal. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def planAndBudget(
      goal: domain.automation.Goal.Slug
  )(using DbCon): Option[(Option[BGoalRow.Plan], Option[BigDecimal])] =
    sql"SELECT plan::text, budget_usd FROM goals WHERE slug = $goal"
      .query[(Option[BGoalRow.Plan], Option[BigDecimal])]
      .run()
      .headOption

  /** Record an approved plan, and when it was approved (V054: two collections' plans for one goal are merged by "the
    * one approved later wins").
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setPlan(goal: domain.automation.Goal.Slug, planJson: BGoalRow.Plan)(using DbCon): Unit =
    Unused(
      sql"""UPDATE goals SET plan = $planJson::jsonb, plan_approved_at = now(), updated_at = now()
                               WHERE slug = $goal""".update.run(),
      Unused.Why.Idempotent
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setBudget(goal: domain.automation.Goal.Slug, usd: Option[BigDecimal])(using DbCon): Unit =
    Unused(
      sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $goal".update.run(),
      Unused.Why.Idempotent
    )

  // ── schedules ──────────────────────────────────────────────────────────────────────────────────

  final private case class ScheduleRow(
      goal: Goal.Slug,
      kind: ScheduleKind.Wire,
      cron: GoalSchedule.Cron,
      enabled: GoalSchedule.Enabled,
      budgetUsd: Option[BigDecimal],
      nextRunAt: Option[GoalSchedule.NextRunAt],
      lastRunAt: Option[GoalSchedule.LastRunAt],
      lastJob: Option[GoalSchedule.LastRun],
      lastNote: Option[GoalSchedule.LastNote]
  ) derives DbCodec

  private val scheduleSelect = sql"""SELECT goal_slug, kind, cron, enabled, budget_usd,
              to_char(next_run_at AT TIME ZONE 'UTC', $ts), to_char(last_run_at AT TIME ZONE 'UTC', $ts),
              last_job, last_note
       FROM goal_schedules"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def toSchedule(r: ScheduleRow): Option[GoalSchedule] =
    ScheduleKind
      .parse(r.kind)
      .map(k =>
        GoalSchedule(
          r.goal,
          k,
          r.cron,
          r.enabled,
          r.budgetUsd.map(b => GoalSchedule.BudgetUsd(b.toDouble)),
          r.nextRunAt,
          r.lastRunAt,
          r.lastJob,
          r.lastNote
        )
      )

  /** Every schedule (or one goal's), the next due first. */
  def schedules(goal: Option[domain.automation.Goal.Slug] = None)(using DbCon): List[GoalSchedule] =
    val where = goal.fold(sql"")(g => sql" WHERE goal_slug = $g")
    sql"$scheduleSelect$where ORDER BY next_run_at NULLS LAST, goal_slug, kind"
      .query[ScheduleRow]
      .run()
      .toList
      .flatMap(toSchedule)

  /** One row here (a bundle's import, before the managers move it into its goal's watcher). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def schedule(goal: domain.automation.Goal.Slug, kind: ScheduleKind)(using
      DbCon
  ): Option[GoalSchedule] =
    sql"$scheduleSelect WHERE goal_slug = $goal AND kind = ${kind.wire}"
      .query[ScheduleRow]
      .run()
      .headOption
      .flatMap(toSchedule)

  /** A row here (tests, a bundle's import) before the managers move it into its goal's watcher. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setSchedule(
      goal: domain.automation.Goal.Slug,
      kind: ScheduleKind,
      cron: GoalSchedule.Cron,
      enabled: GoalSchedule.Enabled,
      budgetUsd: Option[BigDecimal],
      next: Option[java.time.Instant]
  )(using DbCon)(using creator: Creator.New): Unit =
    val at = next.map(_.toString)
    val _ =
      sql"""INSERT INTO goal_schedules (goal_slug, kind, cron, enabled, budget_usd, next_run_at, creator)
            VALUES ($goal, ${kind.wire}, $cron, $enabled, $budgetUsd, $at::timestamptz, ${StoredCreator.column(
          creator
        )}::jsonb)
            ON CONFLICT (goal_slug, kind) DO UPDATE SET cron = EXCLUDED.cron, enabled = EXCLUDED.enabled,
              budget_usd = EXCLUDED.budget_usd, next_run_at = EXCLUDED.next_run_at, updated_at = now()""".update.run()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def removeSchedule(goal: domain.automation.Goal.Slug, kind: ScheduleKind)(using DbCon): Boolean =
    sql"DELETE FROM goal_schedules WHERE goal_slug = $goal AND kind = ${kind.wire}".update.run() > 0

  /** The goals that still have rows here: schedules the managers have not moved into a watcher yet (since phase 3 a
    * goal's schedules ARE its `watch:<goal>` manager; a bundle's import still writes rows here, and the next managers'
    * tick moves them).
    */
  def scheduleGoals()(using DbCon): List[domain.automation.Goal.Slug] =
    sql"SELECT DISTINCT goal_slug FROM goal_schedules ORDER BY goal_slug"
      .query[domain.automation.Goal.Slug]
      .run()
      .toList

  /** Take a goal's schedules OUT of the table, in the caller's transaction (the migration into its watcher): two
    * servers migrating at once cannot both get them.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def takeSchedulesIn(goal: domain.automation.Goal.Slug)(using DbCon): List[GoalSchedule] =
    sql"""DELETE FROM goal_schedules WHERE goal_slug = $goal RETURNING goal_slug, kind, cron, enabled, budget_usd,
              to_char(next_run_at AT TIME ZONE 'UTC', $ts), to_char(last_run_at AT TIME ZONE 'UTC', $ts),
              last_job, last_note"""
      .query[ScheduleRow]
      .run()
      .toList
      .flatMap(toSchedule)
