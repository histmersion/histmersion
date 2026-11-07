package com.histmersion.server

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.api.{Api, OpsApi, Write}
import com.histmersion.api.automation.Queue
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.server.views.WorkListView
import com.histmersion.util.data.checks.CheckRule

/** REST wiring for stream 2.8 — lint (the registered checks), the work lists (the automation's, a listing as its view
  * shows it) and the goals' export, each route delegating to the one controller MCP calls too. Appended to
  * [[HttpApi]]'s list; the `queue/items/{id}` routes come first so `items` can never be parsed as a list name.
  */
object OpsRoutes:

  private def refused(what: String, raw: String): Api.Error =
    Api.Error(Api.Error.Message(s"invalid $what '$raw'"), Some(Api.Error.Code(Write.CodeInvalid)))

  private def scoped[A](raw: Option[Queue.Wire], what: String)(either: String => Either[String, A]): Either[Api.Error, Option[A]] =
    raw.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(v) => either(v).left.map(_ => refused(what, v)).map(Some(_))

  def apply(w: ServerWiring): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.lint.serverLogic[Identity](rules => w.checks.lint(rules.map(r => CheckRule.Name(r.value)))),
    OpsApi.lintFix.serverLogicSuccess[Identity](rules => w.checks.lintFix(rules.map(r => CheckRule.Name(r.value)))),
    OpsApi.queuePatch.serverLogic[Identity]((id, p) => w.workLists.patch(QueueItemId(id.value), p)),
    OpsApi.queueDelete.serverLogic[Identity](id => w.workLists.delete(QueueItemId(id.value))),
    OpsApi.queueExport.serverLogic[Identity](list =>
      w.workLists.exportText(Queue.Kind.Label(list.value)).map(r => Queue.Export(r.value))
    ),
    OpsApi.queueImport.serverLogic[Identity]((list, req) => w.workLists.importText(Queue.Kind.Label(list.value), req)),
    OpsApi.queueList.serverLogic[Identity]((list, all, goal, job) =>
      for
        g <- scoped(goal, "goal slug")(com.histmersion.domain.automation.Goal.Slug.either(_))
        j <- scoped(job, "job id")(com.histmersion.util.jobs.Job.Id.either(_))
        r <- w.workListView.list(WorkListView.QueueName(list.value), WorkListView.IncludeAll(all), g, j)
      yield r
    ),
    OpsApi.queueAdd.serverLogic[Identity]((list, in) => w.workLists.add(Queue.Kind.Label(list.value), in)),
    OpsApi.goalsExport.serverLogicSuccess[Identity](_ => com.histmersion.api.automation.Goal.Export(w.goals.exportText()))
  )
