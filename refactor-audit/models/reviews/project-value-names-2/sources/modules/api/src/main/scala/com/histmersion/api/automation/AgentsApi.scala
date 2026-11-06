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
  type Actions = ActionsValue
  type ActionsValue = ActionsValue.T
  object ActionsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
    (writeBase.put
      .in("models" / path[String]("role"))
      .in(jsonBody[ModelChains.ChainSet])
      .out(jsonBody[ModelChains.RoleChain])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.SetChainPart1Part1, _root_.com.histmersion.api.automation.ModelChains.ChainSet)]((refactorDecoded: (_root_.java.lang.String, _root_.com.histmersion.api.automation.ModelChains.ChainSet)) => (_root_.com.histmersion.api.automation.AgentsApi.SetChainPart1Part1((refactorDecoded)._1), (refactorDecoded)._2))((refactorEncoded: (_root_.com.histmersion.api.automation.AgentsApi.SetChainPart1Part1, _root_.com.histmersion.api.automation.ModelChains.ChainSet)) => (((refactorEncoded)._1).value, (refactorEncoded)._2))

  lazy val resetChain: PublicEndpoint[com.histmersion.api.automation.AgentsApi.ResetChainPart1, Api.Error, ModelChains.RoleChain, Any] =
    (writeBase.delete.in("models" / path[String]("role")).out(jsonBody[ModelChains.RoleChain])).mapIn[_root_.com.histmersion.api.automation.AgentsApi.ResetChainPart1]((refactorDecoded19: _root_.java.lang.String) => _root_.com.histmersion.api.automation.AgentsApi.ResetChainPart1(refactorDecoded19))((refactorEncoded19: _root_.com.histmersion.api.automation.AgentsApi.ResetChainPart1) => (refactorEncoded19).value)

  lazy val setAllowance: PublicEndpoint[ModelChains.AllowanceSet, Api.Error, ModelChains.AllowanceStatus, Any] =
    writeBase.put.in("allowances").in(jsonBody[ModelChains.AllowanceSet]).out(jsonBody[ModelChains.AllowanceStatus])

  lazy val removeAllowance: PublicEndpoint[com.histmersion.api.automation.AgentsApi.RemoveAllowancePart1, Api.Error, Goal.Plans.Removed, Any] =
    (writeBase.delete.in("allowances").in(query[String]("target")).out(jsonBody[Goal.Plans.Removed])).mapIn[_root_.com.histmersion.api.automation.AgentsApi.RemoveAllowancePart1]((refactorDecoded3: _root_.java.lang.String) => _root_.com.histmersion.api.automation.AgentsApi.RemoveAllowancePart1(refactorDecoded3))((refactorEncoded3: _root_.com.histmersion.api.automation.AgentsApi.RemoveAllowancePart1) => (refactorEncoded3).value)

  lazy val doctor: PublicEndpoint[Option[com.histmersion.api.automation.AgentsApi.DoctorPart1], Api.Error, DoctorReport, Any] =
    (base.get.in("doctor").in(query[Option[Boolean]]("offline")).out(jsonBody[DoctorReport])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.DoctorPart1]]((refactorDecoded4: _root_.scala.Option[_root_.scala.Boolean]) => (refactorDecoded4).map(refactorElement => _root_.com.histmersion.api.automation.AgentsApi.DoctorPart1(refactorElement)))((refactorEncoded4: _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.DoctorPart1]) => (refactorEncoded4).map(refactorElement2 => (refactorElement2).value))

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
    (base.get
      .in("runs")
      .in(ListFilters.runQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .out(jsonBody[List[RunInfo]])
      .out(Paging.headers)).mapIn[(_root_.com.histmersion.api.automation.ListFilters.RunFilter, _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part4])]((refactorDecoded5: (_root_.com.histmersion.api.automation.ListFilters.RunFilter, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => ((refactorDecoded5)._1, ((refactorDecoded5)._2).map(refactorElement3 => _root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part2(refactorElement3)), ((refactorDecoded5)._3).map(refactorElement4 => _root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part3(refactorElement4)), ((refactorDecoded5)._4).map(refactorElement5 => _root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part4(refactorElement5))))((refactorEncoded5: (_root_.com.histmersion.api.automation.ListFilters.RunFilter, _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.RunsPart1Part4])) => ((refactorEncoded5)._1, ((refactorEncoded5)._2).map(refactorElement6 => (refactorElement6).value), ((refactorEncoded5)._3).map(refactorElement7 => (refactorElement7).value), ((refactorEncoded5)._4).map(refactorElement8 => (refactorElement8).value)))

  lazy val run: PublicEndpoint[com.histmersion.api.automation.AgentsApi.RunPart1, Api.Error, RunDetail, Any] =
    (endpoint.get.in("api" / "agents" / "runs" / path[String]("id")).errorOut(agentErrors).out(jsonBody[RunDetail])).mapIn[_root_.com.histmersion.api.automation.AgentsApi.RunPart1]((refactorDecoded6: _root_.java.lang.String) => _root_.com.histmersion.api.automation.AgentsApi.RunPart1(refactorDecoded6))((refactorEncoded6: _root_.com.histmersion.api.automation.AgentsApi.RunPart1) => (refactorEncoded6).value)

  lazy val compare: PublicEndpoint[List[com.histmersion.api.automation.AgentsApi.ComparePart1], Api.Error, RunCompare.Comparison, Any] =
    (endpoint.get
      .in("api" / "agents" / "compare")
      .in(query[List[String]]("id"))
      .errorOut(agentErrors)
      .out(jsonBody[RunCompare.Comparison])).mapIn[_root_.scala.List[_root_.com.histmersion.api.automation.AgentsApi.ComparePart1]]((refactorDecoded24: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded24).map(refactorElement29 => _root_.com.histmersion.api.automation.AgentsApi.ComparePart1(refactorElement29)))((refactorEncoded24: _root_.scala.List[_root_.com.histmersion.api.automation.AgentsApi.ComparePart1]) => (refactorEncoded24).map(refactorElement30 => (refactorElement30).value))

  lazy val control: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.ControlPart1Part1, com.histmersion.api.automation.AgentsApi.ControlPart1Part2, Option[com.histmersion.api.automation.AgentsApi.ControlPart1Part3], Option[com.histmersion.api.automation.AgentsApi.ControlPart1Part4]), Api.Error, RunInfo, Any] =
    (writeBase.post
      .in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd"))
      .in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part1, _root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part2, _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part4])]((refactorDecoded25: (_root_.java.lang.String, _root_.java.lang.String, _root_.scala.Option[_root_.scala.Double], _root_.scala.Option[_root_.scala.Long])) => (_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part1((refactorDecoded25)._1), _root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part2((refactorDecoded25)._2), ((refactorDecoded25)._3).map(refactorElement31 => _root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part3(refactorElement31)), ((refactorDecoded25)._4).map(refactorElement32 => _root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part4(refactorElement32))))((refactorEncoded25: (_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part1, _root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part2, _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ControlPart1Part4])) => (((refactorEncoded25)._1).value, ((refactorEncoded25)._2).value, ((refactorEncoded25)._3).map(refactorElement33 => (refactorElement33).value), ((refactorEncoded25)._4).map(refactorElement34 => (refactorElement34).value)))

  val Actions: List[com.histmersion.api.automation.AgentsApi.Actions] = List(_root_.com.histmersion.api.automation.AgentsApi.ActionsValue("pause"), _root_.com.histmersion.api.automation.AgentsApi.ActionsValue("resume"), _root_.com.histmersion.api.automation.AgentsApi.ActionsValue("cancel"))

  lazy val approvals: PublicEndpoint[
    (ListFilters.ApprovalFilter, Option[com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part2], Option[com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part3], Option[com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part4]),
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
      .out(Paging.headers)).mapIn[(_root_.com.histmersion.api.automation.ListFilters.ApprovalFilter, _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part4])]((refactorDecoded26: (_root_.com.histmersion.api.automation.ListFilters.ApprovalFilter, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => ((refactorDecoded26)._1, ((refactorDecoded26)._2).map(refactorElement35 => _root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part2(refactorElement35)), ((refactorDecoded26)._3).map(refactorElement36 => _root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part3(refactorElement36)), ((refactorDecoded26)._4).map(refactorElement37 => _root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part4(refactorElement37))))((refactorEncoded26: (_root_.com.histmersion.api.automation.ListFilters.ApprovalFilter, _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.automation.AgentsApi.ApprovalsPart1Part4])) => ((refactorEncoded26)._1, ((refactorEncoded26)._2).map(refactorElement38 => (refactorElement38).value), ((refactorEncoded26)._3).map(refactorElement39 => (refactorElement39).value), ((refactorEncoded26)._4).map(refactorElement40 => (refactorElement40).value)))

  lazy val decide: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.DecidePart1Part1, com.histmersion.api.automation.AgentsApi.DecidePart1Part2, DecisionRequest), Api.Error, ApprovalInfo, Any] =
    (writeBase.post
      .in("approvals" / path[Long]("id") / path[String]("decision"))
      .in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.DecidePart1Part1, _root_.com.histmersion.api.automation.AgentsApi.DecidePart1Part2, _root_.com.histmersion.api.automation.Agents.DecisionRequest)]((refactorDecoded10: (_root_.scala.Long, _root_.java.lang.String, _root_.com.histmersion.api.automation.Agents.DecisionRequest)) => (_root_.com.histmersion.api.automation.AgentsApi.DecidePart1Part1((refactorDecoded10)._1), _root_.com.histmersion.api.automation.AgentsApi.DecidePart1Part2((refactorDecoded10)._2), (refactorDecoded10)._3))((refactorEncoded10: (_root_.com.histmersion.api.automation.AgentsApi.DecidePart1Part1, _root_.com.histmersion.api.automation.AgentsApi.DecidePart1Part2, _root_.com.histmersion.api.automation.Agents.DecisionRequest)) => (((refactorEncoded10)._1).value, ((refactorEncoded10)._2).value, (refactorEncoded10)._3))

  import com.histmersion.api.automation.Goal.Plans.*

  private lazy val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(agentErrors)

  lazy val goalView: PublicEndpoint[com.histmersion.api.automation.AgentsApi.GoalViewPart1, Api.Error, GoalView, Any] =
    (goalBase.get.in("view").out(jsonBody[GoalView])).mapIn[_root_.com.histmersion.api.automation.AgentsApi.GoalViewPart1]((refactorDecoded11: _root_.java.lang.String) => _root_.com.histmersion.api.automation.AgentsApi.GoalViewPart1(refactorDecoded11))((refactorEncoded11: _root_.com.histmersion.api.automation.AgentsApi.GoalViewPart1) => (refactorEncoded11).value)

  lazy val addGoalLink: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.AddGoalLinkPart1Part1, LinkAdd), Api.Error, GoalLink, Any] =
    (goalBase.post.in("links").in(jsonBody[LinkAdd]).out(jsonBody[GoalLink])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.AddGoalLinkPart1Part1, _root_.com.histmersion.api.automation.Goal.Plans.LinkAdd)]((refactorDecoded12: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Goal.Plans.LinkAdd)) => (_root_.com.histmersion.api.automation.AgentsApi.AddGoalLinkPart1Part1((refactorDecoded12)._1), (refactorDecoded12)._2))((refactorEncoded12: (_root_.com.histmersion.api.automation.AgentsApi.AddGoalLinkPart1Part1, _root_.com.histmersion.api.automation.Goal.Plans.LinkAdd)) => (((refactorEncoded12)._1).value, (refactorEncoded12)._2))

  lazy val removeGoalLink: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part1, com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part2, com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part3), Api.Error, Removed, Any] =
    (goalBase.delete.in("links").in(query[String]("kind")).in(query[String]("ref")).out(jsonBody[Removed])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part1, _root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part2, _root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part3)]((refactorDecoded30: (_root_.java.lang.String, _root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part1((refactorDecoded30)._1), _root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part2((refactorDecoded30)._2), _root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part3((refactorDecoded30)._3)))((refactorEncoded30: (_root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part1, _root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part2, _root_.com.histmersion.api.automation.AgentsApi.RemoveGoalLinkPart1Part3)) => (((refactorEncoded30)._1).value, ((refactorEncoded30)._2).value, ((refactorEncoded30)._3).value))

  lazy val setSchedule: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part1, com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part2, ScheduleSet), Api.Error, GoalSchedule, Any] =
    (goalBase.put.in("schedules" / path[String]("kind")).in(jsonBody[ScheduleSet]).out(jsonBody[GoalSchedule])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part1, _root_.com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part2, _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet)]((refactorDecoded31: (_root_.java.lang.String, _root_.java.lang.String, _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet)) => (_root_.com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part1((refactorDecoded31)._1), _root_.com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part2((refactorDecoded31)._2), (refactorDecoded31)._3))((refactorEncoded31: (_root_.com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part1, _root_.com.histmersion.api.automation.AgentsApi.SetSchedulePart1Part2, _root_.com.histmersion.api.automation.Goal.Plans.ScheduleSet)) => (((refactorEncoded31)._1).value, ((refactorEncoded31)._2).value, (refactorEncoded31)._3))

  lazy val removeSchedule: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part1, com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part2), Api.Error, Removed, Any] =
    (goalBase.delete.in("schedules" / path[String]("kind")).out(jsonBody[Removed])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part1, _root_.com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part2)]((refactorDecoded32: (_root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part1((refactorDecoded32)._1), _root_.com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part2((refactorDecoded32)._2)))((refactorEncoded32: (_root_.com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part1, _root_.com.histmersion.api.automation.AgentsApi.RemoveSchedulePart1Part2)) => (((refactorEncoded32)._1).value, ((refactorEncoded32)._2).value))

  lazy val schedules: PublicEndpoint[Unit, Api.Error, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  lazy val generate: PublicEndpoint[GenerateRequest, Api.Error, GenerateResult, Any] =
    writeBase.post.in("generate").in(jsonBody[GenerateRequest]).out(jsonBody[GenerateResult])

  lazy val quota: PublicEndpoint[Unit, Api.Error, PlanQuota.QuotaInfo, Any] =
    base.get.in("quota").out(jsonBody[PlanQuota.QuotaInfo])

  lazy val setPool: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetPoolPart1Part1, PlanQuota.PoolSet), Api.Error, PlanQuota.PoolStatus, Any] =
    (writeBase.put.in("quota" / path[String]("pool")).in(jsonBody[PlanQuota.PoolSet]).out(jsonBody[PlanQuota.PoolStatus])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.SetPoolPart1Part1, _root_.com.histmersion.api.automation.PlanQuota.PoolSet)]((refactorDecoded33: (_root_.java.lang.String, _root_.com.histmersion.api.automation.PlanQuota.PoolSet)) => (_root_.com.histmersion.api.automation.AgentsApi.SetPoolPart1Part1((refactorDecoded33)._1), (refactorDecoded33)._2))((refactorEncoded33: (_root_.com.histmersion.api.automation.AgentsApi.SetPoolPart1Part1, _root_.com.histmersion.api.automation.PlanQuota.PoolSet)) => (((refactorEncoded33)._1).value, (refactorEncoded33)._2))

  lazy val setFallthrough: PublicEndpoint[(com.histmersion.api.automation.AgentsApi.SetFallthroughPart1Part1, PlanQuota.FallthroughSet), Api.Error, PlanQuota.QuotaInfo, Any] =
    (writeBase.put
      .in("quota" / "fallthrough" / path[String]("role"))
      .in(jsonBody[PlanQuota.FallthroughSet])
      .out(jsonBody[PlanQuota.QuotaInfo])).mapIn[(_root_.com.histmersion.api.automation.AgentsApi.SetFallthroughPart1Part1, _root_.com.histmersion.api.automation.PlanQuota.FallthroughSet)]((refactorDecoded34: (_root_.java.lang.String, _root_.com.histmersion.api.automation.PlanQuota.FallthroughSet)) => (_root_.com.histmersion.api.automation.AgentsApi.SetFallthroughPart1Part1((refactorDecoded34)._1), (refactorDecoded34)._2))((refactorEncoded34: (_root_.com.histmersion.api.automation.AgentsApi.SetFallthroughPart1Part1, _root_.com.histmersion.api.automation.PlanQuota.FallthroughSet)) => (((refactorEncoded34)._1).value, (refactorEncoded34)._2))

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
