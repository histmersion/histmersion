package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.PageSize
import com.histmersion.api.Paging
import com.histmersion.api.automation.{AgentRole, AgentsApi, ModelChains}
import com.histmersion.domain.automation.goals.BGoalLinkRow
import com.histmersion.domain.automation.Approval

/** REST wiring of the embedded agents — every route delegates to [[AgentsControllers]]. */
object AgentsRoutes:

  def apply(a: AgentsControllers): List[ServerEndpoint[Any, Identity]] = List(
    AgentsApi.generate.serverLogic[Identity](req => a.generate(req)),
    AgentsApi.quota.serverLogicSuccess[Identity](_ => a.quota),
    AgentsApi.setFallthrough.serverLogic[Identity]((role, in) => a.setFallthrough(AgentRole.Wire(role.value), in)),
    AgentsApi.refreshQuota.serverLogic[Identity](in => a.refreshQuota(in)),
    AgentsApi.claudeGuard.serverLogicSuccess[Identity](r => a.guard(r)),
    AgentsApi.setPool.serverLogic[Identity]((pool, in) => a.setPool(pool, in)),
    AgentsApi.models.serverLogicSuccess[Identity](_ => a.models),
    AgentsApi.setChain.serverLogic[Identity]((role, in) => a.setChain(role, in)),
    AgentsApi.resetChain.serverLogic[Identity](role => a.resetChain(role)),
    AgentsApi.setAllowance.serverLogic[Identity](in => a.setAllowance(in)),
    AgentsApi.removeAllowance.serverLogic[Identity](target =>
      a.removeAllowance(ModelChains.AllowanceSet.Target(target.value))
    ),
    AgentsApi.doctor.serverLogicSuccess[Identity](offline =>
      a.doctor(AgentsControllers.Offline(offline.exists(_.value)))
    ),
    AgentsApi.probe.serverLogic[Identity](req => a.probe(req)),
    AgentsApi.startRun.serverLogic[Identity](req => a.startRun(req)),
    AgentsApi.runs.serverLogic[Identity]((f, limit, before, upTo) =>
      a.runsPage(
        f,
        limit.flatMap(l => PageSize.option(l.value)),
        before.map(c => Paging.Cursor(c.value)),
        upTo.map(c => Paging.Cursor(c.value))
      )
    ),
    AgentsApi.run.serverLogic[Identity](id => AgentsControllers.runId(id.value).flatMap(a.run)),
    AgentsApi.compare.serverLogic[Identity](ids => AgentsControllers.runIds(ids.map(_.value)).flatMap(a.compare)),
    AgentsApi.control.serverLogic[Identity]((id, action, usd, tokens) =>
      AgentsControllers
        .runId(id.value)
        .flatMap(i =>
          a.control(
            i,
            action,
            usd.map(u => AgentsControllers.BudgetUsd(u.value)),
            tokens.map(t => AgentsControllers.BudgetTokens(t.value))
          )
        )
    ),
    AgentsApi.approvals.serverLogic[Identity]((f, limit, before, upTo) =>
      a.approvalsPage(
        f,
        limit.flatMap(l => PageSize.option(l.value)),
        before.map(c => Paging.Cursor(c.value)),
        upTo.map(c => Paging.Cursor(c.value))
      )
    ),
    AgentsApi.decide.serverLogic[Identity]((id, decision, req) =>
      a.decide(Approval.Id(id.value), Approval.Outcome(decision.value), req)
    ),
    AgentsApi.goalView.serverLogic[Identity](slug => AgentsControllers.goalSlug(slug.value).flatMap(a.goalView)),
    AgentsApi.addGoalLink.serverLogic[Identity]((slug, in) =>
      AgentsControllers.goalSlug(slug.value).flatMap(g => a.addGoalLink(g, in))
    ),
    AgentsApi.removeGoalLink.serverLogic[Identity]((slug, kind, ref) =>
      AgentsControllers.goalSlug(slug.value).flatMap(g => a.removeGoalLink(g, kind, BGoalLinkRow.Ref(ref.value)))
    ),
    AgentsApi.setSchedule.serverLogic[Identity]((slug, kind, in) =>
      AgentsControllers.goalSlug(slug.value).flatMap(g => a.setSchedule(g, kind, in))
    ),
    AgentsApi.removeSchedule.serverLogic[Identity]((slug, kind) =>
      AgentsControllers.goalSlug(slug.value).flatMap(g => a.removeSchedule(g, kind))
    ),
    AgentsApi.schedules.serverLogicSuccess[Identity](_ => a.schedules)
  )
