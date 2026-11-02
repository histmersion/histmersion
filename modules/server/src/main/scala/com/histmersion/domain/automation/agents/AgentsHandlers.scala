package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.approvals.{ApprovalDigestService, Approvals, AutoApprovals}
import com.histmersion.domain.automation.autopilot.AutopilotService
import com.histmersion.domain.automation.goals.GoalFocusService
import com.histmersion.domain.automation.managers.{DrainService, ManagersHandlers, ManagerSupervisor, Zero}
import com.histmersion.domain.automation.scheduler.SchedulerService
import com.histmersion.server.agents.{AgentDoctor, BuiltinAi, ClaudeCode, CliRuntime, Codex, Opencode, PlanPools}

import com.histmersion.domain.automation.worklists.QueueItemId

import com.histmersion.domain.automation.agents.VerbatimFailure
import com.histmersion.domain.automation.workflows.{ToolName, Workflows}
import hearth.kindlings.optics.syntax.*
import com.histmersion.pluginhost.HostAiHttp

import com.histmersion.util.database.Transactor
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.GoalPlans.*
import com.histmersion.db.ModelSettingsRepo

/** The ONE implementation of the embedded-agent operations (docs/research-embedded-agents.md): the model check, the
  * probe and (through [[Engine]]) pipeline runs. REST ([[AgentsRoutes]]) and the CLI reach it; it is held as
  * `Handlers.agents`.
  *
  * `clientFor` is the seam a test uses to put sttp-ai's scripted model in place of a provider; `planSeams` how the plan
  * pools are read on purpose ([[PlanPools.Seams]]).
  */
