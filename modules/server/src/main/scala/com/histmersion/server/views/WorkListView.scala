package com.histmersion.server.views

import io.scalaland.chimney.dsl.*

import com.histmersion.api.automation.Queue
import com.histmersion.api.views.QueueListing
import com.histmersion.api.automation.Queue.GoalRef
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Queue.Kind
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.worklists.QueueScope
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.library.acquisition.AcquireCheckService
import com.histmersion.api.Api
import com.histmersion.api.library.Acquire

/** A work list as `GET /api/queue/{list}` shows it: its items (open, all, a goal's or a job's) as the automation keeps
  * them, the goals each belongs to or is linked from, and — for the ACQUIRE list — the library's last legal check of
  * each item.
  */
final class WorkListView(
    workLists: WorkListControllers,
    queues: QueueService,
    goals: GoalService,
    acquireChecks: AcquireCheckService
):
  def list(
      list: String,
      all: Boolean,
      goal: Option[String] = None,
      job: Option[String] = None
  ): Either[Api.Error, QueueListing] =
    workLists.kindOf(list).map { k =>
      val scope = (goal.map(_.trim).filter(_.nonEmpty), job.map(_.trim).filter(_.nonEmpty)) match
        case (_, Some(j))    => QueueScope.ForJob(j)
        case (Some(g), None) => QueueScope.ForGoal(g, goals.refs(g, com.histmersion.domain.automation.Goal.LinkKind.QueueItem).toSet, withDone = all)
        case (None, None)    => if all then QueueScope.All else QueueScope.Open
      val items = queues.list(k, scope)
      val checks =
        if k != Kind.Acquire then Nil
        else
          acquireChecks
            .of(items.map(_.id))
            .values
            .toList
            .sortBy(_.item)
            .map(r => Acquire.Checks.AcquireCheck(r.item, r.at.toString, r.outcome, r.reason, r.checks))
      val linked = goals.goalsOfItems(items.map(_.id)).map((id, slug, title) => (id, GoalRef(slug, title)))
      val titles = items.flatMap(_.goal).distinct.flatMap(s => goals.get(s).map(g => s -> g.title)).toMap
      val own = items.flatMap(i => i.goal.map(s => (i.id, GoalRef(s, titles.getOrElse(s, s)))))
      QueueListing(k.label, items, checks, Queue.itemGoals(own, linked))
    }
