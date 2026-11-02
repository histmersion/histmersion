package com.histmersion.server

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.api.OpsApi

/** REST wiring for stream 2.8 — lint (the registered checks), the work lists (the automation's, a listing as its view
  * shows it) and the goals' export, each route delegating to the one controller MCP calls too. Appended to
  * [[HttpApi]]'s list; the `queue/items/{id}` routes come first so `items` can never be parsed as a list name.
  */
object OpsRoutes:

  def apply(w: ServerWiring): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.lint.serverLogic[Identity](rules => w.checks.lint(rules)),
    OpsApi.lintFix.serverLogicSuccess[Identity](rules => w.checks.lintFix(rules)),
    OpsApi.queuePatch.serverLogic[Identity]((id, p) => w.workLists.patch(id, p)),
    OpsApi.queueDelete.serverLogic[Identity](id => w.workLists.delete(id)),
    OpsApi.queueExport.serverLogic[Identity](list => w.workLists.exportText(list)),
    OpsApi.queueImport.serverLogic[Identity]((list, req) => w.workLists.importText(list, req)),
    OpsApi.queueList.serverLogic[Identity]((list, all, goal, job) => w.workListView.list(list, all, goal, job)),
    OpsApi.queueAdd.serverLogic[Identity]((list, in) => w.workLists.add(list, in)),
    OpsApi.goalsExport.serverLogicSuccess[Identity](_ => w.goals.exportText())
  )
