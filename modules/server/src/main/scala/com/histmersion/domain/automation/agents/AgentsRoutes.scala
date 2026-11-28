package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.modelchains.AllowanceSet
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.PageSize
import com.histmersion.api.Paging
import com.histmersion.api.automation.{AgentRole, AgentsApi, ModelChains}
import com.histmersion.domain.automation.goals.BGoalLinkRow
import com.histmersion.domain.automation.Approval
import com.histmersion.api.Api
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{tolerated, LintRule}

/** REST wiring of the embedded agents — every route delegates to [[AgentsControllers]]. */
object AgentsRoutes:

  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def apply(a: AgentsControllers): List[ServerEndpoint[Any, Identity]] = List(
    AgentsApi.generate.serverLogic[Identity](req => a.generate(req).left.map(Api.Error(_))),
    AgentsApi.quota.serverLogicSuccess[Identity](_ => a.quota),
    AgentsApi.setFallthrough.serverLogic[Identity]((role, in) =>
      a.setFallthrough(AgentRole.Wire(role.value), in).left.map(Api.Error(_))
    ),
    AgentsApi.refreshQuota.serverLogic[Identity](in => a.refreshQuota(in).left.map(Api.Error(_))),
    AgentsApi.claudeGuard.serverLogicSuccess[Identity](r => a.guard(r)),
    AgentsApi.setPool.serverLogic[Identity]((pool, in) => a.setPool(pool, in).left.map(Api.Error(_))),
    AgentsApi.models.serverLogicSuccess[Identity](_ => a.models),
    AgentsApi.setChain.serverLogic[Identity]((role, in) => a.setChain(role, in).left.map(Api.Error(_))),
    AgentsApi.resetChain.serverLogic[Identity](role => a.resetChain(role).left.map(Api.Error(_))),
    AgentsApi.setAllowance.serverLogic[Identity](in => a.setAllowance(in).left.map(Api.Error(_))),
    AgentsApi.removeAllowance.serverLogic[Identity](target =>
      a.removeAllowance(AllowanceSet.Target(target.value)).left.map(Api.Error(_))
    ),
    AgentsApi.doctor.serverLogicSuccess[Identity](offline =>
      a.doctor(AgentsControllers.Offline(offline.exists(_.online)))
    ),
    AgentsApi.probe.serverLogic[Identity](req => a.probe(req)),
    AgentsApi.startRun.serverLogic[Identity](req => CreatorControllers.rest(req)(a.startRun(req))),
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
      CreatorControllers.rest(req)(a.decide(Approval.Id(id.value), Approval.Outcome(decision.value), req))
    ),
    AgentsApi.goalView.serverLogic[Identity](slug => AgentsControllers.goalSlug(slug.value).flatMap(a.goalView)),
    AgentsApi.addGoalLink.serverLogic[Identity]((slug, in) =>
      CreatorControllers.rest(in)(AgentsControllers.goalSlug(slug.value).flatMap(g => a.addGoalLink(g, in)))
    ),
    AgentsApi.removeGoalLink.serverLogic[Identity]((slug, kind, ref) =>
      AgentsControllers.goalSlug(slug.value).flatMap(g => a.removeGoalLink(g, kind, BGoalLinkRow.Ref(ref.value)))
    ),
    AgentsApi.setSchedule.serverLogic[Identity]((slug, kind, in) =>
      CreatorControllers.rest(in)(AgentsControllers.goalSlug(slug.value).flatMap(g => a.setSchedule(g, kind, in)))
    ),
    AgentsApi.removeSchedule.serverLogic[Identity]((slug, kind) =>
      AgentsControllers.goalSlug(slug.value).flatMap(g => a.removeSchedule(g, kind))
    ),
    AgentsApi.schedules.serverLogicSuccess[Identity](_ => a.schedules)
  )
