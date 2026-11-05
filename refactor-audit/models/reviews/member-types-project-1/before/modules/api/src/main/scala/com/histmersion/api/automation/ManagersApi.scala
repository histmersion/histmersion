package com.histmersion.api.automation
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api

object ManagersApi:
  import Managers.*

  /** The managers' errors: 404 for a missing one, 409 for a run in the way, 400 otherwise. */
  lazy val errors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Api.Error])(Api.Error.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])(Api.Error.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  private lazy val base = endpoint.in("api" / "agents" / "managers").errorOut(errors)
  private lazy val one = base.in(path[String]("id"))

  lazy val list: PublicEndpoint[Unit, Api.Error, List[ManagerInfo], Any] =
    base.get.out(jsonBody[List[ManagerInfo]])

  lazy val create: PublicEndpoint[ManagerCreate, Api.Error, ManagerInfo, Any] =
    base.post.in(jsonBody[ManagerCreate]).out(jsonBody[ManagerInfo])

  lazy val get: PublicEndpoint[String, Api.Error, ManagerDetail, Any] =
    one.get.out(jsonBody[ManagerDetail])

  lazy val update: PublicEndpoint[(String, ManagerUpdate), Api.Error, ManagerInfo, Any] =
    one.put.in(jsonBody[ManagerUpdate]).out(jsonBody[ManagerInfo])

  lazy val delete: PublicEndpoint[String, Api.Error, Goal.Plans.Removed, Any] =
    one.delete.out(jsonBody[Goal.Plans.Removed])

  lazy val items: PublicEndpoint[(String, Option[String], Option[Int], Option[Int]), Api.Error, ItemPage, Any] =
    one.get
      .in("items")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ItemPage])

  lazy val addItem: PublicEndpoint[(String, EnqueueRequest), Api.Error, ManagerItem, Any] =
    one.post.in("items").in(jsonBody[EnqueueRequest]).out(jsonBody[ManagerItem])

  lazy val patchItem: PublicEndpoint[(String, String, ItemPatch), Api.Error, ManagerItem, Any] =
    one.patch.in("items" / path[String]("item")).in(jsonBody[ItemPatch]).out(jsonBody[ManagerItem])

  lazy val hints: PublicEndpoint[(String, Option[Boolean]), Api.Error, List[ManagerHint], Any] =
    one.get.in("hints").in(query[Option[Boolean]]("all")).out(jsonBody[List[ManagerHint]])

  lazy val addHint: PublicEndpoint[(String, HintAdd), Api.Error, ManagerHint, Any] =
    one.post.in("hints").in(jsonBody[HintAdd]).out(jsonBody[ManagerHint])

  lazy val removeHint: PublicEndpoint[(String, Long), Api.Error, Goal.Plans.Removed, Any] =
    one.delete.in("hints" / path[Long]("hint")).out(jsonBody[Goal.Plans.Removed])

  lazy val events: PublicEndpoint[(String, Option[Long], Option[Int]), Api.Error, EventPage, Any] =
    one.get.in("events").in(query[Option[Long]]("before")).in(query[Option[Int]]("limit")).out(jsonBody[EventPage])

  lazy val action: PublicEndpoint[(String, String, Option[Boolean]), Api.Error, ManagerInfo, Any] =
    one.post.in(path[String]("action")).in(query[Option[Boolean]]("runs")).out(jsonBody[ManagerInfo])

  lazy val enqueue: PublicEndpoint[EnqueueRequest, Api.Error, EnqueueResult, Any] =
    endpoint.post
      .in("api" / "agents" / "enqueue")
      .errorOut(errors)
      .in(jsonBody[EnqueueRequest])
      .out(jsonBody[EnqueueResult])

  lazy val focus: PublicEndpoint[Unit, Api.Error, Autopilot.FocusInfo, Any] =
    endpoint.get.in("api" / "agents" / "autopilot" / "focus").errorOut(errors).out(jsonBody[Autopilot.FocusInfo])

  lazy val setFocus: PublicEndpoint[Autopilot.FocusSet, Api.Error, Autopilot.FocusInfo, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "focus")
      .errorOut(errors)
      .in(jsonBody[Autopilot.FocusSet])
      .out(jsonBody[Autopilot.FocusInfo])

  private lazy val sched = endpoint.in("api" / "agents" / "scheduler").errorOut(errors)

  lazy val scheduler: PublicEndpoint[Option[Int], Api.Error, AgentScheduler.SchedulerStatus, Any] =
    sched.get.in(query[Option[Int]]("limit")).out(jsonBody[AgentScheduler.SchedulerStatus])

  lazy val setScheduler: PublicEndpoint[AgentScheduler.SchedulerSet, Api.Error, AgentScheduler.SchedulerStatus, Any] =
    sched.post.in(jsonBody[AgentScheduler.SchedulerSet]).out(jsonBody[AgentScheduler.SchedulerStatus])

  lazy val schedulerWhy: PublicEndpoint[String, Api.Error, AgentScheduler.WhyInfo, Any] =
    sched.get.in("why" / path[String]("item")).out(jsonBody[AgentScheduler.WhyInfo])

  lazy val digest: PublicEndpoint[Unit, Api.Error, ApprovalDigest.Digest, Any] =
    endpoint.get.in("api" / "agents" / "approvals" / "digest").errorOut(errors).out(jsonBody[ApprovalDigest.Digest])

  lazy val decideAll: PublicEndpoint[ApprovalDigest.BulkDecide, Api.Error, ApprovalDigest.BulkResult, Any] =
    endpoint.post
      .in("api" / "agents" / "approvals" / "decide")
      .errorOut(errors)
      .in(jsonBody[ApprovalDigest.BulkDecide])
      .out(jsonBody[ApprovalDigest.BulkResult])

  lazy val stalls: PublicEndpoint[Option[Int], Api.Error, Stalls.StallReport, Any] =
    endpoint.get
      .in("api" / "agents" / "stalls")
      .errorOut(errors)
      .in(query[Option[Int]]("days"))
      .out(jsonBody[Stalls.StallReport])

  /** The fixed-path endpoints come before `action`, whose last segment is a parameter. */
  /** The managers themselves, their items, hints and events (first of the agents' endpoints in `Api.all`). */
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
    enqueue
  )

  /** The steering of the managers: the focus, the scheduler, the approvals' digest (between the autopilot's switches
    * and the drain, in `Api.all`).
    */
  lazy val steering: List[AnyEndpoint] = List(
    focus,
    setFocus,
    scheduler,
    setScheduler,
    schedulerWhy,
    digest,
    decideAll
  )

  /** What has stalled (after the drain, in `Api.all`). */
  lazy val stalled: List[AnyEndpoint] = List(stalls)
