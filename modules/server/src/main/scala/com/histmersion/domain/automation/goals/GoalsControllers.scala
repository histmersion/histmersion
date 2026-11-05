package com.histmersion.domain.automation.goals

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.Api
import com.histmersion.api.automation.{Goal, GoalsExport}
import com.histmersion.api.Write
import com.histmersion.api.aliases.{LabelRename, LabelRenames}
import com.histmersion.api.automation.Goal.Plans.{GoalView, LinkKind, Plan}
import com.histmersion.domain.automation.worklists.{QueueItemId, QueueService}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.LabelRenamed

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
  private def notFound(slug: com.histmersion.domain.automation.goals.GoalsControllers.NotFoundSlug) = Api.Error(s"No goal '$slug'", Some(Write.CodeNotFound))

  def list(q: Api.GoalQuery): Goal.List = goals.page(q.status, pageSize(q.limit), q.offset.getOrElse(0))

  def get(slug: com.histmersion.domain.automation.goals.GoalsControllers.GetSlug): Either[Api.Error, Goal] = goals.get(current(slug)).toRight(notFound(slug))

  /** A put replaces the goal. */
  def put(slug: com.histmersion.domain.automation.goals.GoalsControllers.PutSlug, in: Goal.Put): Either[Api.Error, Goal] =
    maintenance.guard("put_goal"):
      val now = current(slug)
      goals.put(now, in).flatMap(_ => get(now))

  def patch(slug: com.histmersion.domain.automation.goals.GoalsControllers.PatchSlug, p: Goal.Patch): Either[Api.Error, Goal] =
    maintenance.guard("patch_goal"):
      val now = current(slug)
      goals.patch(now, p).flatMap(_ => get(now))

  def delete(slug: com.histmersion.domain.automation.goals.GoalsControllers.DeleteSlug): Either[Api.Error, Goal.Deleted] =
    maintenance.guard("delete_goal"):
      val now = current(slug)
      if goals.delete(now) then Right(Goal.Deleted(now, true)) else Left(notFound(slug))

  /** `POST /api/goals/{slug}/rename` — a new slug; the old one keeps resolving (V053). */
  def rename(slug: com.histmersion.domain.automation.goals.GoalsControllers.RenameSlug, req: LabelRename): Either[Api.Error, LabelRenamed] =
    maintenance.guard("rename_goal"):
      val from = current(slug)
      for
        to <- LabelRenames.validate(Bundle.Kind.Goal, from, req.to)
        _ <- goals.rename(from, to)
      yield LabelRenamed(Bundle.Kind.Goal, from, to)

  /** `meta/GOALS.md`: every goal as its view (links, schedules, approved plan, run cap — the same parts
    * `GET /api/goals/{slug}/view` returns), plus the state of each linked work-list item so the file can show it done
    * or open. Read-only; the rendering is [[GoalsExport.render]].
    */
  def exportText(): com.histmersion.domain.automation.goals.GoalsControllers.ExportTextResult =
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

object GoalsControllers:
  type NotFoundSlug = NotFoundSlug.T
  object NotFoundSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSlug = GetSlug.T
  object GetSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSlug = PutSlug.T
  object PutSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSlug = PatchSlug.T
  object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSlug = DeleteSlug.T
  object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameSlug = RenameSlug.T
  object RenameSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportTextResult = ExportTextResult.T
  object ExportTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
