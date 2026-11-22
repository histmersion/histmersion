package com.histmersion
package server.views

import io.scalaland.chimney.dsl.*
import WorkListView.given
import io.scalaland.chimney.Transformer
import com.histmersion.domain.AcquireRecheck

import com.histmersion.api.automation.Queue
import com.histmersion.api.views.QueueListing
import com.histmersion.api.automation.Queue.GoalRef
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Queue.Kind
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.worklists.QueueScope
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.library.acquisition.{AcquireAttempt, AcquireAttemptRow, AcquireCheckService}
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal as ApiGoal
import com.histmersion.domain.automation.goals.GoalLinkRepo
import com.histmersion.api.library.Acquire
import com.histmersion.domain.automation.Goal
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.constraint.any.Pure

/** A work list as `GET /api/queue/{list}` shows it: its items (open, all, a goal's or a job's) as the automation keeps
  * them, the goals each belongs to or is linked from, and — for the ACQUIRE list — the library's last legal check of
  * each item.
  */
final class WorkListView(
    workLists: WorkListControllers,
    queues: QueueService,
    goals: GoalService,
    acquireAttempts: AcquireCheckService
):
  def list(
      list: WorkListControllers.KindOfList,
      all: WorkListView.IncludeAll,
      goal: Option[Goal.Slug] = None,
      job: Option[Job.Id] = None
  ): Either[Api.Error, QueueListing] =
    workLists.kindOf(list).map { k =>
      val scope = (goal, job) match
        case (_, Some(j))    => QueueScope.ForJob(QueueScope.ForJob.Job(j.value))
        case (Some(g), None) =>
          QueueScope.ForGoal(
            QueueScope.ForGoal.Goal(g.value),
            goals
              .refs(g, Goal.LinkKind.QueueItem)
              .map(r => QueueScope.ForGoal.Linked(r.value))
              .toSet,
            withDone = QueueScope.ForGoal.WithDone(all.value)
          )
        case (None, None) => if all.value then QueueScope.All else QueueScope.Open
      val items = queues.list(k, scope)
      val checks =
        if k != Kind.Acquire then Nil
        else
          val attempts = acquireAttempts.of(items.map(_.id.transformInto[AcquireAttemptRow.Id]))
          items.flatMap(i =>
            attempts.get(i.id.transformInto[AcquireAttemptRow.Id]).flatMap(as => as.headOption.map(as -> _)).map {
              (as, last) =>
                Acquire.Checks.AcquireCheck(
                  i.id,
                  Acquire.Checks.AcquireCheck.CheckedAt(last.at.toString),
                  AcquireRecheck.Outcome
                    .of(last.outcome)
                    .fold(AcquireAttemptRow.LegacyWire)(_.wire)
                    .transformInto[Acquire.Checks.AcquireCheck.Outcome],
                  AcquireRecheck.Rationale.Said
                    .of(AcquireRecheck.Rationale.of(last))
                    .transformInto[Acquire.Checks.AcquireCheck.Said],
                  AcquireAttempt.Tries.of(as).transformInto[Acquire.Checks.AcquireCheck.Tries]
                )
            }
          )
      val linked = goals
        .goalsOfItems(items.map(i => GoalLinkRepo.ItemId(i.id.value)))
        .map((id, slug, title) =>
          (
            Queue.QueueItem.Id(id.value),
            GoalRef(ApiGoal.Slug(slug.value), ApiGoal.Title(title.value))
          )
        )
      val titles = items
        .flatMap(_.goal)
        .distinct
        .flatMap(s => Goal.Slug.option(s.value).flatMap(goals.get).map(g => s -> ApiGoal.Title(g.title.value)))
        .toMap
      val own = items.flatMap(i => i.goal.map(s => (i.id, GoalRef(s, titles.getOrElse(s, ApiGoal.Title(s.value))))))
      QueueListing(k.label, items, checks, Queue.itemGoals(own, linked))
    }

object WorkListView:

  /** A check's outcome as the listing carries it: the stored wire spelling, unchanged. The names differ (the core
    * enum's `Outcome.Wire`, the record's `Outcome`), so chimney-iron's same-name pairing does not apply.
    */
  given Transformer[AcquireRecheck.Outcome.Wire, Acquire.Checks.AcquireCheck.Outcome] = wire =>
    Acquire.Checks.AcquireCheck.Outcome(wire.value)
  type IncludeAll = IncludeAll.T
  object IncludeAll extends Newtype[Boolean, Pure]
