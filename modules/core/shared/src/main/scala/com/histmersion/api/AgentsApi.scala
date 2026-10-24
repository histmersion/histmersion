package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract of the embedded agents (docs/research-embedded-agents.md §5.3). Its own file
  * next to [[Api]] (which appends `AgentsApi.all`), like [[OpsApi]]. Definitions only. */
object AgentsApi:
  import Agents.*

  private lazy val base = endpoint.in("api" / "agents").errorOut(jsonBody[ApiError])

  /** 404 for a run that does not exist, 409 for a run in the wrong state (resume a finished one),
    * 400 for anything else (agents off, no model, an unpriced model, an unknown pipeline). */
  private lazy val agentErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(AgentsApi.CodeConflict)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** The `ApiError.code` of a run that cannot make the asked transition. */
  val CodeConflict = "conflict"

  private lazy val writeBase = endpoint.in("api" / "agents").errorOut(agentErrors)

  /** `GET /api/agents/models` — each role's model and whether it could run now (nothing is sent). */
  lazy val models: PublicEndpoint[Unit, ApiError, ModelsInfo, Any] =
    base.get.in("models").out(jsonBody[ModelsInfo])

  /** `PUT /api/agents/models/{role}` — save a role's chain (first choice first) over config. */
  lazy val setChain: PublicEndpoint[(String, ModelChains.ChainSet), ApiError, ModelChains.RoleChain, Any] =
    writeBase.put.in("models" / path[String]("role")).in(jsonBody[ModelChains.ChainSet]).out(jsonBody[ModelChains.RoleChain])

  /** `DELETE /api/agents/models/{role}` — drop the saved chain; `agents.roles.<role>` applies again. */
  lazy val resetChain: PublicEndpoint[String, ApiError, ModelChains.RoleChain, Any] =
    writeBase.delete.in("models" / path[String]("role")).out(jsonBody[ModelChains.RoleChain])

  /** `PUT /api/agents/allowances` — save one model's or provider's (`claude-code:*`) allowance. */
  lazy val setAllowance: PublicEndpoint[ModelChains.AllowanceSet, ApiError, ModelChains.AllowanceStatus, Any] =
    writeBase.put.in("allowances").in(jsonBody[ModelChains.AllowanceSet]).out(jsonBody[ModelChains.AllowanceStatus])

  /** `DELETE /api/agents/allowances?target=…` — drop the saved allowance; config's applies again. */
  lazy val removeAllowance: PublicEndpoint[String, ApiError, GoalPlans.Removed, Any] =
    writeBase.delete.in("allowances").in(query[String]("target")).out(jsonBody[GoalPlans.Removed])

  /** `GET /api/agents/doctor[?offline=true]` — every provider checked without spending anything. */
  lazy val doctor: PublicEndpoint[Option[Boolean], ApiError, DoctorReport, Any] =
    base.get.in("doctor").in(query[Option[Boolean]]("offline")).out(jsonBody[DoctorReport])

  /** `POST /api/agents/probe` — one live call with one read tool and a typed answer. */
  lazy val probe: PublicEndpoint[ProbeRequest, ApiError, ProbeResult, Any] =
    writeBase.post.in("probe").in(jsonBody[ProbeRequest]).out(jsonBody[ProbeResult])

  /** `POST /api/agents/runs` — start a pipeline; answers the queued run at once. */
  lazy val startRun: PublicEndpoint[RunRequest, ApiError, RunInfo, Any] =
    writeBase.post.in("runs").in(jsonBody[RunRequest]).out(jsonBody[RunInfo])

  /** `GET /api/agents/runs[?status=…&pipeline=…&manager=…&goal=…&model=…&provider=…&origin=…&since=…&until=…&q=…&limit=…&before=…&upTo=…]`
    * — newest first, filtered on the server ([[ListFilters.RunFilter]]: a list field matches any of its
    * values, given repeated or comma-separated); paged by keyset like the jobs ([[Paging]]: cursors are
    * run ids; the total, next cursor, per-state counts and the pipeline / origin / manager / model /
    * provider facet counts are headers). */
  lazy val runs: PublicEndpoint[(ListFilters.RunFilter, Option[Int], Option[String], Option[String]), ApiError,
      (List[RunInfo], Paging.Page), Any] =
    base.get.in("runs").in(ListFilters.runQuery).in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before")).in(query[Option[String]]("upTo"))
      .out(jsonBody[List[RunInfo]]).out(Paging.headers)

  /** `GET /api/agents/runs/{id}` — the run, its checkpointed steps and its traced messages. */
  lazy val run: PublicEndpoint[String, ApiError, RunDetail, Any] =
    endpoint.get.in("api" / "agents" / "runs" / path[String]("id")).errorOut(agentErrors).out(jsonBody[RunDetail])

  /** `GET /api/agents/compare?id=…&id=…` — two or more runs of one pipeline on one target, side by
    * side: per step the model (configured → reported), tokens, cost or plan pool, time and attempts,
    * then the pipeline's outputs (#992). */
  lazy val compare: PublicEndpoint[List[String], ApiError, RunCompare.Comparison, Any] =
    endpoint.get.in("api" / "agents" / "compare").in(query[List[String]]("id")).errorOut(agentErrors)
      .out(jsonBody[RunCompare.Comparison])

  /** `POST /api/agents/runs/{id}/{pause|resume|cancel}`; a resume may raise the run's budget
    * (`?budgetUsd=&budgetTokens=`) — the way out of a budget pause. */
  lazy val control: PublicEndpoint[(String, String, Option[Double], Option[Long]), ApiError, RunInfo, Any] =
    writeBase.post.in("runs" / path[String]("id") / path[String]("action"))
      .in(query[Option[Double]]("budgetUsd")).in(query[Option[Long]]("budgetTokens"))
      .out(jsonBody[RunInfo])

  /** The verbs of [[control]]. */
  val Actions: List[String] = List("pause", "resume", "cancel")

  /** `GET /api/agents/approvals[?state=open|approved|rejected|failed|all&kind=…&subject=…&goal=…&decidedBy=…&limit=…&before=…&upTo=…]`
    * — newest first; the default is the open ones; filtered on the server ([[ListFilters.ApprovalFilter]]);
    * paged by keyset ([[Paging]]: cursors are approval ids; the total, next cursor, per-state counts and
    * the kind / decidedBy facet counts are headers). */
  lazy val approvals: PublicEndpoint[(ListFilters.ApprovalFilter, Option[Int], Option[String], Option[String]), ApiError,
      (List[ApprovalInfo], Paging.Page), Any] =
    endpoint.get.in("api" / "agents" / "approvals").in(ListFilters.approvalQuery).in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before")).in(query[Option[String]]("upTo"))
      .errorOut(agentErrors).out(jsonBody[List[ApprovalInfo]]).out(Paging.headers)

  /** `POST /api/agents/approvals/{id}/{approve|reject}` — approving applies the change through the
    * same handler a person's patch takes; 409 when the approval was already decided. */
  lazy val decide: PublicEndpoint[(Long, String, DecisionRequest), ApiError, ApprovalInfo, Any] =
    writeBase.post.in("approvals" / path[Long]("id") / path[String]("decision")).in(jsonBody[DecisionRequest])
      .out(jsonBody[ApprovalInfo])

  // ── goals: what hangs off a goal, its links and its schedules (P3) ──────────────────────────────

  import GoalPlans.*

  private lazy val goalBase = endpoint.in("api" / "goals" / path[String]("slug")).errorOut(agentErrors)

  /** `GET /api/goals/{slug}/view` — the goal, its sub-goals, links, schedules and approved plan. */
  lazy val goalView: PublicEndpoint[String, ApiError, GoalView, Any] =
    goalBase.get.in("view").out(jsonBody[GoalView])

  /** `POST /api/goals/{slug}/links` — link a source, an article, a search query or a queue item. */
  lazy val addGoalLink: PublicEndpoint[(String, LinkAdd), ApiError, GoalLink, Any] =
    goalBase.post.in("links").in(jsonBody[LinkAdd]).out(jsonBody[GoalLink])

  /** `DELETE /api/goals/{slug}/links?kind=…&ref=…`. */
  lazy val removeGoalLink: PublicEndpoint[(String, String, String), ApiError, Removed, Any] =
    goalBase.delete.in("links").in(query[String]("kind")).in(query[String]("ref")).out(jsonBody[Removed])

  /** `PUT /api/goals/{slug}/schedules/{currency|acquisition}` — create or change a schedule: a sweep of the
    * goal's watcher `watch:<goal>` (created paused), which owns the one cron (docs/research-process-managers.md). */
  lazy val setSchedule: PublicEndpoint[(String, String, ScheduleSet), ApiError, GoalSchedule, Any] =
    goalBase.put.in("schedules" / path[String]("kind")).in(jsonBody[ScheduleSet]).out(jsonBody[GoalSchedule])

  /** `DELETE /api/goals/{slug}/schedules/{kind}` — the sweep leaves the watcher (which stays). */
  lazy val removeSchedule: PublicEndpoint[(String, String), ApiError, Removed, Any] =
    goalBase.delete.in("schedules" / path[String]("kind")).out(jsonBody[Removed])

  /** `GET /api/agents/schedules` — every goal's schedules, the next due first. */
  lazy val schedules: PublicEndpoint[Unit, ApiError, List[GoalSchedule], Any] =
    base.get.in("schedules").out(jsonBody[List[GoalSchedule]])

  /** `POST /api/agents/generate` — write the per-runtime agent trees (server-side files). */
  lazy val generate: PublicEndpoint[GenerateRequest, ApiError, GenerateResult, Any] =
    writeBase.post.in("generate").in(jsonBody[GenerateRequest]).out(jsonBody[GenerateResult])

  /** `GET /api/agents/quota` — the plan pools (windows, floors, freshness, hold, reset) and the metered ones. */
  lazy val quota: PublicEndpoint[Unit, ApiError, PlanQuota.QuotaInfo, Any] =
    base.get.in("quota").out(jsonBody[PlanQuota.QuotaInfo])

  /** `PUT /api/agents/quota/{pool}` — hold or release a plan pool, and/or save its floors. */
  lazy val setPool: PublicEndpoint[(String, PlanQuota.PoolSet), ApiError, PlanQuota.PoolStatus, Any] =
    writeBase.put.in("quota" / path[String]("pool")).in(jsonBody[PlanQuota.PoolSet]).out(jsonBody[PlanQuota.PoolStatus])

  /** `PUT /api/agents/quota/fallthrough/{role}` — may the role's chain fall from a plan to a paid key. */
  lazy val setFallthrough: PublicEndpoint[(String, PlanQuota.FallthroughSet), ApiError, PlanQuota.QuotaInfo, Any] =
    writeBase.put.in("quota" / "fallthrough" / path[String]("role")).in(jsonBody[PlanQuota.FallthroughSet])
      .out(jsonBody[PlanQuota.QuotaInfo])

  /** `POST /api/agents/quota/refresh` — take a fresh reading of one plan pool (or every pool) now: the
    * ChatGPT plan through `codex app-server` (spends nothing), the Claude plan through its probe (one
    * minimal `claude -p` turn, unless `refresh = off`). */
  lazy val refreshQuota: PublicEndpoint[PlanQuota.QuotaRefresh, ApiError, PlanQuota.QuotaRefreshed, Any] =
    writeBase.post.in("quota" / "refresh").in(jsonBody[PlanQuota.QuotaRefresh]).out(jsonBody[PlanQuota.QuotaRefreshed])

  /** `POST /api/agents/quota/claude` — the Claude quota-guard mod's report; answers go on or stop. */
  lazy val claudeGuard: PublicEndpoint[PlanQuota.ModReport, ApiError, PlanQuota.GuardAnswer, Any] =
    writeBase.post.in("quota" / "claude").in(jsonBody[PlanQuota.ModReport]).out(jsonBody[PlanQuota.GuardAnswer])

  lazy val all: List[AnyEndpoint] = List(generate, quota, setFallthrough, refreshQuota, claudeGuard, setPool, models, setChain, resetChain, setAllowance, removeAllowance, doctor, probe, startRun, runs, run, compare, control, approvals, decide,
    goalView, addGoalLink, removeGoalLink, setSchedule, removeSchedule, schedules)