final class AgentsHandlers(
    site: EngineSite,
    clientFor: LlmClient.Factory,
    val cli: CliRuntime,
    planSeams: PlanPools.Seams
):

  /** A test's factory (sttp-ai's scripted model) over the server's own CLI context. No CLI is started to read a plan
    * pool (a scripted model's test must never spend a real plan on a probe).
    */
  def this(site: EngineSite, clientFor: LlmClient.Factory) =
    this(site, clientFor, CliRuntime(site.config, site.roster), PlanPools.Seams.Offline)

  private def this(site: EngineSite, w: (LlmClient.Factory, CliRuntime)) =
    this(site, w._1, w._2, PlanPools.Seams.Real)

  /** The server's own providers ([[LlmClient.factory]]: the ones registered in the pinned plugin generation — the HTTP
    * ones, the CLI runtimes and any plugin's).
    */
  def this(site: EngineSite) = this(site, AgentsHandlers.serverWiring(site))

  /** Read at each use: a config reload switches it (Deps). */
  private def cfg = site.config.agents

  /** The agents' settings now (read at each use). */
  def config: AgentsConfig = cfg

  /** How many sources a goal gathers before its articles are planned (the site's, read at each use). */
  def sourcesToPlan: Int = site.sourcesToPlan
  private val ds = site.ds

  /** The goals: their sub-goals, links, plans and the schedules not moved into a watcher yet. */
  val goals: com.histmersion.domain.automation.goals.GoalService =
    com.histmersion.domain.automation.goals.GoalService(Transactor(ds))

  /** The agent runs and their steps, as the engine's features read them. */
  val runs: RunService = RunService(Transactor(ds))

  /** The process managers, their items and events, as the engine's other features reach them. */
  val managerStore: com.histmersion.domain.automation.managers.ManagerService =
    com.histmersion.domain.automation.managers.ManagerService(Transactor(ds))

  /** What the focus records of every goal, and its reads over them. */
  val focusStore: com.histmersion.domain.automation.goals.FocusService =
    com.histmersion.domain.automation.goals.FocusService(Transactor(ds))

  /** The approvals: what runs proposed for a person to decide. */
  val approvalStore: com.histmersion.domain.automation.agents.ApprovalService =
    com.histmersion.domain.automation.agents.ApprovalService(Transactor(ds), site.clock)

  /** The autopilot's row: its switch, housekeeping times, the scheduler's strategy, the digest's day, the drain. */
  val autopilotRow: com.histmersion.domain.automation.autopilot.AutopilotRowService =
    com.histmersion.domain.automation.autopilot.AutopilotRowService(Transactor(ds))

  /** What the engine asks of the rest of the server (goals, the housekeeping) until the owners offer services. */
  val host: com.histmersion.domain.automation.workflows.AutomationHost = site.host(this)

  /** The work lists' operations as a person's go: the worklog lines of the engine's services. */
  val workLists: com.histmersion.domain.automation.worklists.WorkListControllers = site.workLists

  /** The runs' rows, recorded through the jobs area that owns the `jobs` table. */
  val jobRuns: com.histmersion.util.jobs.JobRuns = com.histmersion.util.jobs.JobRuns(Transactor(ds))

  /** The work lists' kernel (the runs' log lines, the review's tasks). */
  val queues: com.histmersion.domain.automation.worklists.QueueService = site.queues

  /** How the engine, the managers and their threads keep work on the generation it started on. */
  val pins: com.histmersion.util.jobs.Pinning = site.pins

  /** A person's pause and cancel of the server's long background jobs. */
  val jobStops: com.histmersion.util.jobs.JobStops = site.jobStops

  /** The time the managers' tick, the autopilot, the scheduler and the digest decide by. */
  val clock: java.time.Clock = site.clock

  /** What the daily approval digest also reads the hour on ([[EngineSite.digestClock]]). */
  val digestClock: Option[java.time.Clock] = site.digestClock

  val definitions: AgentDefinitions = cli.definitions
  val tokens: RunTokens = cli.tokens

  /** The subscription pools (also what the CLI clients report their readings to). */
  val pools: PlanPools = PlanPools(ds, cfg, () => cli.trees.codexHome, planSeams)
  cli.pools = Some(pools)

  /** A `hint-effect` approval a person approves is applied by the managers (read when it happens, so the forward
    * reference to [[managing]] is safe).
    */
  val approvals: Approvals = Approvals(approvalStore, cfg, site.approvalEffects, p => managing.applyProposal(p))

  /** What the domains register into the engine: their workflows and agent tools. A registration the engine refuses (two
    * workflows with one identifier, a tool nobody contributes) stops the server here.
    */
  val workflows: Workflows =
    Workflows.of(site.contributions(this)).fold(m => throw IllegalStateException(s"workflows: $m"), identity)

  /** A workflow whose roles the definitions were not told of would run agents nobody may define: refused here. */
  private val unrostered =
    workflows.definitions.flatMap(_.roles).distinct.filterNot(definitions.roster.roles.contains)
  if unrostered.nonEmpty then
    throw IllegalStateException(
      s"workflows: roles the agent definitions do not know: ${unrostered.map(_.wire).mkString(", ")}"
    )

  /** The contributed agent tools, by name. */
  val toolbox: Toolbox = Toolbox(workflows.tools)

  /** The run pool. Constructed here, started by [[start]] (from `Main`, after the OCR pool). */
  val engine: Engine = Engine(ds, cfg, this, workflows)

  /** The process managers' supervisor (started with the pool), nudged whenever a run settles. */
  val managers: ManagerSupervisor = ManagerSupervisor(ds, cfg, this, site.workLists, workflows.managerKinds)
  engine.onSettled(managers.settled)

  /** The operations on the managers (REST, MCP, CLI). */
  val managing: ManagersHandlers = ManagersHandlers(ds, cfg, site.config.limits, this, managers, host)

  /** The checked auto approvals the managers' tick runs (article-plan, goal-plan, article-publish). */
  val autoApprovals: AutoApprovals = AutoApprovals(ds, cfg, this)

  /** The autopilot: one switch that lets the managers carry every goal from planned to reviewed. */
  val autopilot: AutopilotService = AutopilotService(ds, cfg, this)

  /** The autopilot's focus: how the goals rank (its pins and priorities are the goals' priorities in the scheduler); it
    * holds nothing since the central scheduler admits the work.
    */
  val focus: GoalFocusService = GoalFocusService(ds, cfg, this)

  /** The central scheduler: which of the managers' pending items start a run, across every job type. */
  val scheduler: SchedulerService = SchedulerService(ds, cfg, this)

  /** The approval digest: everything waiting for a person, a daily worklog line, a group decided at once. */
  val digest: ApprovalDigestService = ApprovalDigestService(ds, cfg, this)

  /** Drain mode: nothing new starts while the runs in flight finish (a deploy). */
  val drain: DrainService = DrainService(ds, cfg, this)

  /** One agent server per database: the engine and the managers start only in the server that holds it (ttl 3 × the
    * managers' tick).
    */
  val lease: AgentLease = AgentLease(ds, 3 * math.max(5, cfg.managers.tickSeconds))

  /** Start the run pool and the managers — only when agents are enabled, and only once this server holds the agent
    * lease (a second server on the same database waits for it to be free); off, no thread is started and queued or
    * paused runs simply wait. Every start first moves the goal schedules left into their goals' watchers (created
    * paused; idempotent — agents on or off). Returns how many interrupted runs were paused (0 when the lease is held
    * elsewhere).
    */
  def start(): Int =
    val _ = managers.migrateSchedules(java.time.Instant.now(clock))
    if !cfg.enabled then 0
    else
      if cfg.runtimes.generate then
        generate(GenerateRequest()) match
          case Right(r) =>
            scribe.info(
              s"agent runtimes generated in ${r.dir}: ${r.files} file(s), ${r.written} changed, ${r.removed} removed"
            )
          case Left(e) => scribe.warn(s"agent runtimes not generated: ${e.message}")
      managers.ensureDefaults()
      val paused = java.util.concurrent.atomic.AtomicInteger(0)
      lease.start { () =>
        paused.set(engine.start())
        managers.start()
      }
      paused.get

  // ── the generated runtime trees ────────────────────────────────────────────────────────────────

  /** The MCP tools `/mcp` advertises — what a placeholder may name. A `var` because the tool list is built after the
    * handlers ([[com.histmersion.server.McpTools]] needs them); `Main` attaches it before [[start]]. Until then, the
    * agent tools.
    */
  @volatile private var knownTools: Set[String] = toolbox.names.map(_.value).toSet

  def attachTools(names: Set[String]): Unit = knownTools = names ++ toolbox.names.map(_.value)

  /** `POST /api/agents/generate` — works with agents off too: the interactive plugin the research workspace installs is
    * one of the trees.
    */
  def generate(req: GenerateRequest): Either[ApiError, GenerateResult] =
    for
      rts <- req.runtimes
        .map(r => AgentRuntime.parse(r).filter(AgentRuntime.Generated.contains).toRight(r))
        .partitionMap(identity) match
        case (Nil, ok) => Right(if ok.isEmpty then AgentRuntime.Generated else ok)
        case (bad, _)  =>
          Left(
            ApiError(
              s"unknown runtime(s) ${bad.mkString(", ")} (one of: ${AgentRuntime.Generated.map(_.wire).mkString(", ")})"
            )
          )
      r <- cli.trees.generate(knownTools, rts, AgentTrees.out(req.out)).left.map(ApiError(_))
    yield r

  // ── plan pools ─────────────────────────────────────────────────────────────────────────────────

  private def covers(p: PlanQuota.PoolId): List[String] = p match
    case PlanQuota.PoolId.ClaudePlan  => List(s"${ProviderKind.ClaudeCode.wire}:*")
    case PlanQuota.PoolId.ChatgptPlan =>
      s"${ProviderKind.Codex.wire}:*" :: Option
        .when(cfg.providers.opencode.chatgptLogin)(s"${ProviderKind.Opencode.wire}:openai/*")
        .toList

  /** `GET /api/agents/quota` — readable with agents off. Nothing is spent: the ChatGPT pool is read from the app-server
    * (throttled), the Claude pool from what runs and mods reported.
    */
  def quota: PlanQuota.QuotaInfo =
    val now = System.currentTimeMillis()
    PlanQuota.QuotaInfo(
      pools.statuses(covers, now),
      picker.allowanceStatuses(now),
      Role.values.toList.filter(picker.fallthrough).map(_.wire),
      java.time.Instant.ofEpochMilli(now).toString,
      picker.cooldowns(now).map((m, u) => PlanQuota.Cooldown(m, u.toString, ModelPicker.CoolingWhy))
    )

  private def poolOf(raw: String): Either[ApiError, PlanQuota.PoolId] =
    PlanQuota.PoolId
      .parse(raw)
      .toRight(ApiError(s"unknown pool '$raw' (one of: ${PlanQuota.PoolId.values.map(_.wire).mkString(", ")})"))

  /** `PUT /api/agents/quota/{pool}` — the hold switch and the floors. */
  def setPool(pool: String, in: PlanQuota.PoolSet): Either[ApiError, PlanQuota.PoolStatus] =
    for
      p <- poolOf(pool)
      f <- in.floors.map(_.trim) match
        case Some("") => Right(Some(None))
        case Some(t)  => PlanQuota.Floors.parse(t).left.map(ApiError(_)).map(fs => Some(Some(fs.text)))
        case None     => Right(None)
      _ = in.hold.foreach(pools.hold(p, _))
      _ = f.foreach(pools.setFloors(p, _))
      st <- pools.statuses(covers).find(_.pool == p.wire).toRight(ApiError(s"no pool ${p.wire}"))
    yield st

  /** `PUT /api/agents/quota/fallthrough/{role}`. */
  def setFallthrough(role: String, in: PlanQuota.FallthroughSet): Either[ApiError, PlanQuota.QuotaInfo] =
    roleOf(role).map { r =>
      com.histmersion.db.PlanQuotaRepo.setFallthrough(ds, r.wire, in.fallthrough)
      quota
    }

  /** `POST /api/agents/quota/refresh` — a fresh reading of one pool (None: every pool) now, whatever its age: the
    * ChatGPT plan from the app-server (free), the Claude plan from its probe (one minimal `claude -p` turn) unless
    * `refresh = off`. The login is checked again first; a pool without one is not read.
    */
  def refreshQuota(in: PlanQuota.QuotaRefresh): Either[ApiError, PlanQuota.QuotaRefreshed] =
    in.pool
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(poolOf(_).map(List(_)))
      .getOrElse(Right(PlanQuota.PoolId.values.toList))
      .map { ps =>
        val cooled = picker.cooldowns().map(_._1)
        val done = ps.map(p => p -> pools.refreshPool(p, PlanQuota.RefreshTrigger.Command, force = true))
        val after = pools.statuses(covers).map(st => st.pool -> st).toMap
        // a model parked after its plan's limit comes back as soon as a reading taken now says so (#1827)
        val still = picker.cooldowns().map(_._1).toSet
        PlanQuota.QuotaRefreshed(
          done.flatMap { (p, r) =>
            after
              .get(p.wire)
              .map(st =>
                r match
                  case Right(x)  => PlanQuota.PoolRefreshed(p.wire, x.ok, x.text, st)
                  case Left(why) => PlanQuota.PoolRefreshed(p.wire, refreshed = false, s"not read: $why", st)
              )
          },
          cooled.filterNot(still)
        )
      }

  /** `POST /api/agents/quota/claude` — a mod's report, answered with go on or stop. */
  def guard(r: PlanQuota.ModReport): PlanQuota.GuardAnswer = pools.guard(r)

  /** Stop the managers and the pool (tests; the server process just exits). */
  def stop(): Unit =
    managers.stop()
    engine.stop()
    lease.stop()

  /** A config reload swapped the database and this server holds the agent lease on the new one
    * (docs/research-plugins.md §3.6): do there what a start does — the goal schedules moved, the default managers made,
    * interrupted runs paused and queued ones enqueued (the engine and the managers keep their threads; their next unit
    * of work runs on the new database).
    */
  def adoptDatabase(): Unit =
    if cfg.enabled then
      val _ = managers.migrateSchedules(java.time.Instant.now(clock))
      managers.ensureDefaults()
      val paused = engine.start()
      managers.start()
      scribe.info(s"agents: running on the new database ($paused interrupted run(s) paused there)")

  /** Off unless `agents.enabled`: every agent operation but the read-only model listing refuses. */
  private def enabled: Either[ApiError, Unit] =
    if cfg.enabled then Right(())
    else Left(ApiError("embedded agents are off — set HISTMERSION_AGENTS_ENABLED=true on the server to use them"))

  /** The price table, parsed once per call (config is immutable; parsing is microseconds). */
  private def prices: Either[String, Map[String, Price]] =
    Agents.Prices.parse(cfg.prices).left.map(m => s"agents.prices: $m")

  /** A model that can run: on a plan pool or PRICED (an unpriced metered model is refused, never treated as free), and
    * with a client its provider can build (a hosted provider needs its key; `claude-code` a loopback server).
    */
  def resolve(m: ModelRef): Either[String, (LlmClient, Billing)] =
    for
      ps <- prices
      b <- Agents.Prices.billing(ps, m, pools.poolOf(m))
      c <- clientFor(cfg, m)
    yield (c, b)

  /** The names plugin AI providers are registered under in the pinned generation — what a `provider:model` may name
    * besides the host's own (docs/research-plugins.md §22).
    */
  /** The client plugin AI providers' doctor checks and model listings go through. */
  private val aiHttp: com.histmersion.plugin.AiHttp = HostAiHttp()

  def knownProviders: Set[String] = BuiltinAi.known(site.aiProviders())

  /** The model chains and allowances in effect, and the choice before every LLM call. */
  val picker: ModelPicker = ModelPicker(ds, cfg, resolve, pools, () => knownProviders)

  /** A role's chain can run at all: at least one of its models is on a plan or priced, and buildable (allowances are
    * not looked at — a run whose models are all spent queues and pauses until a window frees).
    */
  def runnable(role: Role, overrideRef: Option[String]): Either[String, Unit] =
    picker.chainFor(role, overrideRef).flatMap { chain =>
      val tried = chain.map(m => m -> resolve(m))
      if tried.exists(_._2.isRight) then Right(())
      else
        Left(
          s"no model of the ${role.wire} chain can run: " + tried
            .collect { case (m, Left(w)) => s"${m.wire}: $w" }
            .mkString("; ")
        )
    }

  /** `GET /api/agents/models` — each role's chain, the model it would use now, and every allowance's use per window.
    * Sends nothing to a provider: clients are constructed, spends summed.
    */
  def models: ModelsInfo = models(System.currentTimeMillis())

  def models(now: Long): ModelsInfo =
    val chains = picker.roleChains(now)
    val roles = chains.map { c =>
      val shown = c.active.orElse(c.models.headOption.map(_.model))
      val entry = c.models.find(e => shown.contains(e.model))
      RoleModel(
        c.role,
        shown,
        ready = c.active.isDefined,
        if c.active.isDefined then None
        else
          c.problem.orElse(
            Option.when(c.models.nonEmpty)(c.models.flatMap(e => e.problem.map(p => s"${e.model}: $p")).mkString("; "))
          )
      )
    }
    val modes = List("status-change" -> cfg.approvals.statusChange, "edges" -> cfg.approvals.edges).collect {
      case (k, v) if ApprovalMode.parse(v).isEmpty =>
        s"agents.approvals.$k = '$v' is neither manual nor auto; treated as manual"
    }
    val modes2 = Option
      .when(ApprovalMode.parse(cfg.approvals.articlePublish).isEmpty)(
        s"agents.approvals.article-publish = '${cfg.approvals.articlePublish}' is neither manual nor auto; treated as manual"
      )
      .toList
    val problems = prices.left.toOption.toList ++ modes ++ modes2 ++ picker.allowances._2 ++
      Option.when(!cfg.enabled)("embedded agents are off (agents.enabled = false)")
    ModelsInfo(
      cfg.enabled,
      roles,
      Pipeline.values.toList.map(_.wire),
      cfg.maxConcurrentRuns,
      problems,
      chains,
      picker.allowanceStatuses(now),
      java.time.Instant.ofEpochMilli(now).toString
    )

  // ── chains and allowances: saved over config ───────────────────────────────────────────────────

  private def roleOf(raw: String): Either[ApiError, Role] =
    Role.parse(raw).toRight(ApiError(s"unknown role '$raw' (one of: ${Role.values.map(_.wire).mkString(", ")})"))

  private def chainOf(role: Role): Either[ApiError, ModelChains.RoleChain] =
    picker.roleChains(System.currentTimeMillis()).find(_.role == role.wire).toRight(ApiError(s"no role ${role.wire}"))

  /** `PUT /api/agents/models/{role}` — the role's chain, saved over `agents.roles.<role>`. Every model must be a
    * `provider:model`; one that is not priced or buildable yet is accepted and shown as not ready (a price or key may
    * come next). Works with agents off.
    */
  def setChain(role: String, in: ModelChains.ChainSet): Either[ApiError, ModelChains.RoleChain] =
    for
      r <- roleOf(role)
      ms <- ModelChains.parseModels(in.models, knownProviders).left.map(ApiError(_))
      _ <- Either.cond(
        ms.nonEmpty,
        (),
        ApiError("a chain needs at least one provider:model (reset the role to go back to config)")
      )
      _ = ModelSettingsRepo.setChain(ds, r.wire, ms.map(_.wire))
      c <- chainOf(r)
    yield c

  /** `DELETE /api/agents/models/{role}` — back to `agents.roles.<role>`. */
  def resetChain(role: String): Either[ApiError, ModelChains.RoleChain] =
    for
      r <- roleOf(role)
      _ = ModelSettingsRepo.removeChain(ds, r.wire)
      c <- chainOf(r)
    yield c

  /** `PUT /api/agents/allowances` — one model's or provider's limits and reserve, saved over config. */
  def setAllowance(in: ModelChains.AllowanceSet): Either[ApiError, ModelChains.AllowanceStatus] =
    for
      t <- ModelChains.Target.parse(in.target, knownProviders).left.map(ApiError(_))
      ls <- in.limits.map(ModelChains.Limit.fromSpec).partitionMap(identity) match
        case (Nil, ok) => Right(ok)
        case (errs, _) => Left(ApiError(errs.mkString("; ")))
      a <- ModelChains.Allowance.of(t, ls, in.reservePct).left.map(ApiError(_))
      _ = ModelSettingsRepo.setAllowance(ds, t.wire, a.limits.map(_.spec), a.reservePct)
      st <- picker
        .allowanceStatuses(System.currentTimeMillis())
        .find(_.target == t.wire)
        .toRight(ApiError(s"the allowance of ${t.wire} was not recorded"))
    yield st

  /** `DELETE /api/agents/allowances?target=…` — the saved allowance goes; config's (if any) applies again. */
  def removeAllowance(target: String): Either[ApiError, GoalPlans.Removed] =
    ModelChains.Target
      .parse(target, knownProviders)
      .left
      .map(ApiError(_))
      .map(t => GoalPlans.Removed(ModelSettingsRepo.removeAllowance(ds, t.wire)))

  /** `GET /api/agents/doctor` — every provider checked without spending anything. */
  def doctor(offline: Boolean): Agents.DoctorReport =
    val r =
      AgentDoctor(site.config, picker, resolve, registry = Some(site.aiProviders()), aiHttp = aiHttp)
        .report(online = !offline)
    val held = lease.status match
      case Some((o, u, false, true))  => DoctorCheck("agent lease", CheckLevel.Ok, s"held by this server ($o) until $u")
      case Some((o, u, false, false)) =>
        DoctorCheck(
          "agent lease",
          CheckLevel.Warn,
          s"held by another server ($o) until $u: this one runs no agents, schedules or managers on this database"
        )
      case Some((o, u, true, _)) =>
        DoctorCheck("agent lease", CheckLevel.Warn, s"expired ($o until $u): the next server to start takes it")
      case None if cfg.enabled => DoctorCheck("agent lease", CheckLevel.Warn, "not held: the agents have not started")
      case None                => DoctorCheck("agent lease", CheckLevel.Ok, "not held (agents off)")
    r.modify(_.checks).using(_ :+ held)

  // ── runs ───────────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/runs` — validated up front (pipeline, target, model, prices, budgets) so a mistake is a 400 now,
    * not a failed run later; then queued.
    */
  def startRun(req: RunRequest): Either[ApiError, RunInfo] = startRunAs(req, Origin.Person, None)

  /** [[startRun]] for a schedule or another run: `parent` is the run that starts this one (its goal and model carry
    * over when the request names none).
    */
  def startRunAs(req: RunRequest, origin: Origin, parent: Option[RunInfo]): Either[ApiError, RunInfo] =
    startRunWith(req, origin, parent, identity)

  /** [[startRunAs]] with the options a request does not carry set by `tweak` (a hint-effect run's hint). */
  def startRunWith(
      req: RunRequest,
      origin: Origin,
      parent: Option[RunInfo],
      tweak: RunParams => RunParams
  ): Either[ApiError, RunInfo] =
    prepare(req, parent).map { pr =>
      val id = jobRuns.create(
        RunService.row(
          pr.pipeline,
          pr.target,
          pr.model,
          BigDecimal(pr.usd),
          pr.tokens,
          tweak(pr.params),
          req.acquireItem,
          pr.goal,
          parent.map(_.id),
          origin,
          dryRun = pr.dryRun
        )
      )
      val run = runs.get(id).get
      engine.submit(run.id)
      run
    }

  /** A run request checked and resolved, ready to be created (a manager creates it inside its own transaction,
    * [[com.histmersion.util.jobs.JobRuns.createIn]], and submits it after the commit).
    */
  final case class Prepared(
      pipeline: Pipeline,
      target: String,
      model: Option[String],
      usd: Double,
      tokens: Long,
      params: RunParams,
      goal: Option[String],
      dryRun: Boolean
  )

  /** Everything [[startRunAs]] checks before it queues a run — pipeline, target, models, options, goal, budgets —
    * without creating it.
    */
  def prepare(req: RunRequest, parent: Option[RunInfo]): Either[ApiError, Prepared] =
    for
      _ <- enabled
      p <- Pipeline
        .parse(req.pipeline)
        .toRight(
          ApiError(s"unknown pipeline '${req.pipeline}' (one of: ${Pipeline.values.map(_.wire).mkString(", ")})")
        )
      target <- targetOf(p, req.target)
      over <- overrides(p, req.modelFor)
      // every role the pipeline uses must have a chain with a priced model it can build, NOW — its
      // own --model-for when it has one, else --model, else its chain
      _ <- p.roles.foldLeft[Either[ApiError, Unit]](Right(())) { (acc, role) =>
        acc.flatMap(_ => runnable(role, over.get(role).orElse(req.model)).left.map(ApiError(_)))
      }
      // and an override for a role the pipeline only reaches through a chained run (compile-article's
      // review) is checked the same way
      _ <- over.toList.filterNot((r, _) => p.roles.contains(r)).foldLeft[Either[ApiError, Unit]](Right(())) {
        case (acc, (role, ref)) => acc.flatMap(_ => runnable(role, Some(ref)).left.map(ApiError(_)))
      }
      _ <- host.runOptions(p, req, target, cfg.review.roundsCap)
      goal <- goalOf(p, target, req.goal.orElse(parent.flatMap(_.goal)))
      usd <- positive(req.budgetUsd.orElse(goal.flatMap(goalBudget)).getOrElse(cfg.budgets.runUsd), "budget-usd")
      tok <- positive(req.budgetTokens.getOrElse(cfg.budgets.runTokensFor(p.wire)).toDouble, "budget-tokens")
    yield
      val clean = (xs: List[String]) => Option(xs.map(_.trim).filter(_.nonEmpty).distinct).filter(_.nonEmpty)
      val params = RunParams(
        fix = req.fix.filter(identity),
        rounds = req.rounds,
        title = req.title.map(_.trim).filter(_.nonEmpty),
        kind = req.kind.map(_.trim.toLowerCase).filter(_.nonEmpty),
        query = req.query.map(_.trim).filter(_.nonEmpty),
        topics = clean(req.topics),
        tags = clean(req.tags),
        modelFor = Option.when(over.nonEmpty)(over.toList.sortBy(_._1.ordinal).map((r, m) => ModelOverride(r.wire, m)))
      )
      // a run started by a dry run is one too (nothing it starts may write)
      val dry = req.dryRun.contains(true) || parent.exists(_.dryRun)
      Prepared(p, target, req.model.map(_.trim).filter(_.nonEmpty), usd, tok.toLong, params, goal, dry)

  /** `--model-for role=provider:model`, checked before the run is queued: a role the pipeline uses, once, with a model
    * ref (or `a > b` chain) that parses. Whether that model can run is checked with the rest of the roles (the
    * readiness `models set` shows).
    */
  private def overrides(p: Pipeline, asked: List[ModelOverride]): Either[ApiError, Map[Role, String]] =
    val roles = host.runRoles(p)
    asked.foldLeft[Either[ApiError, Map[Role, String]]](Right(Map.empty)) { (acc, o) =>
      acc.flatMap { m =>
        for
          r <- Role
            .parse(o.role)
            .toRight(
              ApiError(s"--model-for: unknown role '${o.role}' (one of: ${Role.values.map(_.wire).mkString(", ")})")
            )
          _ <- Either.cond(
            roles.contains(r),
            (),
            ApiError(
              s"--model-for: ${p.wire} uses no ${r.wire} role " +
                s"(its roles: ${
                    if roles.isEmpty then "none — it calls no model" else roles.map(_.wire).mkString(", ")
                  })"
            )
          )
          _ <- Either.cond(!m.contains(r), (), ApiError(s"--model-for: the ${r.wire} role is given twice"))
          _ <- ModelChains.parseChain(o.model, knownProviders).left.map(e => ApiError(s"--model-for ${r.wire}: $e"))
        yield m + (r -> o.model.trim)
      }
    }

  /** The target as the pipeline names it (its workflow's domain resolves it), or a manager. */
  private def targetOf(p: Pipeline, raw: String): Either[ApiError, String] =
    host
      .runTarget(p, raw)
      .getOrElse(
        managerStore
          .get(raw.trim)
          .map(_.id)
          .toRight(ApiError(s"No manager '$raw'", Some(Write.CodeNotFound)))
      )

  /** The goal a run works for: the target of a goal pipeline, else the one the request names (which must exist).
    */
  private def goalOf(p: Pipeline, target: String, named: Option[String]): Either[ApiError, Option[String]] =
    if p.targetKind == TargetKind.Goal then Right(Some(target))
    else
      named.map(_.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(g) => host.goal(g).map(x => Some(x.slug))

  private def goalBudget(goal: String): Option[Double] =
    goals.planAndBudget(goal).flatMap(_._2).map(_.toDouble)

  /** Budgets are stored to the micro-dollar (numeric(12,6)): anything smaller would round to 0. */
  private def positive(v: Double, what: String): Either[ApiError, Double] =
    if v >= 0.000001 then Right(v) else Left(ApiError(s"$what must be at least 0.000001"))

  def runs(status: Option[String], limit: Option[Int], goal: Option[String] = None): Either[ApiError, List[RunInfo]] =
    status.map(_.trim).filter(_.nonEmpty) match
      case Some(st) if RunState.parse(st).isEmpty =>
        Left(ApiError(s"unknown status '$st' (one of: ${RunState.values.map(_.wire).mkString(", ")})"))
      case st => Right(runs.list(st, site.config.limits.page(limit, site.config.limits.search), goal).toList)

  /** `GET /api/agents/runs` — one page, newest first ([[Paging]]), with the per-state counts of the runs (of `goal`,
    * when given).
    */
  def runsPage(
      status: Option[String],
      limit: Option[Int],
      goal: Option[String] = None,
      before: Option[String] = None,
      upTo: Option[String] = None
  ): Either[ApiError, (List[RunInfo], Paging.Page)] =
    runsPage(ListFilters.RunFilter(status = status.toList, goal = goal), limit, before, upTo)

  /** `GET /api/agents/runs` filtered ([[ListFilters.RunFilter]]): one page, newest first, with the per-state counts and
    * the facet counts under the same filter.
    */
  def runsPage(
      f: ListFilters.RunFilter,
      limit: Option[Int],
      before: Option[String],
      upTo: Option[String]
  ): Either[ApiError, (List[RunInfo], Paging.Page)] =
    for
      q <- com.histmersion.server.ListQueries.runs(f, java.time.Instant.now(clock))
      page <- runs
        .page(q, site.config.limits.page(limit, site.config.limits.search), cursor(before), cursor(upTo))
        .left
        .map(ApiError(_, Some(Write.CodeNotFound)))
    yield
      val (rows, more) = page
      val (counts, facets) = runs.runFacets(q)
      (rows.toList, Paging.Page.filtered(counts, q.status, Option.when(more)(rows.last.id), facets))

  private def cursor(c: Option[String]): Option[String] = c.map(_.trim).filter(_.nonEmpty)

  def run(id: String): Either[ApiError, RunDetail] =
    runs
      .get(id)
      .toRight(ApiError(s"No agent run '$id'", Some(Write.CodeNotFound)))
      .map(r => RunDetail(r, runs.steps(id, cfg.trace.maxToolResultChars), runs.messages(id)))

  /** `GET /api/agents/compare?id=…` (#992) — runs of one pipeline on one target side by side, from their recorded steps
    * (full outputs, not the display cut). How each configured model is paid for is named from the server's prices and
    * plan pools.
    */
  def compare(ids: List[String]): Either[ApiError, RunCompare.Comparison] =
    val wanted = ids.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)
    for
      runs <- wanted.foldLeft[Either[ApiError, List[RunDetail]]](Right(Nil)) { (acc, id) =>
        acc.flatMap(rs =>
          runs
            .get(id)
            .toRight(ApiError(s"No agent run '$id'", Some(Write.CodeNotFound)))
            .map(r => rs :+ RunDetail(r, runs.steps(r.id, Int.MaxValue), Nil))
        )
      }
      cmp <- CompareRuns.compare(runs.map(CompareRunsViews.run), billingOf).left.map(ApiError(_))
    yield CompareRunsViews.comparison(cmp)

  /** How a configured model is paid for, as `agent models` words it (None: it cannot be read). */
  private def billingOf(ref: String): Option[String] =
    ModelRef
      .parse(ref, knownProviders)
      .toOption
      .flatMap(m => prices.toOption.flatMap(ps => Agents.Prices.billing(ps, m, pools.poolOf(m)).toOption))
      .map(_.describe)

  def control(
      id: String,
      action: String,
      budgetUsd: Option[Double],
      budgetTokens: Option[Long]
  ): Either[ApiError, RunInfo] =
    action.trim.toLowerCase match
      case "pause"  => engine.pause(id)
      case "resume" => enabled.flatMap(_ => engine.resume(id, budgetUsd, budgetTokens))
      case "cancel" => engine.cancel(id)
      case other    => Left(ApiError(s"unknown action '$other' (one of: ${AgentsApi.Actions.mkString(", ")})"))

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/approvals` — readable whether or not agents are on (a person can still decide what an earlier run
    * proposed).
    */
  def approvalsList(state: Option[String], limit: Option[Int]): Either[ApiError, List[ApprovalInfo]] =
    approvals.list(state, limit, site.config.limits.page(limit, site.config.limits.search))

  /** One page of [[approvalsList]] ([[Paging]]), with the per-state counts of every approval. */
  def approvalsPage(
      state: Option[String],
      limit: Option[Int],
      before: Option[String] = None,
      upTo: Option[String] = None
  ): Either[ApiError, (List[ApprovalInfo], Paging.Page)] =
    approvalsPage(ListFilters.ApprovalFilter(state = state), limit, before, upTo)

  /** `GET /api/agents/approvals` filtered ([[ListFilters.ApprovalFilter]]): one page, with the per-state and facet
    * counts under the same filter.
    */
  def approvalsPage(
      f: ListFilters.ApprovalFilter,
      limit: Option[Int],
      before: Option[String],
      upTo: Option[String]
  ): Either[ApiError, (List[ApprovalInfo], Paging.Page)] =
    approvals.page(f, site.config.limits.page(limit, site.config.limits.search), cursor(before), cursor(upTo))

  /** `POST /api/agents/approvals/{id}/{approve|reject}`. Deciding works with agents off too: it only applies a change a
    * person accepts, through the ordinary patch.
    */
  def decide(id: Long, decision: String, req: DecisionRequest): Either[ApiError, ApprovalInfo] =
    approvals.decide(id, decision, req.note).map { a =>
      // a blocking approval: the run waiting on it goes back to the queue (it applies the decision)
      if a.approvalKind.exists(_.blocking) then a.runId.foreach(r => engine.wake(r))
      // an approved plan of articles: its seeding manager gets one compile item per article (the item
      // held for it is settled by the next tick, which the nudge brings forward)
      if a.approvalKind.contains(ApprovalKind.ArticlePlan) then { val _ = managers.applyPlan(a) }
      if a.approvalKind.exists(_.aboutSubject) then managers.nudge()
      a
    }

  // ── goals: view, links, schedules (P3) ─────────────────────────────────────────────────────────

  /** `GET /api/goals/{slug}/view` — readable whether or not agents are on. */
  def goalView(slug: String): Either[ApiError, GoalView] =
    host.goal(slug).map { g =>
      val (plan, budget) = goals.planAndBudget(g.slug).getOrElse((None, None))
      GoalView(
        g,
        goals.children(g.slug),
        goals.links(g.slug),
        managing.goalSchedules(Some(g.slug)),
        plan.flatMap(j => io.circe.parser.decode[Plan](j).toOption),
        budget.map(_.toDouble)
      )
    }

  def addGoalLink(slug: String, in: LinkAdd): Either[ApiError, GoalLink] =
    val ref = in.ref.trim
    for
      g <- host.goal(slug)
      r <- in.kind match
        case LinkKind.Source    => host.linkRef(LinkKind.Source, ref)
        case LinkKind.Article   => host.linkRef(LinkKind.Article, ref)
        case LinkKind.QueueItem =>
          ref.toIntOption
            .flatMap(i => site.queues.get(QueueItemId(i)))
            .map(_.id.toString)
            .toRight(ApiError(s"No queue item '$ref'", Some(Write.CodeNotFound)))
        case LinkKind.SearchQuery =>
          Either.cond(
            ref.nonEmpty && ref.length <= cfg.plan.queryChars,
            ref,
            ApiError(s"a query is 1 to ${cfg.plan.queryChars} characters")
          )
      _ = goals.link(g.slug, in.kind, r, in.note.map(_.trim).filter(_.nonEmpty))
      l <- goals
        .links(g.slug)
        .find(l => l.kind == in.kind && l.ref == r)
        .toRight(ApiError(s"the link to $r was not recorded"))
    yield l

  def removeGoalLink(slug: String, kind: String, ref: String): Either[ApiError, Removed] =
    for
      g <- host.goal(slug)
      k <- LinkKind
        .parse(kind)
        .toRight(ApiError(s"unknown link kind '$kind' (one of: ${LinkKind.values.map(_.wire).mkString(", ")})"))
    yield Removed(goals.unlink(g.slug, k, ref.trim))

  private def scheduleKind(kind: String): Either[ApiError, ScheduleKind] =
    ScheduleKind
      .parse(kind)
      .toRight(ApiError(s"unknown schedule '$kind' (one of: ${ScheduleKind.values.map(_.wire).mkString(", ")})"))

  /** `PUT /api/goals/{slug}/schedules/{kind}` — a sweep of the goal's watcher since phase 3
    * ([[ManagersHandlers.setSchedule]]: created paused when it does not exist; the cron is checked, no more often than
    * `agents.scheduler.min-interval-minutes`). Works with agents off: nothing fires until they are on and the watcher
    * is resumed.
    */
  def setSchedule(
      slug: String,
      kind: String,
      in: ScheduleSet,
      now: java.time.Instant = java.time.Instant.now(clock)
  ): Either[ApiError, GoalSchedule] =
    for
      g <- host.goal(slug)
      k <- scheduleKind(kind)
      _ <- in.budgetUsd.fold[Either[ApiError, Double]](Right(0))(positive(_, "budget-usd"))
      out <- managing.setSchedule(g.slug, k, in, now)
    yield out

  def removeSchedule(slug: String, kind: String): Either[ApiError, Removed] =
    for
      g <- host.goal(slug)
      k <- scheduleKind(kind)
    yield managing.removeSchedule(g.slug, k)

  /** Every goal's schedules: its watcher's sweeps. */
  def schedules: List[GoalSchedule] = managing.goalSchedules(None)

  /** `POST /api/agents/probe` — one LLM call offered exactly one read-only tool (`get_source`) and asked for a typed
    * answer about one source. It proves, end to end, that the model is reachable, calls tools, and returns structured
    * output — and the answer is checked against the record.
    */
  def probe(req: ProbeRequest): Either[ApiError, ProbeResult] =
    for
      _ <- enabled
      src <- host.probeSubject(req.citekey)
      picked <- picker.pick(Role.Probe, req.model).left.map {
        case PickFailure.Unavailable(m)     => ApiError(m)
        case PickFailure.Exhausted(m, _, _) => ApiError(m)
      }
      result <- probeWith(picked.client, picked.billing, src)
    yield result

  private def probeWith(client: LlmClient, billing: Billing, src: ProbeAnswer): Either[ApiError, ProbeResult] =
    for
      d <- definitions.agent(StepAgent.Probe).left.map(ApiError(_))
      sys <- definitions.system(d, ToolStyle.Bare).left.map(ApiError(_))
      r <- probeCall(client, billing, src, d, sys)
    yield r

  /** The probe's call, asked again (up to `step.max-retries` times) with the problems stated while its answer leaves a
    * field it asks for empty ([[ProbeAnswer.problems]]) or is no typed answer at all — prose, or JSON the schema
    * refuses (#915: claude-code:haiku answered in prose), told why in [[Engine.answerProblem]]'s words — as every
    * pipeline step is, whatever the runtime. Usage, cost and time are summed over the attempts; the last answer is the
    * one shown.
    */
  private def probeCall(
      client: LlmClient,
      billing: Billing,
      src: ProbeAnswer,
      d: AgentDef,
      system: String
  ): Either[ApiError, ProbeResult] =
    val ask = s"Call get_source with citekey \"${src.citekey}\" and section \"summary\", then " +
      "reply with that source's citekey, exact title and publication year."
    def call(user: String) = LlmCall[ProbeAnswer](
      system = system,
      user = user,
      tools = toolbox.tools(d.tools),
      schema = AnswerSchemas.probe,
      maxIterations = math.min(cfg.step.maxIterations, 3),
      maxTokens = math.min(cfg.step.maxTokens, 512),
      agent = Some(d)
    )
    val maxAttempts = 1 + math.max(0, cfg.step.maxRetries)
    val t0 = System.nanoTime()
    @annotation.tailrec
    def go(attempt: Int, user: String, used: Usage, calls: List[String]): Either[ApiError, ProbeResult] =
      AgentsHandlers.attempt(client.model)(client.run(call(user))) match
        case Left(e)    => Left(e)
        case Right(res) =>
          val use = used + LlmClient.usage(res.usage)
          val answer = res.finalAnswer.toOption
          val problems =
            res.finalAnswer.fold(f => List(Engine.answerProblem(f, res.finishReason)), _.problems(src.year))
          val toolCalls = calls ++ res.toolCalls.toList.map(c => s"${c.toolName} ${c.input}")
          if problems.nonEmpty && attempt < maxAttempts then
            go(
              attempt + 1,
              ask + Prompt.retry(problems, answer.map(a => io.circe.Encoder[ProbeAnswer].apply(a).noSpaces)),
              use,
              toolCalls
            )
          else
            Right(
              ProbeResult(
                model = client.model.wire,
                answer = answer,
                rawAnswer = LlmClient.rawAnswer(res.finalAnswer).take(2000),
                matchesRecord =
                  problems.isEmpty && answer.exists(a => host.sameTitle(a.title, src.title) && a.year == src.year),
                toolCalls = toolCalls,
                iterations = res.iterations,
                finish = LlmClient.finish(res.finishReason),
                usage = use,
                costUsd = Some(billing.cost(use).toDouble),
                millis = (System.nanoTime() - t0) / 1000000,
                problems = problems,
                attempts = attempt
              )
            )
    go(1, ask, Usage.Zero, Nil)

object AgentsHandlers:
  /** The server's providers over one [[CliRuntime]]. */
  def serverWiring(site: EngineSite): (LlmClient.Factory, CliRuntime) =
    val rt = CliRuntime(site.config, site.roster)
    (LlmClient.factory(rt, site.aiProviders), rt)

  /** [[attempt]], telling the plan's own usage limit (a model to pass over, [[ModelLimitReached]]) apart from a broken
    * provider.
    */
  def call[A](model: ModelRef)(a: => A): Either[Either[ModelLimitReached, ApiError], A] =
    try Right(a)
    catch
      case e: ModelLimitReached =>
        scribe.warn(s"agent call to ${model.wire}: ${e.getMessage}")
        Left(Left(e))
      case e: InterruptedException => throw e
      case e: Exception            => attempt(model)(throw e).left.map(Right(_))

  /** A provider call that threw (unreachable host, an API error, a malformed response) → a value, logged with the model
    * but never with the request (which can carry an API key header).
    */
  def attempt[A](model: ModelRef)(a: => A): Either[ApiError, A] =
    try Right(a)
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        val logged = e match
          case v: VerbatimFailure => v.description
          case _                  => s"${e.getClass.getSimpleName}: ${e.getMessage}"
        scribe.warn(s"agent call to ${model.wire} failed: $logged")
        Left(ApiError(s"the call to ${model.wire} failed: ${VerbatimFailure.describe(e)}".take(600)))
