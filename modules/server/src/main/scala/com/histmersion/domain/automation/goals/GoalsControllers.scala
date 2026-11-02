package com.histmersion.domain.automation.goals

import com.histmersion.api.{Api, ApiError}
import com.histmersion.api.automation.{Goal, GoalDeleted, GoalList, GoalPatch, GoalPut, GoalsExport}
import com.histmersion.api.Write
import com.histmersion.api.aliases.{LabelRename, LabelRenames}
import com.histmersion.api.automation.GoalPlans.{GoalView, LinkKind, Plan}
import com.histmersion.domain.automation.worklists.{QueueItemId, QueueService}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.values.BundleKind

/** The goals' operations as REST, MCP and the CLI reach them: list, read, put, patch, delete and rename a goal, and the
  * `meta/GOALS.md` export. An old slug (V053) answers as the goal it was renamed to, and a write to one goes there
  * (#1799); every write holds the maintenance guard.
  */
final class GoalsControllers(
    goals: GoalService,
    queues: QueueService,
    current: String => String,
    pageSize: Option[Int] => Int,
    maintenance: WriteGate
):
  private def notFound(slug: String) = ApiError(s"No goal '$slug'", Some(Write.CodeNotFound))

  def list(q: Api.GoalQuery): GoalList = goals.page(q.status, pageSize(q.limit), q.offset.getOrElse(0))

  def get(slug: String): Either[ApiError, Goal] = goals.get(current(slug)).toRight(notFound(slug))

  /** A put replaces the goal. */
  def put(slug: String, in: GoalPut): Either[ApiError, Goal] =
    maintenance.guard("put_goal"):
      val now = current(slug)
      goals.put(now, in).flatMap(_ => get(now))

  def patch(slug: String, p: GoalPatch): Either[ApiError, Goal] =
    maintenance.guard("patch_goal"):
      val now = current(slug)
      goals.patch(now, p).flatMap(_ => get(now))

  def delete(slug: String): Either[ApiError, GoalDeleted] =
    maintenance.guard("delete_goal"):
      val now = current(slug)
      if goals.delete(now) then Right(GoalDeleted(now, true)) else Left(notFound(slug))

  /** `POST /api/goals/{slug}/rename` — a new slug; the old one keeps resolving (V053). */
  def rename(slug: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    maintenance.guard("rename_goal"):
      val from = current(slug)
      for
        to <- LabelRenames.validate(BundleKind.Goal, from, req.to)
        _ <- goals.rename(from, to)
      yield LabelRenamed(BundleKind.Goal, from, to)

  /** `meta/GOALS.md`: every goal as its view (links, schedules, approved plan, run cap — the same parts
    * `GET /api/goals/{slug}/view` returns), plus the state of each linked work-list item so the file can show it done
    * or open. Read-only; the rendering is [[GoalsExport.render]].
    */
  def exportText(): String =
    val schedules = goals.schedules(None).groupBy(_.goal)
    val views = goals.all().map { g =>
      val (plan, budget) = goals.planAndBudget(g.slug).getOrElse((None, None))
      GoalView(
        g,
        Nil,
        goals.links(g.slug),
        schedules.getOrElse(g.slug, Nil),
        plan.flatMap(j => io.circe.parser.decode[Plan](j).toOption),
        budget.map(_.toDouble)
      )
    }
    val itemIds = views.flatMap(_.links).filter(_.kind == LinkKind.QueueItem).flatMap(_.ref.toIntOption).distinct
    val status = queues.byIds(itemIds.map(QueueItemId(_))).map(i => i.id -> i.status).toMap
    GoalsExport.render(views, status)
