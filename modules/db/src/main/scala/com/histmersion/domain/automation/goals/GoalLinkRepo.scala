package com.histmersion.domain.automation.goals
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given

/** What hangs off a goal (migrations V046, V049): its links (sources, articles, search queries, queue items), its
  * approved plan and run cap, and its schedules. Stateless, like every repo.
  */
object GoalLinkRepo:

  type LinksGoal = LinksGoal.T
  object LinksGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsInResultPart1 = GoalsOfItemsInResultPart1.T
  object GoalsOfItemsInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsInResultPart2 = GoalsOfItemsInResultPart2.T
  object GoalsOfItemsInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsInResultPart3 = GoalsOfItemsInResultPart3.T
  object GoalsOfItemsInResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsInItems = GoalsOfItemsInItems.T
  object GoalsOfItemsInItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsInResult = RefsInResult.T
  object RefsInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsInGoal = RefsInGoal.T
  object RefsInGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoal = LinkGoal.T
  object LinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRef = LinkRef.T
  object LinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkNote = LinkNote.T
  object LinkNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkResult = UnlinkResult.T
  object UnlinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkGoal = UnlinkGoal.T
  object UnlinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkRef = UnlinkRef.T
  object UnlinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCountsResultPart1Part1 = LinkCountsResultPart1Part1.T
  object LinkCountsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCountsResultPart1Part2 = LinkCountsResultPart1Part2.T
  object LinkCountsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCountsResultPart2 = LinkCountsResultPart2.T
  object LinkCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlannedResult = PlannedResult.T
  object PlannedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanAndBudgetResultPart1 = PlanAndBudgetResultPart1.T
  object PlanAndBudgetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanAndBudgetGoal = PlanAndBudgetGoal.T
  object PlanAndBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPlanGoal = SetPlanGoal.T
  object SetPlanGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPlanPlanJson = SetPlanPlanJson.T
  object SetPlanPlanJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetBudgetGoal = SetBudgetGoal.T
  object SetBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulesGoal = SchedulesGoal.T
  object SchedulesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleGoal = ScheduleGoal.T
  object ScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleGoal = SetScheduleGoal.T
  object SetScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleCron = SetScheduleCron.T
  object SetScheduleCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleEnabled = SetScheduleEnabled.T
  object SetScheduleEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveScheduleResult = RemoveScheduleResult.T
  object RemoveScheduleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveScheduleGoal = RemoveScheduleGoal.T
  object RemoveScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleGoalsResult = ScheduleGoalsResult.T
  object ScheduleGoalsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TakeSchedulesInGoal = TakeSchedulesInGoal.T
  object TakeSchedulesInGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  final private case class LinkRow(
      kind: LinkRow.Kind,
      ref: LinkRow.Ref,
      note: Option[LinkRow.Note],
      title: Option[LinkRow.Title],
      createdAt: LinkRow.CreatedAt,
      queue: Option[LinkRow.Queue]
  ) derives DbCodec

  object LinkRow:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ref = Ref.T
    object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queue = Queue.T
    object Queue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A goal's links, each with the title of what it points at (`None` = it no longer exists; a query has no title).
    * Sources, then articles, queries, queue items; oldest first within a kind.
    */
  def links(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.LinksGoal)(using DbCon): List[GoalLink] =
    sql"""SELECT l.kind, l.ref, l.note,
                 CASE l.kind WHEN 'source'  THEN (SELECT s.title FROM sources s WHERE s.citekey = l.ref)
                             WHEN 'article' THEN (SELECT a.title FROM articles a WHERE a.slug = l.ref)
                             WHEN 'queue-item' THEN (SELECT q.text FROM queue_items q WHERE q.id::text = l.ref)
                             ELSE NULL END,
                 to_char(l.created_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)}),
                 CASE l.kind WHEN 'queue-item' THEN (SELECT q.list FROM queue_items q WHERE q.id::text = l.ref) END
          FROM goal_links l WHERE l.goal_slug = $goal
          ORDER BY CASE l.kind WHEN 'source' THEN 0 WHEN 'article' THEN 1 WHEN 'query' THEN 2 ELSE 3 END,
                   l.created_at, l.ref""".query[LinkRow].run().toList.flatMap { r =>
      LinkKind.parse(r.kind).map(k => GoalLink(k, r.ref, r.note, r.title, r.createdAt, r.queue))
    }

  /** [[goalsOfItems]] on the caller's connection. */
  def goalsOfItemsIn(items: List[com.histmersion.domain.automation.goals.GoalLinkRepo.GoalsOfItemsInItems])(using DbCon): List[(com.histmersion.domain.automation.goals.GoalLinkRepo.GoalsOfItemsInResultPart1, com.histmersion.domain.automation.goals.GoalLinkRepo.GoalsOfItemsInResultPart2, com.histmersion.domain.automation.goals.GoalLinkRepo.GoalsOfItemsInResultPart3)] =
    if items.isEmpty then Nil
    else
      locally:
        val refs = items.map(_.toString)
        sql"""SELECT l.ref, g.slug, g.title FROM goal_links l JOIN goals g ON g.slug = l.goal_slug
            WHERE l.kind = ${LinkKind.QueueItem.wire} AND l.ref = ANY($refs::text[])
            ORDER BY g.title, g.slug"""
          .query[(String, String, String)]
          .run()
          .toList
          .flatMap((ref, slug, title) => ref.toIntOption.map((_, slug, title)))

  /** [[refs]] on the caller's connection. */
  def refsIn(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.RefsInGoal, kind: LinkKind)(using DbCon): List[com.histmersion.domain.automation.goals.GoalLinkRepo.RefsInResult] =
    sql"SELECT ref FROM goal_links WHERE goal_slug = $goal AND kind = ${kind.wire} ORDER BY created_at, ref"
      .query[String]
      .run()
      .toList

  /** Link (idempotent: an existing link keeps its note unless a new one is given). `false` = the goal does not exist.
    */
  def link(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.LinkGoal, kind: LinkKind, ref: com.histmersion.domain.automation.goals.GoalLinkRepo.LinkRef, note: Option[com.histmersion.domain.automation.goals.GoalLinkRepo.LinkNote])(using DbCon): com.histmersion.domain.automation.goals.GoalLinkRepo.LinkResult =
    val exists = sql"SELECT 1 FROM goals WHERE slug = $goal".query[Int].run().nonEmpty
    if exists then
      sql"""INSERT INTO goal_links (goal_slug, kind, ref, note) VALUES ($goal, ${kind.wire}, $ref, $note)
            ON CONFLICT (goal_slug, kind, ref) DO UPDATE SET note = coalesce(EXCLUDED.note, goal_links.note)""".update
        .run()
    exists

  def unlink(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.UnlinkGoal, kind: LinkKind, ref: com.histmersion.domain.automation.goals.GoalLinkRepo.UnlinkRef)(using DbCon): com.histmersion.domain.automation.goals.GoalLinkRepo.UnlinkResult =

    sql"DELETE FROM goal_links WHERE goal_slug = $goal AND kind = ${kind.wire} AND ref = $ref".update.run() > 0

  /** Every goal's links counted by kind: (goal, kind) → n. Article links count only articles that exist.
    */
  def linkCounts()(using DbCon): Map[(com.histmersion.domain.automation.goals.GoalLinkRepo.LinkCountsResultPart1Part1, com.histmersion.domain.automation.goals.GoalLinkRepo.LinkCountsResultPart1Part2), com.histmersion.domain.automation.goals.GoalLinkRepo.LinkCountsResultPart2] =
    sql"""SELECT l.goal_slug, l.kind, count(*)::int FROM goal_links l
          WHERE l.kind <> 'article' OR EXISTS (SELECT 1 FROM articles a WHERE a.slug = l.ref)
          GROUP BY l.goal_slug, l.kind""".query[(String, String, Int)].run().map((g, k, n) => (g, k) -> n).toMap

  /** The goals that record an approved plan. */
  def planned()(using DbCon): Set[com.histmersion.domain.automation.goals.GoalLinkRepo.PlannedResult] =
    sql"SELECT slug FROM goals WHERE plan IS NOT NULL".query[String].run().toSet

  // ── plan and run cap ───────────────────────────────────────────────────────────────────────────

  /** The approved plan (its JSON) and the goal's run cap. `None` = no such goal. */
  def planAndBudget(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.PlanAndBudgetGoal)(using DbCon): Option[(Option[com.histmersion.domain.automation.goals.GoalLinkRepo.PlanAndBudgetResultPart1], Option[BigDecimal])] =
    sql"SELECT plan::text, budget_usd FROM goals WHERE slug = $goal"
      .query[(Option[String], Option[BigDecimal])]
      .run()
      .headOption

  /** Record an approved plan, and when it was approved (V054: two collections' plans for one goal are merged by "the
    * one approved later wins").
    */
  def setPlan(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.SetPlanGoal, planJson: com.histmersion.domain.automation.goals.GoalLinkRepo.SetPlanPlanJson)(using DbCon): Unit =
    val _ = sql"""UPDATE goals SET plan = $planJson::jsonb, plan_approved_at = now(), updated_at = now()
                               WHERE slug = $goal""".update.run()

  def setBudget(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.SetBudgetGoal, usd: Option[BigDecimal])(using DbCon): Unit =
    val _ = sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $goal".update.run()

  // ── schedules ──────────────────────────────────────────────────────────────────────────────────

  final private case class ScheduleRow(
      goal: ScheduleRow.Goal,
      kind: ScheduleRow.Kind,
      cron: ScheduleRow.Cron,
      enabled: ScheduleRow.Enabled,
      budgetUsd: Option[BigDecimal],
      nextRunAt: Option[ScheduleRow.NextRunAt],
      lastRunAt: Option[ScheduleRow.LastRunAt],
      lastJob: Option[ScheduleRow.LastJob],
      lastNote: Option[ScheduleRow.LastNote]
  ) derives DbCodec

  object ScheduleRow:
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NextRunAt = NextRunAt.T
    object NextRunAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastRunAt = LastRunAt.T
    object LastRunAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastJob = LastJob.T
    object LastJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastNote = LastNote.T
    object LastNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val scheduleSelect = Sql.raw(s"""SELECT goal_slug, kind, cron, enabled, budget_usd,
              to_char(next_run_at AT TIME ZONE 'UTC', $ts), to_char(last_run_at AT TIME ZONE 'UTC', $ts),
              last_job, last_note
       FROM goal_schedules""")

  private def toSchedule(r: ScheduleRow): Option[GoalSchedule] =
    ScheduleKind
      .parse(r.kind)
      .map(k =>
        GoalSchedule(
          r.goal,
          k,
          r.cron,
          r.enabled,
          r.budgetUsd.map(_.toDouble),
          r.nextRunAt,
          r.lastRunAt,
          r.lastJob,
          r.lastNote
        )
      )

  /** Every schedule (or one goal's), the next due first. */
  def schedules(goal: Option[com.histmersion.domain.automation.goals.GoalLinkRepo.SchedulesGoal] = None)(using DbCon): List[GoalSchedule] =
    val where = goal.fold(Sql.empty)(g => Sql.of(" WHERE goal_slug = ?", Param.S(g)))
    (scheduleSelect ++ where ++ " ORDER BY next_run_at NULLS LAST, goal_slug, kind").frag
      .query[ScheduleRow]
      .run()
      .toList
      .flatMap(toSchedule)

  /** One row here (a bundle's import, before the managers move it into its goal's watcher). */
  def schedule(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.ScheduleGoal, kind: ScheduleKind)(using DbCon): Option[GoalSchedule] =
    (scheduleSelect ++ Sql.of(" WHERE goal_slug = ? AND kind = ?", Param.S(goal), Param.S(kind.wire))).frag
      .query[ScheduleRow]
      .run()
      .headOption
      .flatMap(toSchedule)

  /** A row here (tests, a bundle's import) before the managers move it into its goal's watcher. */
  def setSchedule(
      goal: com.histmersion.domain.automation.goals.GoalLinkRepo.SetScheduleGoal,
      kind: ScheduleKind,
      cron: com.histmersion.domain.automation.goals.GoalLinkRepo.SetScheduleCron,
      enabled: com.histmersion.domain.automation.goals.GoalLinkRepo.SetScheduleEnabled,
      budgetUsd: Option[BigDecimal],
      next: Option[java.time.Instant]
  )(using DbCon): Unit =
    val at = next.map(_.toString)
    val _ =
      sql"""INSERT INTO goal_schedules (goal_slug, kind, cron, enabled, budget_usd, next_run_at)
            VALUES ($goal, ${kind.wire}, $cron, $enabled, $budgetUsd, $at::timestamptz)
            ON CONFLICT (goal_slug, kind) DO UPDATE SET cron = EXCLUDED.cron, enabled = EXCLUDED.enabled,
              budget_usd = EXCLUDED.budget_usd, next_run_at = EXCLUDED.next_run_at, updated_at = now()""".update.run()

  def removeSchedule(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.RemoveScheduleGoal, kind: ScheduleKind)(using DbCon): com.histmersion.domain.automation.goals.GoalLinkRepo.RemoveScheduleResult =
    sql"DELETE FROM goal_schedules WHERE goal_slug = $goal AND kind = ${kind.wire}".update.run() > 0

  /** The goals that still have rows here: schedules the managers have not moved into a watcher yet (since phase 3 a
    * goal's schedules ARE its `watch:<goal>` manager; a bundle's import still writes rows here, and the next managers'
    * tick moves them).
    */
  def scheduleGoals()(using DbCon): List[com.histmersion.domain.automation.goals.GoalLinkRepo.ScheduleGoalsResult] =
    sql"SELECT DISTINCT goal_slug FROM goal_schedules ORDER BY goal_slug".query[String].run().toList

  /** Take a goal's schedules OUT of the table, in the caller's transaction (the migration into its watcher): two
    * servers migrating at once cannot both get them.
    */
  def takeSchedulesIn(goal: com.histmersion.domain.automation.goals.GoalLinkRepo.TakeSchedulesInGoal)(using DbCon): List[GoalSchedule] =
    Sql
      .of(
        s"""DELETE FROM goal_schedules WHERE goal_slug = ? RETURNING goal_slug, kind, cron, enabled, budget_usd,
              to_char(next_run_at AT TIME ZONE 'UTC', $ts), to_char(last_run_at AT TIME ZONE 'UTC', $ts),
              last_job, last_note""",
        Param.S(goal)
      )
      .frag
      .query[ScheduleRow]
      .run()
      .toList
      .flatMap(toSchedule)
