package com.histmersion.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract of the process managers (docs/research-process-managers.md §5): its own file
  * next to [[AgentsApi]] (which [[Api]] appends, like this one). Definitions only. */
object ManagersApi:
  import Managers.*

  /** 404 for a manager or item that does not exist, 409 for one in the wrong state, 400 otherwise. */
  private val errors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  private val base = endpoint.in("api" / "agents" / "managers").errorOut(errors)
  private val one  = base.in(path[String]("id"))

  /** `GET /api/agents/managers` — every manager with its counts, runs in flight and last event. */
  val list: PublicEndpoint[Unit, ApiError, List[ManagerInfo], Any] =
    base.get.out(jsonBody[List[ManagerInfo]])

  /** `POST /api/agents/managers` — a new manager (`review`, `enrich`, or `seed` with its goal; created paused). */
  val create: PublicEndpoint[ManagerCreate, ApiError, ManagerInfo, Any] =
    base.post.in(jsonBody[ManagerCreate]).out(jsonBody[ManagerInfo])

  /** `GET /api/agents/managers/{id}` — the manager, its items in flight, its hints, its newest events. */
  val get: PublicEndpoint[String, ApiError, ManagerDetail, Any] =
    one.get.out(jsonBody[ManagerDetail])

  /** `PUT /api/agents/managers/{id}` — its settings (replaced as given) and cron. */
  val update: PublicEndpoint[(String, ManagerUpdate), ApiError, ManagerInfo, Any] =
    one.put.in(jsonBody[ManagerUpdate]).out(jsonBody[ManagerInfo])

  /** `DELETE /api/agents/managers/{id}` — its runs in flight are cancelled first. */
  val delete: PublicEndpoint[String, ApiError, GoalPlans.Removed, Any] =
    one.delete.out(jsonBody[GoalPlans.Removed])

  /** `GET /api/agents/managers/{id}/items[?status=&offset=&limit=]` — the most urgent first. */
  val items: PublicEndpoint[(String, Option[String], Option[Int], Option[Int]), ApiError, ItemPage, Any] =
    one.get.in("items").in(query[Option[String]]("status")).in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("limit")).out(jsonBody[ItemPage])

  /** `POST /api/agents/managers/{id}/items` — add a target, or bring a settled one back. */
  val addItem: PublicEndpoint[(String, EnqueueRequest), ApiError, ManagerItem, Any] =
    one.post.in("items").in(jsonBody[EnqueueRequest]).out(jsonBody[ManagerItem])

  /** `PATCH /api/agents/managers/{id}/items/{item}` — hold, release, priority, skip, reopen (`item` =
    * its id or its target). */
  val patchItem: PublicEndpoint[(String, String, ItemPatch), ApiError, ManagerItem, Any] =
    one.patch.in("items" / path[String]("item")).in(jsonBody[ItemPatch]).out(jsonBody[ManagerItem])

  /** `GET /api/agents/managers/{id}/hints[?all=true]` — the live hints (all: the retired ones too). */
  val hints: PublicEndpoint[(String, Option[Boolean]), ApiError, List[ManagerHint], Any] =
    one.get.in("hints").in(query[Option[Boolean]]("all")).out(jsonBody[List[ManagerHint]])

  /** `POST /api/agents/managers/{id}/hints` — a hint; its structured effect is applied at once. */
  val addHint: PublicEndpoint[(String, HintAdd), ApiError, ManagerHint, Any] =
    one.post.in("hints").in(jsonBody[HintAdd]).out(jsonBody[ManagerHint])

  /** `DELETE /api/agents/managers/{id}/hints/{hint}` — retire it. */
  val removeHint: PublicEndpoint[(String, Long), ApiError, GoalPlans.Removed, Any] =
    one.delete.in("hints" / path[Long]("hint")).out(jsonBody[GoalPlans.Removed])

  /** `GET /api/agents/managers/{id}/events[?before=&limit=]` — newest first. */
  val events: PublicEndpoint[(String, Option[Long], Option[Int]), ApiError, EventPage, Any] =
    one.get.in("events").in(query[Option[Long]]("before")).in(query[Option[Int]]("limit")).out(jsonBody[EventPage])

  /** `POST /api/agents/managers/{id}/{pause|resume|stop}[?runs=true]`. */
  val action: PublicEndpoint[(String, String, Option[Boolean]), ApiError, ManagerInfo, Any] =
    one.post.in(path[String]("action")).in(query[Option[Boolean]]("runs")).out(jsonBody[ManagerInfo])

  /** `POST /api/agents/enqueue` — a page button: `review` or `extend` an article (the review manager),
    * `enrich` a source, or the unenriched sources an article cites or a goal links (the enrich
    * manager), `seed` a goal (its seeding manager proposes its next articles). A manager that does not
    * exist yet is created paused. */
  val enqueue: PublicEndpoint[EnqueueRequest, ApiError, EnqueueResult, Any] =
    endpoint.post.in("api" / "agents" / "enqueue").errorOut(errors).in(jsonBody[EnqueueRequest]).out(jsonBody[EnqueueResult])

  /** `GET /api/agents/autopilot` — the switch, and per research goal its stage, managers, counts, open
    * approvals, last activity and pace (docs/research-process-managers.md "As built: the autopilot"). */
  val autopilot: PublicEndpoint[Unit, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.get.in("api" / "agents" / "autopilot").errorOut(errors).out(jsonBody[Autopilot.AutopilotStatus])

  /** `POST /api/agents/autopilot {on}` — turn it on (every goal gets its managers, at once) or off (the
    * seeding managers and watchers it runs are paused). Idempotent; answers the status with what it did. */
  val setAutopilot: PublicEndpoint[Autopilot.AutopilotSet, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.post.in("api" / "agents" / "autopilot").errorOut(errors).in(jsonBody[Autopilot.AutopilotSet])
      .out(jsonBody[Autopilot.AutopilotStatus])

  /** The fixed-path endpoints come before `action`, whose last segment is a parameter. */
  val all: List[AnyEndpoint] = List(list, create, get, update, delete, items, addItem, patchItem, hints, addHint,
    removeHint, events, action, enqueue, autopilot, setAutopilot)
