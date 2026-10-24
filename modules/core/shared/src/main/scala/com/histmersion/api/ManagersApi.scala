package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract of the process managers (docs/research-process-managers.md §5): its own file
  * next to [[AgentsApi]] (which [[Api]] appends, like this one). Definitions only. */
object ManagersApi:
  import Managers.*

  /** 404 for a manager or item that does not exist, 409 for one in the wrong state, 400 otherwise. */
  private lazy val errors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  private lazy val base = endpoint.in("api" / "agents" / "managers").errorOut(errors)
  private lazy val one  = base.in(path[String]("id"))

  /** `GET /api/agents/managers` — every manager with its counts, runs in flight and last event. */
  lazy val list: PublicEndpoint[Unit, ApiError, List[ManagerInfo], Any] =
    base.get.out(jsonBody[List[ManagerInfo]])

  /** `POST /api/agents/managers` — a new manager (`review`, `enrich`, or `seed` with its goal; created paused). */
  lazy val create: PublicEndpoint[ManagerCreate, ApiError, ManagerInfo, Any] =
    base.post.in(jsonBody[ManagerCreate]).out(jsonBody[ManagerInfo])

  /** `GET /api/agents/managers/{id}` — the manager, its items in flight, its hints, its newest events. */
  lazy val get: PublicEndpoint[String, ApiError, ManagerDetail, Any] =
    one.get.out(jsonBody[ManagerDetail])

  /** `PUT /api/agents/managers/{id}` — its settings (replaced as given) and cron. */
  lazy val update: PublicEndpoint[(String, ManagerUpdate), ApiError, ManagerInfo, Any] =
    one.put.in(jsonBody[ManagerUpdate]).out(jsonBody[ManagerInfo])

  /** `DELETE /api/agents/managers/{id}` — its runs in flight are cancelled first. */
  lazy val delete: PublicEndpoint[String, ApiError, GoalPlans.Removed, Any] =
    one.delete.out(jsonBody[GoalPlans.Removed])

  /** `GET /api/agents/managers/{id}/items[?status=&offset=&limit=]` — the most urgent first. */
  lazy val items: PublicEndpoint[(String, Option[String], Option[Int], Option[Int]), ApiError, ItemPage, Any] =
    one.get.in("items").in(query[Option[String]]("status")).in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("limit")).out(jsonBody[ItemPage])

  /** `POST /api/agents/managers/{id}/items` — add a target, or bring a settled one back. */
  lazy val addItem: PublicEndpoint[(String, EnqueueRequest), ApiError, ManagerItem, Any] =
    one.post.in("items").in(jsonBody[EnqueueRequest]).out(jsonBody[ManagerItem])

  /** `PATCH /api/agents/managers/{id}/items/{item}` — hold, release, priority, skip, reopen (`item` =
    * its id or its target). */
  lazy val patchItem: PublicEndpoint[(String, String, ItemPatch), ApiError, ManagerItem, Any] =
    one.patch.in("items" / path[String]("item")).in(jsonBody[ItemPatch]).out(jsonBody[ManagerItem])

  /** `GET /api/agents/managers/{id}/hints[?all=true]` — the live hints (all: the retired ones too). */
  lazy val hints: PublicEndpoint[(String, Option[Boolean]), ApiError, List[ManagerHint], Any] =
    one.get.in("hints").in(query[Option[Boolean]]("all")).out(jsonBody[List[ManagerHint]])

  /** `POST /api/agents/managers/{id}/hints` — a hint; its structured effect is applied at once. */
  lazy val addHint: PublicEndpoint[(String, HintAdd), ApiError, ManagerHint, Any] =
    one.post.in("hints").in(jsonBody[HintAdd]).out(jsonBody[ManagerHint])

  /** `DELETE /api/agents/managers/{id}/hints/{hint}` — retire it. */
  lazy val removeHint: PublicEndpoint[(String, Long), ApiError, GoalPlans.Removed, Any] =
    one.delete.in("hints" / path[Long]("hint")).out(jsonBody[GoalPlans.Removed])

  /** `GET /api/agents/managers/{id}/events[?before=&limit=]` — newest first. */
  lazy val events: PublicEndpoint[(String, Option[Long], Option[Int]), ApiError, EventPage, Any] =
    one.get.in("events").in(query[Option[Long]]("before")).in(query[Option[Int]]("limit")).out(jsonBody[EventPage])

  /** `POST /api/agents/managers/{id}/{pause|resume|stop}[?runs=true]`. */
  lazy val action: PublicEndpoint[(String, String, Option[Boolean]), ApiError, ManagerInfo, Any] =
    one.post.in(path[String]("action")).in(query[Option[Boolean]]("runs")).out(jsonBody[ManagerInfo])

  /** `POST /api/agents/enqueue` — a page button: `review` or `extend` an article (the review manager),
    * `enrich` a source, or the unenriched sources an article cites or a goal links (the enrich
    * manager), `seed` a goal (its seeding manager proposes its next articles). A manager that does not
    * exist yet is created paused. */
  lazy val enqueue: PublicEndpoint[EnqueueRequest, ApiError, EnqueueResult, Any] =
    endpoint.post.in("api" / "agents" / "enqueue").errorOut(errors).in(jsonBody[EnqueueRequest]).out(jsonBody[EnqueueResult])

  /** `GET /api/agents/autopilot` — the switch, and per research goal its stage, managers, counts, open
    * approvals, last activity and pace (docs/research-process-managers.md "As built: the autopilot"). */
  lazy val autopilot: PublicEndpoint[Unit, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.get.in("api" / "agents" / "autopilot").errorOut(errors).out(jsonBody[Autopilot.AutopilotStatus])

  /** `POST /api/agents/autopilot {on}` — turn it on (every goal gets its managers, at once) or off (the
    * seeding managers and watchers it runs are paused). Idempotent; answers the status with what it did. */
  lazy val setAutopilot: PublicEndpoint[Autopilot.AutopilotSet, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.post.in("api" / "agents" / "autopilot").errorOut(errors).in(jsonBody[Autopilot.AutopilotSet])
      .out(jsonBody[Autopilot.AutopilotStatus])

  /** `POST /api/agents/autopilot/repair-acquire {dryRun}` — the one-shot repair: the open acquisition
    * items a watcher's sweep created for a legal open-access work move into that watcher's ingest backlog
    * (and leave the list); the ones already in the corpus leave it; nothing a person added is touched.
    * `dryRun` lists what it would do. */
  lazy val repairAcquire: PublicEndpoint[RepairAcquire.RepairRequest, ApiError, RepairAcquire.RepairResult, Any] =
    endpoint.post.in("api" / "agents" / "autopilot" / "repair-acquire").errorOut(errors)
      .in(jsonBody[RepairAcquire.RepairRequest]).out(jsonBody[RepairAcquire.RepairResult])

  /** `GET /api/agents/autopilot/focus` — the focus (docs/research-process-managers.md "As built: the
    * focus"): the active goals (why each was chosen, what it does or waits for, its pace), the queue in the
    * order the next become active, and the goals that cannot use a slot now. Readable with the autopilot
    * off (what it would focus on). */
  lazy val focus: PublicEndpoint[Unit, ApiError, Autopilot.FocusInfo, Any] =
    endpoint.get.in("api" / "agents" / "autopilot" / "focus").errorOut(errors).out(jsonBody[Autopilot.FocusInfo])

  /** `POST /api/agents/autopilot/focus {action, goal?, value?}` — `pin` / `unpin` a goal, its `priority`,
    * or the cap (`max`; no value: back to agents.autopilot.max-active-goals). Answers the focus as it is
    * then (the next managers' tick applies it). */
  lazy val setFocus: PublicEndpoint[Autopilot.FocusSet, ApiError, Autopilot.FocusInfo, Any] =
    endpoint.post.in("api" / "agents" / "autopilot" / "focus").errorOut(errors).in(jsonBody[Autopilot.FocusSet])
      .out(jsonBody[Autopilot.FocusInfo])

  private lazy val sched = endpoint.in("api" / "agents" / "scheduler").errorOut(errors)

  /** `GET /api/agents/scheduler[?limit=]` — the central scheduler (docs/research-goal-advancement.md "As
    * built"): the strategy, the resources, the last pass's starts and the queue — the first `limit` waiting
    * items (default 50), each with why it waits and what it waits behind. Readable with agents off (what a
    * pass would do now). */
  lazy val scheduler: PublicEndpoint[Option[Int], ApiError, AgentScheduler.SchedulerStatus, Any] =
    sched.get.in(query[Option[Int]]("limit")).out(jsonBody[AgentScheduler.SchedulerStatus])

  /** `POST /api/agents/scheduler {strategy}` — switch the strategy live (kept in the database; `default`
    * goes back to agents.scheduler.strategy). Answers the status as the next pass will decide it. */
  lazy val setScheduler: PublicEndpoint[AgentScheduler.SchedulerSet, ApiError, AgentScheduler.SchedulerStatus, Any] =
    sched.post.in(jsonBody[AgentScheduler.SchedulerSet]).out(jsonBody[AgentScheduler.SchedulerStatus])

  /** `GET /api/agents/scheduler/why/{item}` — why one manager item (its id, or a target among the open
    * items) is where it is: waiting (behind what), running, waiting for a person, held, settled. */
  lazy val schedulerWhy: PublicEndpoint[String, ApiError, AgentScheduler.WhyInfo, Any] =
    sched.get.in("why" / path[String]("item")).out(jsonBody[AgentScheduler.WhyInfo])

  /** `GET /api/agents/approvals/digest` — everything waiting for a person: the open approvals by kind (how
    * many, the oldest, who decides them now, the subjects, the ids) and the items held for a person. */
  lazy val digest: PublicEndpoint[Unit, ApiError, ApprovalDigest.Digest, Any] =
    endpoint.get.in("api" / "agents" / "approvals" / "digest").errorOut(errors).out(jsonBody[ApprovalDigest.Digest])

  /** `POST /api/agents/approvals/decide {decision, kind?, subject?, ids?, note?, dryRun?, limit?}` — decide every
    * open approval that matches at once, each through the single decision (a refused one does not stop the rest). */
  lazy val decideAll: PublicEndpoint[ApprovalDigest.BulkDecide, ApiError, ApprovalDigest.BulkResult, Any] =
    endpoint.post.in("api" / "agents" / "approvals" / "decide").errorOut(errors).in(jsonBody[ApprovalDigest.BulkDecide])
      .out(jsonBody[ApprovalDigest.BulkResult])

  /** `POST /api/agents/autopilot/drain {on}` — drain: nothing new starts while the runs in flight finish (the
    * status says when it is drained: safe to stop the server); `on: false` lifts it. */
  lazy val drain: PublicEndpoint[Autopilot.DrainSet, ApiError, Autopilot.AutopilotStatus, Any] =
    endpoint.post.in("api" / "agents" / "autopilot" / "drain").errorOut(errors).in(jsonBody[Autopilot.DrainSet])
      .out(jsonBody[Autopilot.AutopilotStatus])

  /** `GET /api/agents/stalls[?days=]` — the unfinished goals with no progress in `days` days (default
    * agents.autopilot.stall-days), each with the reason it stands still and the action that unblocks it. */
  lazy val stalls: PublicEndpoint[Option[Int], ApiError, Stalls.StallReport, Any] =
    endpoint.get.in("api" / "agents" / "stalls").errorOut(errors).in(query[Option[Int]]("days")).out(jsonBody[Stalls.StallReport])

  /** The fixed-path endpoints come before `action`, whose last segment is a parameter. */
  lazy val all: List[AnyEndpoint] = List(list, create, get, update, delete, items, addItem, patchItem, hints, addHint,
    removeHint, events, action, enqueue, autopilot, setAutopilot, repairAcquire, focus, setFocus, scheduler, setScheduler,
    schedulerWhy, digest, decideAll, drain, stalls)
