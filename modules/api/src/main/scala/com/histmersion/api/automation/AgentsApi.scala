package com.histmersion
package api.automation
import com.histmersion.api.json.NewtypeCodecs.given
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.StatusCode
import com.histmersion.api.{Paging, Write}
import com.histmersion.api.given
import com.histmersion.api.Api
import com.histmersion.api.research.ReviewGoal
import scala.`package`.List
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@atBoundary(
  BoundaryKind.Http,
  "declares the Tapir endpoints of the agents routes with their request and response bodies"
)
object AgentsApi:
  type Actions = ActionsValue
  export Agents.Action as ActionsValue

  import Agents.*

  private lazy val base = endpoint.in("api" / "agents").errorOut(Api.problemOut)

  val CodeConflict = "conflict"

  private lazy val writeBase = endpoint.in("api" / "agents").errorOut(Api.problemOut)

  lazy val models: PublicEndpoint[Unit, Api.Error, ModelsInfo, Any] =
    base.get.in("models").out(jsonBody[ModelsInfo])

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val setChain: PublicEndpoint[(AgentRole.Wire, ModelChains.ChainSet), Api.Error, ModelChains.RoleChain, Any] =
    (writeBase.put
      .in("models" / path[String]("role"))
      .in(jsonBody[ModelChains.ChainSet])
      .out(jsonBody[ModelChains.RoleChain]))
      .mapIn[(AgentRole.Wire, ModelChains.ChainSet)]((refactorDecoded: (String, ModelChains.ChainSet)) =>
        (AgentRole.Wire((refactorDecoded)._1), (refactorDecoded)._2)
      )((refactorEncoded: (AgentRole.Wire, ModelChains.ChainSet)) =>
        (((refactorEncoded)._1).value, (refactorEncoded)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val resetChain: PublicEndpoint[AgentRole.Wire, Api.Error, ModelChains.RoleChain, Any] =
    (writeBase.delete
      .in("models" / path[String]("role"))
      .out(jsonBody[ModelChains.RoleChain]))
      .mapIn[AgentRole.Wire]((refactorDecoded19: String) => AgentRole.Wire(refactorDecoded19))(
        (refactorEncoded19: AgentRole.Wire) => (refactorEncoded19).value
      )

  lazy val setAllowance: PublicEndpoint[ModelChains.AllowanceSet, Api.Error, ModelChains.AllowanceStatus, Any] =
    writeBase.put.in("allowances").in(jsonBody[ModelChains.AllowanceSet]).out(jsonBody[ModelChains.AllowanceStatus])

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val removeAllowance: PublicEndpoint[ReviewGoal.Slug, Api.Error, Goal.Plans.Removed, Any] =
    (writeBase.delete
      .in("allowances")
      .in(query[String]("target"))
      .out(jsonBody[Goal.Plans.Removed]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded3: String) => ReviewGoal.Slug(refactorDecoded3))(
        (refactorEncoded3: ReviewGoal.Slug) => (refactorEncoded3).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(
    LintRule.GetWrites,
    "pending S1 follow-up 5 (a GET or HEAD route writes nothing): GET: Process ProcessBuilder.start"
  )
  lazy val doctor: PublicEndpoint[Option[DoctorReport.Online], Api.Error, DoctorReport, Any] =
    (base.get
      .in("doctor")
      .in(query[Option[Boolean]]("offline"))
      .out(jsonBody[DoctorReport]))
      .mapIn[Option[DoctorReport.Online]]((refactorDecoded4: Option[Boolean]) =>
        (refactorDecoded4).map(refactorElement => DoctorReport.Online(refactorElement))
      )((refactorEncoded4: Option[DoctorReport.Online]) =>
        (refactorEncoded4).map(refactorElement2 => (refactorElement2).value)
      )

  lazy val probe: PublicEndpoint[ProbeRequest, Api.Error, ProbeResult, Any] =
    writeBase.post.in("probe").in(jsonBody[ProbeRequest]).out(jsonBody[ProbeResult])

  lazy val startRun: PublicEndpoint[RunRequest, Api.Error, RunInfo, Any] =
    writeBase.post.in("runs").in(jsonBody[RunRequest]).out(jsonBody[RunInfo])

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val runs: PublicEndpoint[
    (ListFilters.RunFilter, Option[Paging.Limit], Option[RunInfo.Status], Option[RunInfo.Pipeline]),
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
      .out(Paging.headers))
      .mapIn[(ListFilters.RunFilter, Option[Paging.Limit], Option[RunInfo.Status], Option[RunInfo.Pipeline])](
        (refactorDecoded5: (ListFilters.RunFilter, Option[Int], Option[String], Option[String])) =>
          (
            (refactorDecoded5)._1,
            ((refactorDecoded5)._2).map(refactorElement3 => Paging.Limit(refactorElement3)),
            ((refactorDecoded5)._3).map(refactorElement4 => RunInfo.Status(refactorElement4)),
            ((refactorDecoded5)._4).map(refactorElement5 => RunInfo.Pipeline(refactorElement5))
          )
      )(
        (refactorEncoded5: (
            ListFilters.RunFilter,
            Option[Paging.Limit],
            Option[RunInfo.Status],
            Option[RunInfo.Pipeline]
        )) =>
          (
            (refactorEncoded5)._1,
            ((refactorEncoded5)._2).map(refactorElement6 => (refactorElement6).value),
            ((refactorEncoded5)._3).map(refactorElement7 => (refactorElement7).value),
            ((refactorEncoded5)._4).map(refactorElement8 => (refactorElement8).value)
          )
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val run: PublicEndpoint[RunInfo.Id, Api.Error, RunDetail, Any] =
    (endpoint.get
      .in("api" / "agents" / "runs" / path[String]("id"))
      .errorOut(Api.problemOut)
      .out(jsonBody[RunDetail]))
      .mapIn[RunInfo.Id]((refactorDecoded6: String) => RunInfo.Id(refactorDecoded6))((refactorEncoded6: RunInfo.Id) =>
        (refactorEncoded6).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val compare: PublicEndpoint[List[RunInfo.Id], Api.Error, RunCompare.Comparison, Any] =
    (endpoint.get
      .in("api" / "agents" / "compare")
      .in(query[List[String]]("id"))
      .errorOut(Api.problemOut)
      .out(jsonBody[RunCompare.Comparison]))
      .mapIn[List[RunInfo.Id]]((refactorDecoded24: List[String]) =>
        (refactorDecoded24).map(refactorElement29 => RunInfo.Id(refactorElement29))
      )((refactorEncoded24: List[RunInfo.Id]) =>
        (refactorEncoded24).map(refactorElement30 => (refactorElement30).value)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val control: PublicEndpoint[
    (RunInfo.Id, Action, Option[RunRequest.BudgetUsd], Option[RunRequest.BudgetTokens]),
    Api.Error,
    RunInfo,
    Any
  ] =
    (writeBase.post
      .in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd"))
      .in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo]))
      .mapIn[(RunInfo.Id, Action, Option[RunRequest.BudgetUsd], Option[RunRequest.BudgetTokens])](
        (refactorDecoded25: (String, String, Option[Double], Option[Long])) =>
          (
            RunInfo.Id((refactorDecoded25)._1),
            Action((refactorDecoded25)._2),
            ((refactorDecoded25)._3).map(refactorElement31 => RunRequest.BudgetUsd(refactorElement31)),
            ((refactorDecoded25)._4).map(refactorElement32 => RunRequest.BudgetTokens(refactorElement32))
          )
      )((refactorEncoded25: (RunInfo.Id, Action, Option[RunRequest.BudgetUsd], Option[RunRequest.BudgetTokens])) =>
        (
          ((refactorEncoded25)._1).value,
          ((refactorEncoded25)._2).value,
          ((refactorEncoded25)._3).map(refactorElement33 => (refactorElement33).value),
          ((refactorEncoded25)._4).map(refactorElement34 => (refactorElement34).value)
        )
      )

  val Actions: List[Actions] = List(Action("pause"), Action("resume"), Action("cancel"))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val approvals: PublicEndpoint[
    (ListFilters.ApprovalFilter, Option[Paging.Limit], Option[Approval.State.Wire], Option[Approval.Kind.Wire]),
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
      .errorOut(Api.problemOut)
      .out(jsonBody[List[ApprovalInfo]])
      .out(Paging.headers))
      .mapIn[
        (ListFilters.ApprovalFilter, Option[Paging.Limit], Option[Approval.State.Wire], Option[Approval.Kind.Wire])
      ]((refactorDecoded26: (ListFilters.ApprovalFilter, Option[Int], Option[String], Option[String])) =>
        (
          (refactorDecoded26)._1,
          ((refactorDecoded26)._2).map(refactorElement35 => Paging.Limit(refactorElement35)),
          ((refactorDecoded26)._3).map(refactorElement36 => Approval.State.Wire(refactorElement36)),
          ((refactorDecoded26)._4).map(refactorElement37 => Approval.Kind.Wire(refactorElement37))
        )
      )(
        (refactorEncoded26: (
            ListFilters.ApprovalFilter,
            Option[Paging.Limit],
            Option[Approval.State.Wire],
            Option[Approval.Kind.Wire]
        )) =>
          (
            (refactorEncoded26)._1,
            ((refactorEncoded26)._2).map(refactorElement38 => (refactorElement38).value),
            ((refactorEncoded26)._3).map(refactorElement39 => (refactorElement39).value),
            ((refactorEncoded26)._4).map(refactorElement40 => (refactorElement40).value)
          )
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val decide: PublicEndpoint[(ApprovalInfo.Id, Decision.Wire, DecisionRequest), Api.Error, ApprovalInfo, Any] =
    (writeBase.post
      .in("approvals" / path[Long]("id") / path[String]("decision"))
      .in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo]))
      .mapIn[(ApprovalInfo.Id, Decision.Wire, DecisionRequest)]((refactorDecoded10: (Long, String, DecisionRequest)) =>
        (ApprovalInfo.Id((refactorDecoded10)._1), Decision.Wire((refactorDecoded10)._2), (refactorDecoded10)._3)
      )((refactorEncoded10: (ApprovalInfo.Id, Decision.Wire, DecisionRequest)) =>
        (((refactorEncoded10)._1).value, ((refactorEncoded10)._2).value, (refactorEncoded10)._3)
      )

  import com.histmersion.api.automation.Goal.Plans.*

  private lazy val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(Api.problemOut)

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val goalView: PublicEndpoint[ReviewGoal.Slug, Api.Error, GoalView, Any] =
    (goalBase.get
      .in("view")
      .out(jsonBody[GoalView]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded11: String) => ReviewGoal.Slug(refactorDecoded11))(
        (refactorEncoded11: ReviewGoal.Slug) => (refactorEncoded11).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val addGoalLink: PublicEndpoint[(ReviewGoal.Slug, LinkAdd), Api.Error, GoalLink, Any] =
    (goalBase.post
      .in("links")
      .in(jsonBody[LinkAdd])
      .out(jsonBody[GoalLink]))
      .mapIn[(ReviewGoal.Slug, LinkAdd)]((refactorDecoded12: (String, LinkAdd)) =>
        (ReviewGoal.Slug((refactorDecoded12)._1), (refactorDecoded12)._2)
      )((refactorEncoded12: (ReviewGoal.Slug, LinkAdd)) => (((refactorEncoded12)._1).value, (refactorEncoded12)._2))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val removeGoalLink: PublicEndpoint[(ReviewGoal.Slug, Goal.LinkKind.Wire, Citekey), Api.Error, Removed, Any] =
    (goalBase.delete
      .in("links")
      .in(query[String]("kind"))
      .in(query[String]("ref"))
      .out(jsonBody[Removed]))
      .mapIn[(ReviewGoal.Slug, Goal.LinkKind.Wire, Citekey)]((refactorDecoded30: (String, String, String)) =>
        (
          ReviewGoal.Slug((refactorDecoded30)._1),
          Goal.LinkKind.Wire((refactorDecoded30)._2),
          Citekey((refactorDecoded30)._3)
        )
      )((refactorEncoded30: (ReviewGoal.Slug, Goal.LinkKind.Wire, Citekey)) =>
        (((refactorEncoded30)._1).value, ((refactorEncoded30)._2).value, ((refactorEncoded30)._3).value)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val setSchedule: PublicEndpoint[
    (ReviewGoal.Slug, api.automation.ScheduleKind.Wire, ScheduleSet),
    Api.Error,
    GoalSchedule,
    Any
  ] =
    (goalBase.put
      .in("schedules" / path[String]("kind"))
      .in(jsonBody[ScheduleSet])
      .out(jsonBody[GoalSchedule]))
      .mapIn[(ReviewGoal.Slug, api.automation.ScheduleKind.Wire, ScheduleSet)](
        (refactorDecoded31: (String, String, ScheduleSet)) =>
          (
            ReviewGoal.Slug((refactorDecoded31)._1),
            api.automation.ScheduleKind.Wire((refactorDecoded31)._2),
            (refactorDecoded31)._3
          )
      )((refactorEncoded31: (ReviewGoal.Slug, api.automation.ScheduleKind.Wire, ScheduleSet)) =>
        (((refactorEncoded31)._1).value, ((refactorEncoded31)._2).value, (refactorEncoded31)._3)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val removeSchedule
      : PublicEndpoint[(ReviewGoal.Slug, api.automation.ScheduleKind.Wire), Api.Error, Removed, Any] =
    (goalBase.delete
      .in("schedules" / path[String]("kind"))
      .out(jsonBody[Removed]))
      .mapIn[(ReviewGoal.Slug, api.automation.ScheduleKind.Wire)]((refactorDecoded32: (String, String)) =>
        (
          ReviewGoal.Slug((refactorDecoded32)._1),
          api.automation.ScheduleKind.Wire((refactorDecoded32)._2)
        )
      )((refactorEncoded32: (ReviewGoal.Slug, api.automation.ScheduleKind.Wire)) =>
        (((refactorEncoded32)._1).value, ((refactorEncoded32)._2).value)
      )

  lazy val schedules: PublicEndpoint[Unit, Api.Error, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  lazy val generate: PublicEndpoint[GenerateRequest, Api.Error, GenerateResult, Any] =
    writeBase.post.in("generate").in(jsonBody[GenerateRequest]).out(jsonBody[GenerateResult])

  lazy val quota: PublicEndpoint[Unit, Api.Error, PlanQuota.QuotaInfo, Any] =
    base.get.in("quota").out(jsonBody[PlanQuota.QuotaInfo])

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val setPool: PublicEndpoint[(PlanQuota.PoolIdText, PlanQuota.PoolSet), Api.Error, PlanQuota.PoolStatus, Any] =
    (writeBase.put
      .in("quota" / path[String]("pool"))
      .in(jsonBody[PlanQuota.PoolSet])
      .out(jsonBody[PlanQuota.PoolStatus]))
      .mapIn[(PlanQuota.PoolIdText, PlanQuota.PoolSet)]((refactorDecoded33: (String, PlanQuota.PoolSet)) =>
        (PlanQuota.PoolIdText((refactorDecoded33)._1), (refactorDecoded33)._2)
      )((refactorEncoded33: (PlanQuota.PoolIdText, PlanQuota.PoolSet)) =>
        (((refactorEncoded33)._1).value, (refactorEncoded33)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val setFallthrough
      : PublicEndpoint[(PlanQuota.PoolIdText, PlanQuota.FallthroughSet), Api.Error, PlanQuota.QuotaInfo, Any] =
    (writeBase.put
      .in("quota" / "fallthrough" / path[String]("role"))
      .in(jsonBody[PlanQuota.FallthroughSet])
      .out(jsonBody[PlanQuota.QuotaInfo]))
      .mapIn[(PlanQuota.PoolIdText, PlanQuota.FallthroughSet)](
        (refactorDecoded34: (String, PlanQuota.FallthroughSet)) =>
          (PlanQuota.PoolIdText((refactorDecoded34)._1), (refactorDecoded34)._2)
      )((refactorEncoded34: (PlanQuota.PoolIdText, PlanQuota.FallthroughSet)) =>
        (((refactorEncoded34)._1).value, (refactorEncoded34)._2)
      )

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
