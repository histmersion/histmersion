package com.histmersion.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract of the embedded agents (docs/research-embedded-agents.md §5.3). Its own file
  * next to [[Api]] (which appends `AgentsApi.all`), like [[OpsApi]]. Definitions only. */
object AgentsApi:
  import Agents.*

  private val base = endpoint.in("api" / "agents").errorOut(jsonBody[ApiError])

  /** 404 for a run that does not exist, 409 for a run in the wrong state (resume a finished one),
    * 400 for anything else (agents off, no model, an unpriced model, an unknown pipeline). */
  private val agentErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** The `ApiError.code` of a run that cannot make the asked transition. */
  val CodeConflict = "conflict"

  private val writeBase = endpoint.in("api" / "agents").errorOut(agentErrors)

  /** `GET /api/agents/models` — each role's model and whether it could run now (nothing is sent). */
  val models: PublicEndpoint[Unit, ApiError, ModelsInfo, Any] =
    base.get.in("models").out(jsonBody[ModelsInfo])

  /** `POST /api/agents/probe` — one live call with one read tool and a typed answer. */
  val probe: PublicEndpoint[ProbeRequest, ApiError, ProbeResult, Any] =
    writeBase.post.in("probe").in(jsonBody[ProbeRequest]).out(jsonBody[ProbeResult])

  /** `POST /api/agents/runs` — start a pipeline; answers the queued run at once. */
  val startRun: PublicEndpoint[RunRequest, ApiError, RunInfo, Any] =
    writeBase.post.in("runs").in(jsonBody[RunRequest]).out(jsonBody[RunInfo])

  /** `GET /api/agents/runs[?status=…&limit=…&goal=…]` — newest first; `goal` = the runs for one goal. */
  val runs: PublicEndpoint[(Option[String], Option[Int], Option[String]), ApiError, List[RunInfo], Any] =
    base.get.in("runs").in(query[Option[String]]("status")).in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("goal"))
      .out(jsonBody[List[RunInfo]])

  /** `GET /api/agents/runs/{id}` — the run, its checkpointed steps and its traced messages. */
  val run: PublicEndpoint[String, ApiError, RunDetail, Any] =
    endpoint.get.in("api" / "agents" / "runs" / path[String]("id")).errorOut(agentErrors).out(jsonBody[RunDetail])

  /** `POST /api/agents/runs/{id}/{pause|resume|cancel}`; a resume may raise the run's budget
    * (`?budgetUsd=&budgetTokens=`) — the way out of a budget pause. */
  val control: PublicEndpoint[(String, String, Option[Double], Option[Long]), ApiError, RunInfo, Any] =
    writeBase.post.in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd")).in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo])

  /** The verbs of [[control]]. */
  val Actions: List[String] = List("pause", "resume", "cancel")

  /** `GET /api/agents/approvals[?state=open|approved|rejected|failed&limit=…]` — newest first; the
    * default is the open ones. */
  val approvals: PublicEndpoint[(Option[String], Option[Int]), ApiError, List[ApprovalInfo], Any] =
    endpoint.get.in("api" / "agents" / "approvals").in(query[Option[String]]("state")).in(query[Option[Int]]("limit"))
      .errorOut(agentErrors).out(jsonBody[List[ApprovalInfo]])

  /** `POST /api/agents/approvals/{id}/{approve|reject}` — approving applies the change through the
    * same handler a person's patch takes; 409 when the approval was already decided. */
  val decide: PublicEndpoint[(Long, String, DecisionRequest), ApiError, ApprovalInfo, Any] =
    writeBase.post.in("approvals" / path[Long]("id") / path[String]("decision")).in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo])

  // ── goals: what hangs off a goal, its links and its schedules (P3) ──────────────────────────────

  import GoalPlans.*

  private val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(agentErrors)

  /** `GET /api/goals/{slug}/view` — the goal, its sub-goals, links, schedules and approved plan. */
  val goalView: PublicEndpoint[String, ApiError, GoalView, Any] =
    goalBase.get.in("view").out(jsonBody[GoalView])

  /** `POST /api/goals/{slug}/links` — link a source, an article, a search query or a queue item. */
  val addGoalLink: PublicEndpoint[(String, LinkAdd), ApiError, GoalLink, Any] =
    goalBase.post.in("links").in(jsonBody[LinkAdd]).out(jsonBody[GoalLink])

  /** `DELETE /api/goals/{slug}/links?kind=…&ref=…`. */
  val removeGoalLink: PublicEndpoint[(String, String, String), ApiError, Removed, Any] =
    goalBase.delete.in("links").in(query[String]("kind")).in(query[String]("ref")).out(jsonBody[Removed])

  /** `PUT /api/goals/{slug}/schedules/{currency|acquisition}` — create or change a schedule. */
  val setSchedule: PublicEndpoint[(String, String, ScheduleSet), ApiError, GoalSchedule, Any] =
    goalBase.put.in("schedules" / path[String]("kind")).in(jsonBody[ScheduleSet]).out(jsonBody[GoalSchedule])

  /** `DELETE /api/goals/{slug}/schedules/{kind}`. */
  val removeSchedule: PublicEndpoint[(String, String), ApiError, Removed, Any] =
    goalBase.delete.in("schedules" / path[String]("kind")).out(jsonBody[Removed])

  /** `GET /api/agents/schedules` — every goal's schedules, the next due first. */
  val schedules: PublicEndpoint[Unit, ApiError, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  val all: List[AnyEndpoint] = List(models, probe, startRun, runs, run, control, approvals, decide,
    goalView, addGoalLink, removeGoalLink, setSchedule, removeSchedule, schedules)
