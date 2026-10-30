package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

object ManagersApi:
  import Managers.*

  private lazy val errors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  private lazy val base = endpoint.in("api" / "agents" / "managers").errorOut(errors)
  private lazy val one = base.in(path[String]("id"))

  lazy val list: PublicEndpoint[Unit, ApiError, List[ManagerInfo], Any] =
    base.get.out(jsonBody[List[ManagerInfo]])

  lazy val create: PublicEndpoint[ManagerCreate, ApiError, ManagerInfo, Any] =
    base.post.in(jsonBody[ManagerCreate]).out(jsonBody[ManagerInfo])

  lazy val get: PublicEndpoint[String, ApiError, ManagerDetail, Any] =
    one.get.out(jsonBody[ManagerDetail])

  lazy val update: PublicEndpoint[(String, ManagerUpdate), ApiError, ManagerInfo, Any] =
    one.put.in(jsonBody[ManagerUpdate]).out(jsonBody[ManagerInfo])

  lazy val delete: PublicEndpoint[String, ApiError, GoalPlans.Removed, Any] =
    one.delete.out(jsonBody[GoalPlans.Removed])

  lazy val items: PublicEndpoint[(String, Option[String], Option[Int], Option[Int]), ApiError, ItemPage, Any] =
    one.get
      .in("items")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ItemPage])

  lazy val addItem: PublicEndpoint[(String, EnqueueRequest), ApiError, ManagerItem, Any] =
    one.post.in("items").in(jsonBody[EnqueueRequest]).out(jsonBody[ManagerItem])

  lazy val patchItem: PublicEndpoint[(String, String, ItemPatch), ApiError, ManagerItem, Any] =
    one.patch.in("items" / path[String]("item")).in(jsonBody[ItemPatch]).out(jsonBody[ManagerItem])

  lazy val hints: PublicEndpoint[(String, Option[Boolean]), ApiError, List[ManagerHint], Any] =
    one.get.in("hints").in(query[Option[Boolean]]("all")).out(jsonBody[List[ManagerHint]])

  lazy val addHint: PublicEndpoint[(String, HintAdd), ApiError, ManagerHint, Any] =
    one.post.in("hints").in(jsonBody[HintAdd]).out(jsonBody[ManagerHint])

  lazy val removeHint: PublicEndpoint[(String, Long), ApiError, GoalPlans.Removed, Any] =
    one.delete.in("hints" / path[Long]("hint")).out(jsonBody[GoalPlans.Removed])

  lazy val events: PublicEndpoint[(String, Option[Long], Option[Int]), ApiError, EventPage, Any] =
    one.get.in("events").in(query[Option[Long]]("before")).in(query[Option[Int]]("limit")).out(jsonBody[EventPage])

  lazy val action: PublicEndpoint[(String, String, Option[Boolean]), ApiError, ManagerInfo, Any] =
    one.post.in(path[String]("action")).in(query[Option[Boolean]]("runs")).out(jsonBody[ManagerInfo])

  lazy val enqueue: PublicEndpoint[EnqueueRequest, ApiError, EnqueueResult, Any] =
    endpoint.post
      .in("api" / "agents" / "enqueue")
      .errorOut(errors)
      .in(jsonBody[EnqueueRequest])
      .out(jsonBody[EnqueueResult])

  lazy val autopilot: PublicEndpoint[Unit, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.get.in("api" / "agents" / "autopilot").errorOut(errors).out(jsonBody[Autopilot.AutopilotStatus])

  lazy val setAutopilot: PublicEndpoint[Autopilot.AutopilotSet, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot")
      .errorOut(errors)
      .in(jsonBody[Autopilot.AutopilotSet])
      .out(jsonBody[Autopilot.AutopilotStatus])

  lazy val setMode: PublicEndpoint[AgentMode.ModeSet, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "mode")
      .errorOut(errors)
      .in(jsonBody[AgentMode.ModeSet])
      .out(jsonBody[Autopilot.AutopilotStatus])

  lazy val repairAcquire: PublicEndpoint[RepairAcquire.RepairRequest, ApiError, RepairAcquire.RepairResult, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "repair-acquire")
      .errorOut(errors)
      .in(jsonBody[RepairAcquire.RepairRequest])
      .out(jsonBody[RepairAcquire.RepairResult])

  lazy val acquireRecheck: PublicEndpoint[AcquireChecks.RecheckRequest, ApiError, AcquireChecks.RecheckResult, Any] =
    endpoint.post
      .in("api" / "queue" / "acquire" / "recheck")
      .errorOut(errors)
      .in(jsonBody[AcquireChecks.RecheckRequest])
      .out(jsonBody[AcquireChecks.RecheckResult])

  lazy val focus: PublicEndpoint[Unit, ApiError, Autopilot.FocusInfo, Any] =
    endpoint.get.in("api" / "agents" / "autopilot" / "focus").errorOut(errors).out(jsonBody[Autopilot.FocusInfo])

  lazy val setFocus: PublicEndpoint[Autopilot.FocusSet, ApiError, Autopilot.FocusInfo, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "focus")
      .errorOut(errors)
      .in(jsonBody[Autopilot.FocusSet])
      .out(jsonBody[Autopilot.FocusInfo])

  private lazy val sched = endpoint.in("api" / "agents" / "scheduler").errorOut(errors)

  lazy val scheduler: PublicEndpoint[Option[Int], ApiError, AgentScheduler.SchedulerStatus, Any] =
    sched.get.in(query[Option[Int]]("limit")).out(jsonBody[AgentScheduler.SchedulerStatus])

  lazy val setScheduler: PublicEndpoint[AgentScheduler.SchedulerSet, ApiError, AgentScheduler.SchedulerStatus, Any] =
    sched.post.in(jsonBody[AgentScheduler.SchedulerSet]).out(jsonBody[AgentScheduler.SchedulerStatus])

  lazy val schedulerWhy: PublicEndpoint[String, ApiError, AgentScheduler.WhyInfo, Any] =
    sched.get.in("why" / path[String]("item")).out(jsonBody[AgentScheduler.WhyInfo])

  lazy val digest: PublicEndpoint[Unit, ApiError, ApprovalDigest.Digest, Any] =
    endpoint.get.in("api" / "agents" / "approvals" / "digest").errorOut(errors).out(jsonBody[ApprovalDigest.Digest])

  lazy val decideAll: PublicEndpoint[ApprovalDigest.BulkDecide, ApiError, ApprovalDigest.BulkResult, Any] =
    endpoint.post
      .in("api" / "agents" / "approvals" / "decide")
      .errorOut(errors)
      .in(jsonBody[ApprovalDigest.BulkDecide])
      .out(jsonBody[ApprovalDigest.BulkResult])

  lazy val drain: PublicEndpoint[Autopilot.DrainSet, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "drain")
      .errorOut(errors)
      .in(jsonBody[Autopilot.DrainSet])
      .out(jsonBody[Autopilot.AutopilotStatus])

  lazy val stalls: PublicEndpoint[Option[Int], ApiError, Stalls.StallReport, Any] =
    endpoint.get
      .in("api" / "agents" / "stalls")
      .errorOut(errors)
      .in(query[Option[Int]]("days"))
      .out(jsonBody[Stalls.StallReport])

  /** The fixed-path endpoints come before `action`, whose last segment is a parameter. */
  lazy val all: List[AnyEndpoint] = List(
    list,
    create,
    get,
    update,
    delete,
    items,
    addItem,
    patchItem,
    hints,
    addHint,
    removeHint,
    events,
    action,
    enqueue,
    autopilot,
    setAutopilot,
    setMode,
    repairAcquire,
    acquireRecheck,
    focus,
    setFocus,
    scheduler,
    setScheduler,
    schedulerWhy,
    digest,
    decideAll,
    drain,
    stalls
  )
