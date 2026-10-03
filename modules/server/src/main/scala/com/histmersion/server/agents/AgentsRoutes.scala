package com.histmersion.server.agents

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.core.AgentsApi

/** REST wiring of the embedded agents — every route delegates to [[AgentsHandlers]]. */
object AgentsRoutes:

  def apply(a: AgentsHandlers): List[ServerEndpoint[Any, Identity]] = List(
    AgentsApi.models.serverLogicSuccess[Identity](_ => a.models),
    AgentsApi.probe.serverLogic[Identity](req => a.probe(req)),
    AgentsApi.startRun.serverLogic[Identity](req => a.startRun(req)),
    AgentsApi.runs.serverLogic[Identity]((status, limit) => a.runs(status, limit)),
    AgentsApi.run.serverLogic[Identity](id => a.run(id)),
    AgentsApi.control.serverLogic[Identity]((id, action, usd, tokens) => a.control(id, action, usd, tokens)),
    AgentsApi.approvals.serverLogic[Identity]((state, limit) => a.approvalsList(state, limit)),
    AgentsApi.decide.serverLogic[Identity]((id, decision, req) => a.decide(id, decision, req))
  )
