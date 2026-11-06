package com.histmersion.api.automation
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.{Paging, Write}
import com.histmersion.api.given
import com.histmersion.api.Api

object AgentsApi:
  type Actions = ActionsValue

  import Agents.*

  private lazy val base = endpoint.in("api" / "agents").errorOut(jsonBody[Api.Error])

  private lazy val agentErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound))).andThen(refactorResult => ((refactorResult) == (true)))),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(AgentsApi.CodeConflict))).andThen(refactorResult4 => ((refactorResult4) == (true)))),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val CodeConflict = "conflict"

  private lazy val writeBase = endpoint.in("api" / "agents").errorOut(agentErrors)

  lazy val models: PublicEndpoint[Unit, Api.Error, ModelsInfo, Any] =
    base.get.in("models").out(jsonBody[ModelsInfo])

  lazy val setChain: PublicEndpoint[(com.histmersion.api.automation.AgentRole.Wire, ModelChains.ChainSet), Api.Error, ModelChains.RoleChain, Any] =
    (writeBase.put
      .in("models" / path[String]("role"))
      .in(jsonBody[ModelChains.ChainSet])
      .out(jsonBody[ModelChains.RoleChain])).mapIn[(_root_.com.histmersion.api.automation.AgentRole.Wire, _root_.com.histmersion.api.automation.ModelChains.ChainSet)]((refactorDecoded: (_root_.java.lang.String, _root_.com.histmersion.api.automation.ModelChains.ChainSet)) => (_root_.com.histmersion.api.automation.AgentRole.Wire((refactorDecoded)._1), (refactorDecoded)._2))((refactorEncoded: (_root_.com.histmersion.api.automation.AgentRole.Wire, _root_.com.histmersion.api.automation.ModelChains.ChainSet)) => (((refactorEncoded)._1).value, (refactorEncoded)._2))

  lazy val resetChain: PublicEndpoint[com.histmersion.api.automation.AgentRole.Wire, Api.Error, ModelChains.RoleChain, Any] =
    (writeBase.delete.in("models" / path[String]("role")).out(jsonBody[ModelChains.RoleChain])).mapIn[_root_.com.histmersion.api.automation.AgentRole.Wire]((refactorDecoded19: _root_.java.lang.String) => _root_.com.histmersion.api.automation.AgentRole.Wire(refactorDecoded19))((refactorEncoded19: _root_.com.histmersion.api.automation.AgentRole.Wire) => (refactorEncoded19).value)

  lazy val setAllowance: PublicEndpoint[ModelChains.AllowanceSet, Api.Error, ModelChains.AllowanceStatus, Any] =
    writeBase.put.in("allowances").in(jsonBody[ModelChains.AllowanceSet]).out(jsonBody[ModelChains.AllowanceStatus])

  lazy val removeAllowance: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, Api.Error, Goal.Plans.Removed, Any] =
    (writeBase.delete.in("allowances").in(query[String]("target")).out(jsonBody[Goal.Plans.Removed])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded3: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded3))((refactorEncoded3: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded3).value)

  lazy val doctor: PublicEndpoint[Option[com.histmersion.api.automation.Agents.DoctorReport.Online], Api.Error, DoctorReport, Any] =
    (base.get.in("doctor").in(query[Option[Boolean]]("offline")).out(jsonBody[DoctorReport])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.automation.Agents.DoctorReport.Online]]((refactorDecoded4: _root_.scala.Option[_root_.scala.Boolean]) => (refactorDecoded4).map(refactorElement => _root_.com.histmersion.api.automation.Agents.DoctorReport.Online(refactorElement)))((refactorEncoded4: _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.DoctorReport.Online]) => (refactorEncoded4).map(refactorElement2 => (refactorElement2).value))

  lazy val probe: PublicEndpoint[ProbeRequest, Api.Error, ProbeResult, Any] =
    writeBase.post.in("probe").in(jsonBody[ProbeRequest]).out(jsonBody[ProbeResult])

  lazy val startRun: PublicEndpoint[RunRequest, Api.Error, RunInfo, Any] =
    writeBase.post.in("runs").in(jsonBody[RunRequest]).out(jsonBody[RunInfo])

  lazy val runs: PublicEndpoint[
    (ListFilters.RunFilter, Option[com.histmersion.api.Paging.Limit], Option[com.histmersion.api.automation.Agents.RunInfo.Status], Option[com.histmersion.api.automation.Agents.RunInfo.Pipeline]),
    Api.Error,
    (List[RunInfo], Paging.Page),
    Any
  ] =
    (base.get
      .in("runs")
      .in(ListFilters.runQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .out(jsonBody[List[RunInfo]])
      .out(Paging.headers)).mapIn[(_root_.com.histmersion.api.automation.ListFilters.RunFilter, _root_.scala.Option[_root_.com.histmersion.api.Paging.Limit], _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunInfo.Status], _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunInfo.Pipeline])]((refactorDecoded5: (_root_.com.histmersion.api.automation.ListFilters.RunFilter, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => ((refactorDecoded5)._1, ((refactorDecoded5)._2).map(refactorElement3 => _root_.com.histmersion.api.Paging.Limit(refactorElement3)), ((refactorDecoded5)._3).map(refactorElement4 => _root_.com.histmersion.api.automation.Agents.RunInfo.Status(refactorElement4)), ((refactorDecoded5)._4).map(refactorElement5 => _root_.com.histmersion.api.automation.Agents.RunInfo.Pipeline(refactorElement5))))((refactorEncoded5: (_root_.com.histmersion.api.automation.ListFilters.RunFilter, _root_.scala.Option[_root_.com.histmersion.api.Paging.Limit], _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunInfo.Status], _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunInfo.Pipeline])) => ((refactorEncoded5)._1, ((refactorEncoded5)._2).map(refactorElement6 => (refactorElement6).value), ((refactorEncoded5)._3).map(refactorElement7 => (refactorElement7).value), ((refactorEncoded5)._4).map(refactorElement8 => (refactorElement8).value)))

  lazy val run: PublicEndpoint[com.histmersion.api.automation.Agents.RunInfo.Id, Api.Error, RunDetail, Any] =
    (endpoint.get.in("api" / "agents" / "runs" / path[String]("id")).errorOut(agentErrors).out(jsonBody[RunDetail])).mapIn[_root_.com.histmersion.api.automation.Agents.RunInfo.Id]((refactorDecoded6: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Agents.RunInfo.Id(refactorDecoded6))((refactorEncoded6: _root_.com.histmersion.api.automation.Agents.RunInfo.Id) => (refactorEncoded6).value)

  lazy val compare: PublicEndpoint[List[com.histmersion.api.automation.Agents.RunInfo.Id], Api.Error, RunCompare.Comparison, Any] =
    (endpoint.get
      .in("api" / "agents" / "compare")
      .in(query[List[String]]("id"))
      .errorOut(agentErrors)
      .out(jsonBody[RunCompare.Comparison])).mapIn[_root_.scala.List[_root_.com.histmersion.api.automation.Agents.RunInfo.Id]]((refactorDecoded24: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded24).map(refactorElement29 => _root_.com.histmersion.api.automation.Agents.RunInfo.Id(refactorElement29)))((refactorEncoded24: _root_.scala.List[_root_.com.histmersion.api.automation.Agents.RunInfo.Id]) => (refactorEncoded24).map(refactorElement30 => (refactorElement30).value))

  lazy val control: PublicEndpoint[(com.histmersion.api.automation.Agents.RunInfo.Id, com.histmersion.api.automation.Agents.Action, Option[com.histmersion.api.automation.Agents.RunRequest.BudgetUsd], Option[com.histmersion.api.automation.Agents.RunRequest.BudgetTokens]), Api.Error, RunInfo, Any] =
    (writeBase.post
      .in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd"))
      .in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo])).mapIn[(_root_.com.histmersion.api.automation.Agents.RunInfo.Id, _root_.com.histmersion.api.automation.Agents.Action, _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunRequest.BudgetUsd], _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunRequest.BudgetTokens])]((refactorDecoded25: (_root_.java.lang.String, _root_.java.lang.String, _root_.scala.Option[_root_.scala.Double], _root_.scala.Option[_root_.scala.Long])) => (_root_.com.histmersion.api.automation.Agents.RunInfo.Id((refactorDecoded25)._1), _root_.com.histmersion.api.automation.Agents.Action((refactorDecoded25)._2), ((refactorDecoded25)._3).map(refactorElement31 => _root_.com.histmersion.api.automation.Agents.RunRequest.BudgetUsd(refactorElement31)), ((refactorDecoded25)._4).map(refactorElement32 => _root_.com.histmersion.api.automation.Agents.RunRequest.BudgetTokens(refactorElement32))))((refactorEncoded25: (_root_.com.histmersion.api.automation.Agents.RunInfo.Id, _root_.com.histmersion.api.automation.Agents.Action, _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunRequest.BudgetUsd], _root_.scala.Option[_root_.com.histmersion.api.automation.Agents.RunRequest.BudgetTokens])) => (((refactorEncoded25)._1).value, ((refactorEncoded25)._2).value, ((refactorEncoded25)._3).map(refactorElement33 => (refactorElement33).value), ((refactorEncoded25)._4).map(refactorElement34 => (refactorElement34).value)))

  val Actions: List[com.histmersion.api.automation.AgentsApi.Actions] = List(_root_.com.histmersion.api.automation.Agents.Action("pause"), _root_.com.histmersion.api.automation.Agents.Action("resume"), _root_.com.histmersion.api.automation.Agents.Action("cancel"))

  lazy val approvals: PublicEndpoint[
    (ListFilters.ApprovalFilter, Option[com.histmersion.api.Paging.Limit], Option[com.histmersion.api.automation.Approval.State.Wire], Option[com.histmersion.api.automation.Approval.Kind.Wire]),
    Api.Error,
    (List[ApprovalInfo], Paging.Page),
    Any
  ] =
    (endpoint.get
      .in("api" / "agents" / "approvals")
      .in(ListFilters.approvalQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .errorOut(agentErrors)
      .out(jsonBody[List[ApprovalInfo]])
      .out(Paging.headers)).mapIn[(_root_.com.histmersion.api.automation.ListFilters.ApprovalFilter, _root_.scala.Option[_root_.com.histmersion.api.Paging.Limit], _root_.scala.Option[_root_.com.histmersion.api.automation.Approval.State.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Approval.Kind.Wire])]((refactorDecoded26: (_root_.com.histmersion.api.automation.ListFilters.ApprovalFilter, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => ((refactorDecoded26)._1, ((refactorDecoded26)._2).map(refactorElement35 => _root_.com.histmersion.api.Paging.Limit(refactorElement35)), ((refactorDecoded26)._3).map(refactorElement36 => _root_.com.histmersion.api.automation.Approval.State.Wire(refactorElement36)), ((refactorDecoded26)._4).map(refactorElement37 => _root_.com.histmersion.api.automation.Approval.Kind.Wire(refactorElement37))))((refactorEncoded26: (_root_.com.histmersion.api.automation.ListFilters.ApprovalFilter, _root_.scala.Option[_root_.com.histmersion.api.Paging.Limit], _root_.scala.Option[_root_.com.histmersion.api.automation.Approval.State.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Approval.Kind.Wire])) => ((refactorEncoded26)._1, ((refactorEncoded26)._2).map(refactorElement38 => (refactorElement38).value), ((refactorEncoded26)._3).map(refactorElement39 => (refactorElement39).value), ((refactorEncoded26)._4).map(refactorElement40 => (refactorElement40).value)))

  lazy val decide: PublicEndpoint[(com.histmersion.api.automation.Agents.ApprovalInfo.Id, com.histmersion.api.automation.Agents.Decision.Wire, DecisionRequest), Api.Error, ApprovalInfo, Any] =
    (writeBase.post
      .in("approvals" / path[Long]("id") / path[String]("decision"))
      .in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo])).mapIn[(_root_.com.histmersion.api.automation.Agents.ApprovalInfo.Id, _root_.com.histmersion.api.automation.Agents.Decision.Wire, _root_.com.histmersion.api.automation.Agents.DecisionRequest)]((refactorDecoded10: (_root_.scala.Long, _root_.java.lang.String, _root_.com.histmersion.api.automation.Agents.DecisionRequest)) => (_root_.com.histmersion.api.automation.Agents.ApprovalInfo.Id((refactorDecoded10)._1), _root_.com.histmersion.api.automation.Agents.Decision.Wire((refactorDecoded10)._2), (refactorDecoded10)._3))((refactorEncoded10: (_root_.com.histmersion.api.automation.Agents.ApprovalInfo.Id, _root_.com.histmersion.api.automation.Agents.Decision.Wire, _root_.com.histmersion.api.automation.Agents.DecisionRequest)) => (((refactorEncoded10)._1).value, ((refactorEncoded10)._2).value, (refactorEncoded10)._3))

  import com.histmersion.api.automation.Goal.Plans.*

  private lazy val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(agentErrors)

  lazy val goalView: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, Api.Error, GoalView, Any] =
    (goalBase.get.in("view").out(jsonBody[GoalView])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded11: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded11))((refactorEncoded11: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded11).value)

  lazy val addGoalLink: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, LinkAdd), Api.Error, GoalLink, Any] =
    (goalBase.post.in("links").in(jsonBody[LinkAdd]).out(jsonBody[GoalLink])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.Goal.Plans.LinkAdd)]((refactorDecoded12: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Goal.Plans.LinkAdd)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded12)._1), (refactorDecoded12)._2))((refactorEncoded12: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.Goal.Plans.LinkAdd)) => (((refactorEncoded12)._1).value, (refactorEncoded12)._2))

  lazy val removeGoalLink: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.automation.Goal.LinkKind.Wire, com.histmersion.api.library.Citekey), Api.Error, Removed, Any] =
    (goalBase.delete.in("links").in(query[String]("kind")).in(query[String]("ref")).out(jsonBody[Removed])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.Goal.LinkKind.Wire, _root_.com.histmersion.api.library.Citekey)]((refactorDecoded30: (_root_.java.lang.String, _root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded30)._1), _root_.com.histmersion.api.automation.Goal.LinkKind.Wire((refactorDecoded30)._2), _root_.com.histmersion.api.library.Citekey((refactorDecoded30)._3)))((refactorEncoded30: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.Goal.LinkKind.Wire, _root_.com.histmersion.api.library.Citekey)) => (((refactorEncoded30)._1).value, ((refactorEncoded30)._2).value, ((refactorEncoded30)._3).value))

  lazy val setSchedule: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.automation.ScheduleKind.Wire, ScheduleSet), Api.Error, GoalSchedule, Any] =
    (goalBase.put.in("schedules" / path[String]("kind")).in(jsonBody[ScheduleSet]).out(jsonBody[GoalSchedule])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.ScheduleKind.Wire, _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet)]((refactorDecoded31: (_root_.java.lang.String, _root_.java.lang.String, _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded31)._1), _root_.com.histmersion.api.automation.ScheduleKind.Wire((refactorDecoded31)._2), (refactorDecoded31)._3))((refactorEncoded31: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.ScheduleKind.Wire, _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet)) => (((refactorEncoded31)._1).value, ((refactorEncoded31)._2).value, (refactorEncoded31)._3))

  lazy val removeSchedule: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.automation.ScheduleKind.Wire), Api.Error, Removed, Any] =
    (goalBase.delete.in("schedules" / path[String]("kind")).out(jsonBody[Removed])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.ScheduleKind.Wire)]((refactorDecoded32: (_root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded32)._1), _root_.com.histmersion.api.automation.ScheduleKind.Wire((refactorDecoded32)._2)))((refactorEncoded32: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.automation.ScheduleKind.Wire)) => (((refactorEncoded32)._1).value, ((refactorEncoded32)._2).value))

  lazy val schedules: PublicEndpoint[Unit, Api.Error, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  lazy val generate: PublicEndpoint[GenerateRequest, Api.Error, GenerateResult, Any] =
    writeBase.post.in("generate").in(jsonBody[GenerateRequest]).out(jsonBody[GenerateResult])

  lazy val quota: PublicEndpoint[Unit, Api.Error, PlanQuota.QuotaInfo, Any] =
    base.get.in("quota").out(jsonBody[PlanQuota.QuotaInfo])

  lazy val setPool: PublicEndpoint[(com.histmersion.api.automation.PlanQuota.PoolIdText, PlanQuota.PoolSet), Api.Error, PlanQuota.PoolStatus, Any] =
    (writeBase.put.in("quota" / path[String]("pool")).in(jsonBody[PlanQuota.PoolSet]).out(jsonBody[PlanQuota.PoolStatus])).mapIn[(_root_.com.histmersion.api.automation.PlanQuota.PoolIdText, _root_.com.histmersion.api.automation.PlanQuota.PoolSet)]((refactorDecoded33: (_root_.java.lang.String, _root_.com.histmersion.api.automation.PlanQuota.PoolSet)) => (_root_.com.histmersion.api.automation.PlanQuota.PoolIdText((refactorDecoded33)._1), (refactorDecoded33)._2))((refactorEncoded33: (_root_.com.histmersion.api.automation.PlanQuota.PoolIdText, _root_.com.histmersion.api.automation.PlanQuota.PoolSet)) => (((refactorEncoded33)._1).value, (refactorEncoded33)._2))

  lazy val setFallthrough: PublicEndpoint[(com.histmersion.api.automation.PlanQuota.PoolIdText, PlanQuota.FallthroughSet), Api.Error, PlanQuota.QuotaInfo, Any] =
    (writeBase.put
      .in("quota" / "fallthrough" / path[String]("role"))
      .in(jsonBody[PlanQuota.FallthroughSet])
      .out(jsonBody[PlanQuota.QuotaInfo])).mapIn[(_root_.com.histmersion.api.automation.PlanQuota.PoolIdText, _root_.com.histmersion.api.automation.PlanQuota.FallthroughSet)]((refactorDecoded34: (_root_.java.lang.String, _root_.com.histmersion.api.automation.PlanQuota.FallthroughSet)) => (_root_.com.histmersion.api.automation.PlanQuota.PoolIdText((refactorDecoded34)._1), (refactorDecoded34)._2))((refactorEncoded34: (_root_.com.histmersion.api.automation.PlanQuota.PoolIdText, _root_.com.histmersion.api.automation.PlanQuota.FallthroughSet)) => (((refactorEncoded34)._1).value, (refactorEncoded34)._2))

  lazy val refreshQuota: PublicEndpoint[PlanQuota.QuotaRefresh, Api.Error, PlanQuota.QuotaRefreshed, Any] =
    writeBase.post.in("quota" / "refresh").in(jsonBody[PlanQuota.QuotaRefresh]).out(jsonBody[PlanQuota.QuotaRefreshed])

  lazy val claudeGuard: PublicEndpoint[PlanQuota.ModReport, Api.Error, PlanQuota.GuardAnswer, Any] =
    writeBase.post.in("quota" / "claude").in(jsonBody[PlanQuota.ModReport]).out(jsonBody[PlanQuota.GuardAnswer])

  lazy val all: List[AnyEndpoint] = List(
    generate,
    quota,
    setFallthrough,
    refreshQuota,
    claudeGuard,
    setPool,
    models,
    setChain,
    resetChain,
    setAllowance,
    removeAllowance,
    doctor,
    probe,
    startRun,
    runs,
    run,
    compare,
    control,
    approvals,
    decide,
    goalView,
    addGoalLink,
    removeGoalLink,
    setSchedule,
    removeSchedule,
    schedules
  )
