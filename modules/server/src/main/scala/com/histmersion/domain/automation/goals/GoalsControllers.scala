package com.histmersion
package domain.automation.goals

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.Api
import com.histmersion.api.automation.{Goal, GoalsExport, ItemStatus}
import com.histmersion.api.Write
import com.histmersion.api.aliases.{LabelRename, LabelRenames}
import com.histmersion.api.automation.Goal.Plans.{GoalLink, GoalView, LinkKind, Plan}
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
    current: com.histmersion.domain.automation.Goal.Slug => com.histmersion.domain.automation.Goal.Slug,
    pageSize: Option[PageSize] => PageSize,
    maintenance: WriteGate
):
  private def notFound(slug: com.histmersion.domain.automation.Goal.Slug) =
    Api.Error(Api.Error.Message(s"No goal '${slug.value}'"), Some(Api.Error.Code(Write.CodeNotFound)))

  def list(q: Api.GoalQuery): Goal.List = goals.page(
    q.status.map(s => com.histmersion.domain.automation.Goal.Status.Wire(s.value)),
    pageSize(q.limit.flatMap(l => PageSize.option(l.value))),
    q.offset.flatMap(o => Offset.option(o.value)).getOrElse(Offset(0))
  )

  def get(slug: com.histmersion.domain.automation.Goal.Slug): Either[Api.Error, Goal] =
    goals.get(current(slug)).toRight(notFound(slug))

  /** A put replaces the goal. */
  def put(slug: com.histmersion.domain.automation.Goal.Slug, in: Goal.Put): Either[Api.Error, Goal] =
    maintenance.guard(WriteGate.OperationName("put_goal")):
      val now = current(slug)
      goals.put(now, in).flatMap(_ => get(now))

  def patch(slug: com.histmersion.domain.automation.Goal.Slug, p: Goal.Patch): Either[Api.Error, Goal] =
    maintenance.guard(WriteGate.OperationName("patch_goal")):
      val now = current(slug)
      goals.patch(now, p).flatMap(_ => get(now))

  def delete(slug: com.histmersion.domain.automation.Goal.Slug): Either[Api.Error, Goal.Deleted] =
    maintenance.guard(WriteGate.OperationName("delete_goal")):
      val now = current(slug)
      if goals.delete(now) then Right(Goal.Deleted(Goal.Slug(now.value), Goal.Deleted.Value(true)))
      else Left(notFound(slug))

  /** `POST /api/goals/{slug}/rename` — a new slug; the old one keeps resolving (V053). */
  def rename(slug: com.histmersion.domain.automation.Goal.Slug, req: LabelRename): Either[Api.Error, LabelRenamed] =
    maintenance.guard(WriteGate.OperationName("rename_goal")):
      val from = current(slug)
      for
        valid <- LabelRenames.validate(
          Bundle.Kind.Goal,
          LabelRenames.Label(from.value),
          LabelRenames.Label(req.to.value)
        )
        to <- com.histmersion.domain.automation.Goal.Slug
          .either(valid.value)
          .left
          .map(_ =>
            Api.Error(
              Api.Error.Message(
                s"'${valid.value}' is not a goal slug (lower-case letters, digits, '_', '.' and '-', at most 121 characters)"
              ),
              Some(Api.Error.Code(Write.CodeInvalid))
            )
          )
        _ <- goals.rename(from, to)
      yield LabelRenamed(Bundle.Kind.Goal, LabelRenamed.From(from.value), LabelRenamed.To(to.value))

  /** `meta/GOALS.md`: every goal as its view (links, schedules, approved plan, run cap — the same parts
    * `GET /api/goals/{slug}/view` returns), plus the state of each linked work-list item so the file can show it done
    * or open. Read-only; the rendering is [[GoalsExport.render]].
    */
  def exportText(): String =
    val schedules = goals.schedules(None).groupBy(_.goal)
    val views = goals.all().map { g =>
      val slug = com.histmersion.domain.automation.Goal.Slug.assume(g.slug.value)
      val (plan, budget) = goals.planAndBudget(slug).getOrElse((None, None))
      GoalView(
        g,
        Nil,
        goals.links(slug),
        schedules.getOrElse(g.slug, Nil),
        plan.flatMap(j => io.circe.parser.decode[Plan](j.value).toOption),
        budget.map(b => GoalView.BudgetUsd(b.toDouble))
      )
    }
    val itemIds = views.flatMap(_.links).filter(_.kind == LinkKind.QueueItem).flatMap(_.ref.value.toIntOption).distinct
    val status = queues
      .byIds(itemIds.map(QueueItemId(_)))
      .map(i => GoalLink.QueueNumber(i.id.value) -> ItemStatus.Wire(i.status.value))
      .toMap
    GoalsExport.render(views, status)
