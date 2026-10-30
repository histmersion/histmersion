package com.histmersion.server

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.api.OpsApi

/** REST wiring for stream 2.8 — every route delegates to the one [[OpsHandlers]] implementation that
  * MCP calls too. Appended to [[HttpApi]]'s list; the `queue/items/{id}` routes come first so `items`
  * can never be parsed as a list name. */
object OpsRoutes:

  def apply(ops: OpsHandlers): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.lint.serverLogic[Identity](rules => ops.lint(rules)),
    OpsApi.lintFix.serverLogicSuccess[Identity](rules => ops.lintFix(rules)),
    OpsApi.queuePatch.serverLogic[Identity]((id, p) => ops.queuePatch(id, p)),
    OpsApi.queueDelete.serverLogic[Identity](id => ops.queueDelete(id)),
    OpsApi.queueExport.serverLogic[Identity](list => ops.queueExport(list)),
    OpsApi.queueImport.serverLogic[Identity]((list, req) => ops.queueImport(list, req)),
    OpsApi.queueList.serverLogic[Identity]((list, all, goal) => ops.queueList(list, all, goal)),
    OpsApi.queueAdd.serverLogic[Identity]((list, in) => ops.queueAdd(list, in)),
    OpsApi.goalsExport.serverLogicSuccess[Identity](_ => ops.goalsExport()),
    OpsApi.query.serverLogic[Identity](req => ops.query(req)),
    OpsApi.schemaTables.serverLogicSuccess[Identity](_ => ops.schemaTables)
  )
