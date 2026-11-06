package com.histmersion.api.automation
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.{Paging, Write}
import com.histmersion.api.given
import com.histmersion.api.Api

object AgentsApi:
  type SetChainPart1Part1 = SetChainPart1Part1.T
  object SetChainPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResetChainPart1 = ResetChainPart1.T
  object ResetChainPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveAllowancePart1 = RemoveAllowancePart1.T
  object RemoveAllowancePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoctorPart1 = DoctorPart1.T
  object DoctorPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPart1Part2 = RunsPart1Part2.T
  object RunsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPart1Part3 = RunsPart1Part3.T
  object RunsPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPart1Part4 = RunsPart1Part4.T
  object RunsPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunPart1 = RunPart1.T
  object RunPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ComparePart1 = ComparePart1.T
  object ComparePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlPart1Part1 = ControlPart1Part1.T
  object ControlPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlPart1Part2 = ControlPart1Part2.T
  object ControlPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlPart1Part3 = ControlPart1Part3.T
  object ControlPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlPart1Part4 = ControlPart1Part4.T
  object ControlPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Actions = Actions.T
  object Actions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsPart1Part2 = ApprovalsPart1Part2.T
  object ApprovalsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsPart1Part3 = ApprovalsPart1Part3.T
  object ApprovalsPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsPart1Part4 = ApprovalsPart1Part4.T
  object ApprovalsPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecidePart1Part1 = DecidePart1Part1.T
  object DecidePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecidePart1Part2 = DecidePart1Part2.T
  object DecidePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalViewPart1 = GoalViewPart1.T
  object GoalViewPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddGoalLinkPart1Part1 = AddGoalLinkPart1Part1.T
  object AddGoalLinkPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveGoalLinkPart1Part1 = RemoveGoalLinkPart1Part1.T
  object RemoveGoalLinkPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveGoalLinkPart1Part2 = RemoveGoalLinkPart1Part2.T
  object RemoveGoalLinkPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveGoalLinkPart1Part3 = RemoveGoalLinkPart1Part3.T
  object RemoveGoalLinkPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSchedulePart1Part1 = SetSchedulePart1Part1.T
  object SetSchedulePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSchedulePart1Part2 = SetSchedulePart1Part2.T
  object SetSchedulePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveSchedulePart1Part1 = RemoveSchedulePart1Part1.T
  object RemoveSchedulePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveSchedulePart1Part2 = RemoveSchedulePart1Part2.T
  object RemoveSchedulePart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPoolPart1Part1 = SetPoolPart1Part1.T
  object SetPoolPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFallthroughPart1Part1 = SetFallthroughPart1Part1.T
  object SetFallthroughPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import Agents.*

  private lazy val base = endpoint.in("api" / "agents").errorOut(jsonBody[Api.Error])

  private lazy val agentErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(Write.CodeNotFound))).andThen(refactorResult => ((refactorResult) == _root_.com.histmersion.api.Api.Error.HasCodeResultPart2(true)))),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.HasCodeCode(AgentsApi.CodeConflict))).andThen(refactorResult4 => ((refactorResult4) == _root_.com.histmersion.api.Api.Error.HasCodeResultPart2(true)))),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val CodeConflict = "conflict"

  private lazy val writeBase = endpoint.in("api" / "agents").errorOut(agentErrors)

  lazy val models: PublicEndpoint[Unit, Api.Error, ModelsInfo, Any] =
    base.get.in("models").out(jsonBody[ModelsInfo])

  lazy val setChain: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetChainPart1Part1, ModelChains.ChainSet), Api.Error, ModelChains.RoleChain, Any] =
    writeBase.put
      .in("models" / path[String]("role"))
      .in(jsonBody[ModelChains.ChainSet])
      .out(jsonBody[ModelChains.RoleChain])

  lazy val resetChain: PublicEndpoint[com.histmersion.api.automation.AgentsApi.ResetChainPart1, Api.Error, ModelChains.RoleChain, Any] =
    writeBase.delete.in("models" / path[String]("role")).out(jsonBody[ModelChains.RoleChain])

  lazy val setAllowance: PublicEndpoint[ModelChains.AllowanceSet, Api.Error, ModelChains.AllowanceStatus, Any] =
    writeBase.put.in("allowances").in(jsonBody[ModelChains.AllowanceSet]).out(jsonBody[ModelChains.AllowanceStatus])

  lazy val removeAllowance: PublicEndpoint[com.histmersion.api.automation.AgentsApi.RemoveAllowancePart1, Api.Error, Goal.Plans.Removed, Any] =
    writeBase.delete.in("allowances").in(query[String]("target")).out(jsonBody[Goal.Plans.Removed])

  lazy val doctor: PublicEndpoint[Option[com.histmersion.api.automation.AgentsApi.DoctorPart1], Api.Error, DoctorReport, Any] =
    base.get.in("doctor").in(query[Option[Boolean]]("offline")).out(jsonBody[DoctorReport])

  lazy val probe: PublicEndpoint[ProbeRequest, Api.Error, ProbeResult, Any] =
    writeBase.post.in("probe").in(jsonBody[ProbeRequest]).out(jsonBody[ProbeResult])

  lazy val startRun: PublicEndpoint[RunRequest, Api.Error, RunInfo, Any] =
    writeBase.post.in("runs").in(jsonBody[RunRequest]).out(jsonBody[RunInfo])

  lazy val runs: PublicEndpoint[
    (ListFilters.RunFilter, Option[com.histmersion.api.automation.AgentsApi.RunsPart1Part2], Option[com.histmersion.api.automation.AgentsApi.RunsPart1Part3], Option[com.histmersion.api.automation.AgentsApi.RunsPart1Part4]),
    Api.Error,
    (List[RunInfo], Paging.Page),
    Any
  ] =
    base.get
      .in("runs")
      .in(ListFilters.runQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .out(jsonBody[List[RunInfo]])
      .out(Paging.headers)

  lazy val run: PublicEndpoint[com.histmersion.api.automation.AgentsApi.RunPart1, Api.Error, RunDetail, Any] =
    endpoint.get.in("api" / "agents" / "runs" / path[String]("id")).errorOut(agentErrors).out(jsonBody[RunDetail])

  lazy val compare: PublicEndpoint[List[com.histmersion.api.automation.AgentsApi.ComparePart1], Api.Error, RunCompare.Comparison, Any] =
    endpoint.get
      .in("api" / "agents" / "compare")
      .in(query[List[String]]("id"))
      .errorOut(agentErrors)
      .out(jsonBody[RunCompare.Comparison])

  lazy val control: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.ControlPart1Part1, com.histmersion.api.automation.AgentsApi.ControlPart1Part2, Option[com.histmersion.api.automation.AgentsApi.ControlPart1Part3], Option[com.histmersion.api.automation.AgentsApi.ControlPart1Part4]), Api.Error, RunInfo, Any] =
    writeBase.post
      .in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd"))
      .in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo])

  val Actions: List[com.histmersion.api.automation.AgentsApi.Actions] = List(_root_.com.histmersion.api.automation.AgentsApi.Actions("pause"), _root_.com.histmersion.api.automation.AgentsApi.Actions("resume"), _root_.com.histmersion.api.automation.AgentsApi.Actions("cancel"))

  lazy val approvals: PublicEndpoint[
    (ListFilters.ApprovalFilter, Option[com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part2], Option[com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part3], Option[com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part4]),
    Api.Error,
    (List[ApprovalInfo], Paging.Page),
    Any
  ] =
    endpoint.get
      .in("api" / "agents" / "approvals")
      .in(ListFilters.approvalQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .errorOut(agentErrors)
      .out(jsonBody[List[ApprovalInfo]])
      .out(Paging.headers)

  lazy val decide: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.DecidePart1Part1, com.histmersion.api.automation.AgentsApi.DecidePart1Part2, DecisionRequest), Api.Error, ApprovalInfo, Any] =
    writeBase.post
      .in("approvals" / path[Long]("id") / path[String]("decision"))
      .in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo])

  import com.histmersion.api.automation.Goal.Plans.*

  private lazy val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(agentErrors)

  lazy val goalView: PublicEndpoint[com.histmersion.api.automation.AgentsApi.GoalViewPart1, Api.Error, GoalView, Any] =
    goalBase.get.in("view").out(jsonBody[GoalView])

  lazy val addGoalLink: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.AddGoalLinkPart1Part1, LinkAdd), Api.Error, GoalLink, Any] =
    goalBase.post.in("links").in(jsonBody[LinkAdd]).out(jsonBody[GoalLink])

  lazy val removeGoalLink: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part1, com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part2, com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part3), Api.Error, Removed, Any] =
    goalBase.delete.in("links").in(query[String]("kind")).in(query[String]("ref")).out(jsonBody[Removed])

  lazy val setSchedule: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part1, com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part2, ScheduleSet), Api.Error, GoalSchedule, Any] =
    goalBase.put.in("schedules" / path[String]("kind")).in(jsonBody[ScheduleSet]).out(jsonBody[GoalSchedule])

  lazy val removeSchedule: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part1, com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part2), Api.Error, Removed, Any] =
    goalBase.delete.in("schedules" / path[String]("kind")).out(jsonBody[Removed])

  lazy val schedules: PublicEndpoint[Unit, Api.Error, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  lazy val generate: PublicEndpoint[GenerateRequest, Api.Error, GenerateResult, Any] =
    writeBase.post.in("generate").in(jsonBody[GenerateRequest]).out(jsonBody[GenerateResult])

  lazy val quota: PublicEndpoint[Unit, Api.Error, PlanQuota.QuotaInfo, Any] =
    base.get.in("quota").out(jsonBody[PlanQuota.QuotaInfo])

  lazy val setPool: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetPoolPart1Part1, PlanQuota.PoolSet), Api.Error, PlanQuota.PoolStatus, Any] =
    writeBase.put.in("quota" / path[String]("pool")).in(jsonBody[PlanQuota.PoolSet]).out(jsonBody[PlanQuota.PoolStatus])

  lazy val setFallthrough: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetFallthroughPart1Part1, PlanQuota.FallthroughSet), Api.Error, PlanQuota.QuotaInfo, Any] =
    writeBase.put
      .in("quota" / "fallthrough" / path[String]("role"))
      .in(jsonBody[PlanQuota.FallthroughSet])
      .out(jsonBody[PlanQuota.QuotaInfo])

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
