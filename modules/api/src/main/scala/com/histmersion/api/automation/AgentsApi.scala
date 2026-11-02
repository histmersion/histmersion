package com.histmersion.api.automation
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.{ApiError, Paging, Write}
import com.histmersion.api.given

object AgentsApi:
  import Agents.*

  private lazy val base = endpoint.in("api" / "agents").errorOut(jsonBody[ApiError])

  private lazy val agentErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  val CodeConflict = "conflict"

  private lazy val writeBase = endpoint.in("api" / "agents").errorOut(agentErrors)

  lazy val models: PublicEndpoint[Unit, ApiError, ModelsInfo, Any] =
    base.get.in("models").out(jsonBody[ModelsInfo])

  lazy val setChain: PublicEndpoint[(String, ModelChains.ChainSet), ApiError, ModelChains.RoleChain, Any] =
    writeBase.put
      .in("models" / path[String]("role"))
      .in(jsonBody[ModelChains.ChainSet])
      .out(jsonBody[ModelChains.RoleChain])

  lazy val resetChain: PublicEndpoint[String, ApiError, ModelChains.RoleChain, Any] =
    writeBase.delete.in("models" / path[String]("role")).out(jsonBody[ModelChains.RoleChain])

  lazy val setAllowance: PublicEndpoint[ModelChains.AllowanceSet, ApiError, ModelChains.AllowanceStatus, Any] =
    writeBase.put.in("allowances").in(jsonBody[ModelChains.AllowanceSet]).out(jsonBody[ModelChains.AllowanceStatus])

  lazy val removeAllowance: PublicEndpoint[String, ApiError, GoalPlans.Removed, Any] =
    writeBase.delete.in("allowances").in(query[String]("target")).out(jsonBody[GoalPlans.Removed])

  lazy val doctor: PublicEndpoint[Option[Boolean], ApiError, DoctorReport, Any] =
    base.get.in("doctor").in(query[Option[Boolean]]("offline")).out(jsonBody[DoctorReport])

  lazy val probe: PublicEndpoint[ProbeRequest, ApiError, ProbeResult, Any] =
    writeBase.post.in("probe").in(jsonBody[ProbeRequest]).out(jsonBody[ProbeResult])

  lazy val startRun: PublicEndpoint[RunRequest, ApiError, RunInfo, Any] =
    writeBase.post.in("runs").in(jsonBody[RunRequest]).out(jsonBody[RunInfo])

  lazy val runs: PublicEndpoint[
    (ListFilters.RunFilter, Option[Int], Option[String], Option[String]),
    ApiError,
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

  lazy val run: PublicEndpoint[String, ApiError, RunDetail, Any] =
    endpoint.get.in("api" / "agents" / "runs" / path[String]("id")).errorOut(agentErrors).out(jsonBody[RunDetail])

  lazy val compare: PublicEndpoint[List[String], ApiError, RunCompare.Comparison, Any] =
    endpoint.get
      .in("api" / "agents" / "compare")
      .in(query[List[String]]("id"))
      .errorOut(agentErrors)
      .out(jsonBody[RunCompare.Comparison])

  lazy val control: PublicEndpoint[(String, String, Option[Double], Option[Long]), ApiError, RunInfo, Any] =
    writeBase.post
      .in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd"))
      .in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo])

  val Actions: List[String] = List("pause", "resume", "cancel")

  lazy val approvals: PublicEndpoint[
    (ListFilters.ApprovalFilter, Option[Int], Option[String], Option[String]),
    ApiError,
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

  lazy val decide: PublicEndpoint[(Long, String, DecisionRequest), ApiError, ApprovalInfo, Any] =
    writeBase.post
      .in("approvals" / path[Long]("id") / path[String]("decision"))
      .in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo])

  import GoalPlans.*

  private lazy val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(agentErrors)

  lazy val goalView: PublicEndpoint[String, ApiError, GoalView, Any] =
    goalBase.get.in("view").out(jsonBody[GoalView])

  lazy val addGoalLink: PublicEndpoint[(String, LinkAdd), ApiError, GoalLink, Any] =
    goalBase.post.in("links").in(jsonBody[LinkAdd]).out(jsonBody[GoalLink])

  lazy val removeGoalLink: PublicEndpoint[(String, String, String), ApiError, Removed, Any] =
    goalBase.delete.in("links").in(query[String]("kind")).in(query[String]("ref")).out(jsonBody[Removed])

  lazy val setSchedule: PublicEndpoint[(String, String, ScheduleSet), ApiError, GoalSchedule, Any] =
    goalBase.put.in("schedules" / path[String]("kind")).in(jsonBody[ScheduleSet]).out(jsonBody[GoalSchedule])

  lazy val removeSchedule: PublicEndpoint[(String, String), ApiError, Removed, Any] =
    goalBase.delete.in("schedules" / path[String]("kind")).out(jsonBody[Removed])

  lazy val schedules: PublicEndpoint[Unit, ApiError, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  lazy val generate: PublicEndpoint[GenerateRequest, ApiError, GenerateResult, Any] =
    writeBase.post.in("generate").in(jsonBody[GenerateRequest]).out(jsonBody[GenerateResult])

  lazy val quota: PublicEndpoint[Unit, ApiError, PlanQuota.QuotaInfo, Any] =
    base.get.in("quota").out(jsonBody[PlanQuota.QuotaInfo])

  lazy val setPool: PublicEndpoint[(String, PlanQuota.PoolSet), ApiError, PlanQuota.PoolStatus, Any] =
    writeBase.put.in("quota" / path[String]("pool")).in(jsonBody[PlanQuota.PoolSet]).out(jsonBody[PlanQuota.PoolStatus])

  lazy val setFallthrough: PublicEndpoint[(String, PlanQuota.FallthroughSet), ApiError, PlanQuota.QuotaInfo, Any] =
    writeBase.put
      .in("quota" / "fallthrough" / path[String]("role"))
      .in(jsonBody[PlanQuota.FallthroughSet])
      .out(jsonBody[PlanQuota.QuotaInfo])

  lazy val refreshQuota: PublicEndpoint[PlanQuota.QuotaRefresh, ApiError, PlanQuota.QuotaRefreshed, Any] =
    writeBase.post.in("quota" / "refresh").in(jsonBody[PlanQuota.QuotaRefresh]).out(jsonBody[PlanQuota.QuotaRefreshed])

  lazy val claudeGuard: PublicEndpoint[PlanQuota.ModReport, ApiError, PlanQuota.GuardAnswer, Any] =
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
