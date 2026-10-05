package com.histmersion.server.agents

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.core.AgentsApi

/** REST wiring of the embedded agents — every route delegates to [[AgentsHandlers]]. */
object AgentsRoutes:

  def apply(a: AgentsHandlers): List[ServerEndpoint[Any, Identity]] = List(
    AgentsApi.models.serverLogicSuccess[Identity](_ => a.models),
    AgentsApi.setChain.serverLogic[Identity]((role, in) => a.setChain(role, in)),
    AgentsApi.resetChain.serverLogic[Identity](role => a.resetChain(role)),
    AgentsApi.setAllowance.serverLogic[Identity](in => a.setAllowance(in)),
    AgentsApi.removeAllowance.serverLogic[Identity](target => a.removeAllowance(target)),
    AgentsApi.doctor.serverLogicSuccess[Identity](offline => a.doctor(offline.contains(true))),
    AgentsApi.probe.serverLogic[Identity](req => a.probe(req)),
    AgentsApi.startRun.serverLogic[Identity](req => a.startRun(req)),
    AgentsApi.runs.serverLogic[Identity]((status, limit, goal) => a.runs(status, limit, goal)),
    AgentsApi.run.serverLogic[Identity](id => a.run(id)),
    AgentsApi.control.serverLogic[Identity]((id, action, usd, tokens) => a.control(id, action, usd, tokens)),
    AgentsApi.approvals.serverLogic[Identity]((state, limit) => a.approvalsList(state, limit)),
    AgentsApi.decide.serverLogic[Identity]((id, decision, req) => a.decide(id, decision, req)),
    AgentsApi.goalView.serverLogic[Identity](slug => a.goalView(slug)),
    AgentsApi.addGoalLink.serverLogic[Identity]((slug, in) => a.addGoalLink(slug, in)),
    AgentsApi.removeGoalLink.serverLogic[Identity]((slug, kind, ref) => a.removeGoalLink(slug, kind, ref)),
    AgentsApi.setSchedule.serverLogic[Identity]((slug, kind, in) => a.setSchedule(slug, kind, in)),
    AgentsApi.removeSchedule.serverLogic[Identity]((slug, kind) => a.removeSchedule(slug, kind)),
    AgentsApi.schedules.serverLogicSuccess[Identity](_ => a.schedules)
  )
