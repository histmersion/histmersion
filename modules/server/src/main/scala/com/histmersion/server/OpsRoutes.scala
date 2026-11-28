package com.histmersion
package server

import com.histmersion.api.automation.queue.Kind
import com.histmersion.util.problem.{Legacy, Problem}

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.api.{Api, OpsApi, Write}
import com.histmersion.api.automation.Queue
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.server.views.WorkListView
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.domain.automation.Goal
import com.histmersion.util.jobs.Job
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** REST wiring for stream 2.8 — lint (the registered checks), the work lists (the automation's, a listing as its view
  * shows it) and the goals' export, each route delegating to the one controller MCP calls too. Appended to
  * [[HttpApi]]'s list; the `queue/items/{id}` routes come first so `items` can never be parsed as a list name.
  */
@movesTo(FeaturePlace.Server)
object OpsRoutes:

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def refused(what: String, raw: String): Api.Error =
    Api.Error(Problem.Unclassified(Legacy.Text(s"invalid $what '$raw'")), Some(Api.Error.Code(Write.CodeInvalid)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def scoped[A](raw: Option[Queue.Wire], what: String)(
      either: String => Either[String, A]
  ): Either[Api.Error, Option[A]] =
    raw.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(v) => either(v).left.map(_ => refused(what, v)).map(Some(_))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def apply(w: ServerWiring): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.lint.serverLogic[Identity](rules =>
      w.checks.lint(rules.map(r => CheckRule.Name(r.value))).left.map(Api.Error(_))
    ),
    OpsApi.lintFix.serverLogicSuccess[Identity](rules => w.checks.heldLintFix(rules.map(r => CheckRule.Name(r.value)))),
    OpsApi.queuePatch.serverLogic[Identity]((id, p) => w.workLists.patch(QueueItemId(id.value), p)),
    OpsApi.queueDelete.serverLogic[Identity](id => w.workLists.delete(QueueItemId(id.value))),
    OpsApi.queueExport.serverLogic[Identity](list => w.workLists.exportText(list).map(r => Queue.Export(r.value))),
    OpsApi.queueImport.serverLogic[Identity]((list, req) => w.workLists.importText(list, req)),
    OpsApi.queueList.serverLogic[Identity]((list, all, goal, job) =>
      for
        g <- scoped(goal, "goal slug")(Goal.Slug.either(_))
        j <- scoped(job, "job id")(Job.Id.either(_))
        r <- w.workListView.list(WorkListControllers.KindOfList(list.value), WorkListView.IncludeAll(all), g, j)
      yield r
    ),
    OpsApi.queueAdd.serverLogic[Identity]((list, in) =>
      w.workLists.addOverRest(WorkListControllers.ListName(list.value), in)
    ),
    OpsApi.goalsExport.serverLogicSuccess[Identity](_ =>
      com.histmersion.api.automation.Goal.Export(w.goals.exportText())
    )
  )
