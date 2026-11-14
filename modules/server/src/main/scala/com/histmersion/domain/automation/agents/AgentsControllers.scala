package com.histmersion
package domain.automation.agents
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import io.scalaland.chimney.dsl.*

import com.histmersion.PageSize
import com.histmersion.domain.automation.agents.AgentsControllers.{BudgetTokens, BudgetUsd, ModelName, PositiveAmount}
import com.histmersion.domain.automation.approvals.{ApprovalDigestService, ApprovalQuery, Approvals, AutoApprovals}
import com.histmersion.domain.automation.goals.BGoalLinkRow
import com.histmersion.domain.automation.{
  Approval as DomainApproval,
  Goal as DomainGoal,
  Pipeline as DomainPipeline,
  Run as DomainRun
}
import com.histmersion.domain.automation.autopilot.AutopilotService
import com.histmersion.domain.automation.goals.GoalFocusService
import com.histmersion.domain.automation.managers.{DrainService, ManagersControllers, ManagerSupervisor, Zero}
import com.histmersion.domain.automation.scheduler.SchedulerService
import com.histmersion.server.agents.{AgentDoctor, BuiltinAi, ClaudeCode, CliRuntime, Codex, Opencode, PlanPools}

import com.histmersion.domain.automation.worklists.QueueItemId

import com.histmersion.domain.automation.agents.VerbatimFailure
import com.histmersion.domain.automation.workflows.{ToolName, Workflows}
import hearth.kindlings.optics.syntax.*
import com.histmersion.pluginhost.HostAiHttp

import com.histmersion.util.database.Transactor
import com.histmersion.api.{Paging, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.domain.automation.agents.ModelSettingsRepo
import com.histmersion.api.automation.{Agents, ListFilters, Managers, ModelChains, PlanQuota, RunCompare}
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.managers.ManagerService
import com.histmersion.domain.automation.goals.FocusService
import com.histmersion.domain.automation.autopilot.AutopilotRowService
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.util.jobs.JobRuns
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.util.jobs.Pinning
import com.histmersion.util.jobs.JobStops
import com.histmersion.util.time.Timer
import com.histmersion.util.time.Pause
import com.histmersion.domain.automation.approvals.ApprovalEffects
import com.histmersion.plugin.AiHttp
import com.histmersion.util.plugins.Plugin
import com.histmersion.domain.automation.managers.SweepAcquireItem
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.server.ListQueries
import com.histmersion.api.library.Filter
import com.histmersion.api.library.Facet
import com.histmersion.util.jobs.Job
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import io.github.iltotore.iron.constraint.any.Pure

/** The ONE implementation of the embedded-agent operations (docs/research-embedded-agents.md): the model check, the
  * probe and (through [[Engine]]) pipeline runs. REST ([[AgentsRoutes]]) and the CLI reach it; it is held as
  * `ServerWiring.agents`.
  *
  * `clientFor` is the seam a test uses to put sttp-ai's scripted model in place of a provider; `planSeams` how the plan
  * pools are read on purpose ([[PlanPools.Seams]]).
  */
final class AgentsControllers(
    site: EngineSite,
    clientFor: LlmClient.Factory,
    val cli: CliRuntime,
    planSeams: PlanPools.Seams
):

  /** A test's factory (sttp-ai's scripted model) over the server's own CLI context. No CLI is started to read a plan
    * pool (a scripted model's test must never spend a real plan on a probe).
    */
  def this(site: EngineSite, clientFor: LlmClient.Factory) =
    this(
      site,
      clientFor,
      CliRuntime(site.config, site.roster, site.userPaths)(using site.stores, site.resources, site.clock, site.jvmHome),
      PlanPools.Seams.Offline
    )

  private def this(site: EngineSite, w: (LlmClient.Factory, CliRuntime)) =
    this(site, w._1, w._2, PlanPools.Seams.Real)

  /** The server's own providers ([[LlmClient.factory]]: the ones registered in the pinned plugin generation — the HTTP
    * ones, the CLI runtimes and any plugin's).
    */
  def this(site: EngineSite) = this(site, AgentsControllers.serverWiring(site))

  /** Read at each use: a config reload switches it (Deps). */
  private def cfg = site.config.agents

  /** The agents' settings now (read at each use). */
  def config: AgentsConfig = cfg

  /** How many sources a goal gathers before its articles are planned (the site's, read at each use). */
  def sourcesToPlan: AgentsControllers.SourcesCount = site.sourcesToPlan

  /** The most panel rounds a run or a manager may ask a review for ([[EngineSite.roundsCap]]). */
  def roundsCap: AgentsControllers.RoundsCap = site.roundsCap
  private val ds = site.ds

  /** The goals: their sub-goals, links, plans and the schedules not moved into a watcher yet. */
  val goals: GoalService =
    GoalService(Transactor(ds))

  /** The agent runs and their steps, as the engine's features read them. */
  val runs: RunService = RunService(Transactor(ds))

  /** The process managers, their items and events, as the engine's other features reach them. */
  val managerStore: ManagerService =
    ManagerService(Transactor(ds))

  /** What the focus records of every goal, and its reads over them. */
  val focusStore: FocusService =
    FocusService(Transactor(ds))

  /** The approvals: what runs proposed for a person to decide. */
  val approvalStore: ApprovalService =
    ApprovalService(Transactor(ds), site.clock)

  /** The autopilot's row: its switch, housekeeping times, the scheduler's strategy, the digest's day, the drain. */
  val autopilotRow: AutopilotRowService =
    AutopilotRowService(Transactor(ds))

  /** What the engine asks of the rest of the server (goals, the housekeeping) until the owners offer services. */
  val host: AutomationHost = site.host(this)

  /** The work lists' operations as a person's go: the worklog lines of the engine's services. */
  val workLists: WorkListControllers = site.workLists

  /** The runs' rows, recorded through the jobs area that owns the `jobs` table. */
  val jobRuns: JobRuns = JobRuns(Transactor(ds))

  /** The work lists' kernel (the runs' log lines, the review's tasks). */
  val queues: QueueService = site.queues

  /** How the engine, the managers and their threads keep work on the generation it started on. */
  val pins: Pinning = site.pins

  /** A person's pause and cancel of the server's long background jobs. */
  val jobStops: JobStops = site.jobStops

  /** The time the managers' tick, the autopilot, the scheduler and the digest decide by. */
  val clock: java.time.Clock = site.clock
  val timer: Timer = site.timer
  val pause: Pause = site.pause

  /** What the daily approval digest also reads the hour on ([[EngineSite.digestClock]]). */
  val digestClock: Option[java.time.Clock] = site.digestClock

  val definitions: AgentDefinitions = cli.definitions
  val tokens: RunTokens = cli.tokens

  /** The subscription pools (also what the CLI clients report their readings to). */
  val pools: PlanPools = PlanPools(ds, cfg, () => cli.trees.codexHome, planSeams)(using site.jvmHome)
  cli.pools = Some(pools)

  /** A `hint-effect` approval a person approves is applied by the managers (read when it happens, so the forward
    * reference to [[managing]] is safe).
    */
  val approvals: Approvals = Approvals(
    approvalStore,
    cfg,
    site.approvalEffects,
    p => managing.applyProposal(p).left.map((text => Problem.Unclassified(Legacy.Text(text))))
  )

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
  val managing: ManagersControllers = ManagersControllers(ds, cfg, site.config.limits, this, managers, host)

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
  val lease: AgentLease = AgentLease(ds, AgentLease.TtlSeconds(3 * math.max(5, cfg.managers.tickSeconds.value)))

  /** Start the run pool and the managers — only when agents are enabled, and only once this server holds the agent
    * lease (a second server on the same database waits for it to be free); off, no thread is started and queued or
    * paused runs simply wait. Every start first moves the goal schedules left into their goals' watchers (created
    * paused; idempotent — agents on or off). Returns how many interrupted runs were paused (0 when the lease is held
    * elsewhere).
    */
  def start(): Int =
    val _ = managers.migrateSchedules(java.time.Instant.now(clock))
    if !cfg.enabled.value then 0
    else
      if cfg.runtimes.generate.value then
        generate(GenerateRequest()) match
          case Right(r) =>
            scribe.info(
              s"agent runtimes generated in ${r.dir}: ${r.files} file(s), ${r.written} changed, ${r.removed} removed"
            )
          case Left(e) => scribe.warn(s"agent runtimes not generated: ${e.message.sentence}")
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
  @volatile private var knownTools: Set[ToolName] = toolbox.names.toSet

  def attachTools(names: Set[ToolName]): Unit = knownTools = names ++ toolbox.names

  /** `POST /api/agents/generate` — works with agents off too: the interactive plugin the research workspace installs is
    * one of the trees.
    */
  def generate(req: GenerateRequest): Either[Api.Error, GenerateResult] =
    for
      rts <- req.runtimes
        .map(r => AgentRuntime.parse(AgentRuntime.Wire(r.value)).filter(AgentRuntime.Generated.contains).toRight(r))
        .partitionMap(identity) match
        case (Nil, ok) => Right(if ok.isEmpty then AgentRuntime.Generated else ok)
        case (bad, _)  =>
          Left(
            Api.Error(
              Problem.Unclassified(
                Legacy.Text(
                  s"unknown runtime(s) ${bad.mkString(", ")} (one of: ${AgentRuntime.Generated.map(_.wire).mkString(", ")})"
                )
              )
            )
          )
      r <- cli.trees
        .generate(knownTools, rts, AgentTrees.out(req.out.map(_.value), site.userPaths))
        .left
        .map(e => Api.Error(e.toNonEmpty.head))
    yield r

  // ── plan pools ─────────────────────────────────────────────────────────────────────────────────

  private def covers(p: PlanQuota.PoolId): List[PlanQuota.PoolStatus.Covers] = p match
    case PlanQuota.PoolId.ClaudePlan  => List(PlanQuota.PoolStatus.Covers(s"${ProviderKind.ClaudeCode.wire}:*"))
    case PlanQuota.PoolId.ChatgptPlan =>
      PlanQuota.PoolStatus.Covers(s"${ProviderKind.Codex.wire}:*") :: Option
        .when(cfg.providers.opencode.chatgptLogin.value)(
          PlanQuota.PoolStatus.Covers(s"${ProviderKind.Opencode.wire}:openai/*")
        )
        .toList

  /** `GET /api/agents/quota` — readable with agents off. Nothing is spent: the ChatGPT pool is read from the app-server
    * (throttled), the Claude pool from what runs and mods reported.
    */
  def quota: PlanQuota.QuotaInfo =
    val now = PlanQuotaRepo.EpochMillis(clock.millis())
    PlanQuota.QuotaInfo(
      pools.statuses(covers, PlanQuota.Now(now.value)),
      picker.allowanceStatuses(now),
      Role.values.toList.filter(picker.fallthrough).map(r => PlanQuota.QuotaInfo.Fallthrough(r.wire.value)),
      PlanQuota.QuotaInfo.Now(java.time.Instant.ofEpochMilli(now.value).toString),
      picker
        .cooldowns(now)
        .map((m, u) =>
          PlanQuota.Cooldown(
            PlanQuota.Cooldown.Model(m.value),
            PlanQuota.Cooldown.Until(u.toString),
            PlanQuota.Rationale(ModelPicker.CoolingWhy)
          )
        )
    )

  private def poolOf(raw: PlanQuota.PoolId.Wire): Either[Api.Error, PlanQuota.PoolId] =
    PlanQuota.PoolId
      .parse(raw)
      .toRight(
        Api.Error(
          Problem.Unclassified(
            Legacy.Text(s"unknown pool '$raw' (one of: ${PlanQuota.PoolId.values.map(_.wire).mkString(", ")})")
          )
        )
      )

  /** `PUT /api/agents/quota/{pool}` — the hold switch and the floors. */
  def setPool(pool: PlanQuota.PoolIdText, in: PlanQuota.PoolSet): Either[Api.Error, PlanQuota.PoolStatus] =
    for
      p <- poolOf(PlanQuota.PoolId.Wire(pool.value))
      f <- in.floors.map(_.value.trim) match
        case Some("") => Right(Some(None))
        case Some(t)  =>
          PlanQuota.Floors
            .parse(PlanQuota.Floors.Wire(t))
            .left
            .map(e => Api.Error(e.toNonEmpty.head))
            .map(fs => Some(Some(fs.text)))
        case None => Right(None)
      _ = in.hold.foreach(h => pools.hold(p, PlanQuotaRepo.Pool.Held(h.value)))
      _ = f.foreach(x => pools.setFloors(p, x.map(w => PlanQuotaRepo.Pool.Floors(w.value))))
      st <- pools
        .statuses(covers)
        .find(_.pool == p.wire)
        .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"no pool ${p.wire}"))))
    yield st

  /** `PUT /api/agents/quota/fallthrough/{role}`. */
  def setFallthrough(role: Role.Wire, in: PlanQuota.FallthroughSet): Either[Api.Error, PlanQuota.QuotaInfo] =
    roleOf(role).map { r =>
      Transactor(ds).transact(
        PlanQuotaRepo.setFallthrough(ModelSettingsRepo.ChainRole(r.wire.value), in.fallthrough.value)
      )
      quota
    }

  /** `POST /api/agents/quota/refresh` — a fresh reading of one pool (None: every pool) now, whatever its age: the
    * ChatGPT plan from the app-server (free), the Claude plan from its probe (one minimal `claude -p` turn) unless
    * `refresh = off`. The login is checked again first; a pool without one is not read.
    */
  def refreshQuota(in: PlanQuota.QuotaRefresh): Either[Api.Error, PlanQuota.QuotaRefreshed] =
    in.pool
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(t => poolOf(PlanQuota.PoolId.Wire(t)).map(List(_)))
      .getOrElse(Right(PlanQuota.PoolId.values.toList))
      .map { ps =>
        val cooled = picker.cooldowns().map(_._1)
        val done =
          ps.map(p => p -> pools.refreshPool(p, PlanQuota.RefreshTrigger.Command, force = PlanPools.Force(true)))
        val after = pools.statuses(covers).map(st => st.pool -> st).toMap
        // a model parked after its plan's limit comes back as soon as a reading taken now says so (#1827)
        val still = picker.cooldowns().map(_._1).toSet
        PlanQuota.QuotaRefreshed(
          done.flatMap { (p, r) =>
            after
              .get(p.wire)
              .map(st =>
                r match
                  case Right(x) =>
                    PlanQuota.PoolRefreshed(
                      p.wire,
                      PlanQuota.PoolRefreshed.Refreshed(x.ok.value),
                      PlanQuota.PoolRefreshed.Detail(x.text.value),
                      st
                    )
                  case Left(why) =>
                    PlanQuota.PoolRefreshed(
                      p.wire,
                      PlanQuota.PoolRefreshed.Refreshed(false),
                      PlanQuota.PoolRefreshed.Detail(s"not read: $why"),
                      st
                    )
              )
          },
          cooled.filterNot(still).map(m => PlanQuota.QuotaRefreshed.Lifted(m.value))
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
    if cfg.enabled.value then
      val _ = managers.migrateSchedules(java.time.Instant.now(clock))
      managers.ensureDefaults()
      val paused = engine.start()
      managers.start()
      scribe.info(s"agents: running on the new database ($paused interrupted run(s) paused there)")

  /** Off unless `agents.enabled`: every agent operation but the read-only model listing refuses. */
  private def enabled: Either[Api.Error, Unit] =
    if cfg.enabled.value then Right(())
    else
      Left(
        Api.Error(
          Problem.Unclassified(
            Legacy.Text("embedded agents are off — set HISTMERSION_AGENTS_ENABLED=true on the server to use them")
          )
        )
      )

  /** The price table, parsed once per call (config is immutable; parsing is microseconds). */
  private def prices: Either[Problem, Map[ModelRef.Model, Price]] =
    Agents.Prices
      .parse(Agents.Prices.Wire(cfg.prices.value))
      .left
      .map(m => Problem.Unclassified(Legacy.Text(s"agents.prices: ${m}")))

  /** A model that can run: on a plan pool or PRICED (an unpriced metered model is refused, never treated as free), and
    * with a client its provider can build (a hosted provider needs its key; `claude-code` a loopback server).
    */
  def resolve(m: ModelRef): Either[Problem, (LlmClient, Billing)] =
    for
      ps <- prices
      b <- Agents.Prices.billing(ps, m, pools.poolOf(m))
      c <- clientFor(cfg, m)
    yield (c, b)

  /** The names plugin AI providers are registered under in the pinned generation — what a `provider:model` may name
    * besides the host's own (docs/research-plugins.md §22).
    */
  /** The client plugin AI providers' doctor checks and model listings go through. */
  private val aiHttp: AiHttp = HostAiHttp()

  def knownProviders: Set[Plugin.Id] = BuiltinAi.known(site.aiProviders())

  private def knownPluginNames: Set[ProviderKind.Plugin.Name] =
    knownProviders.map(id => ProviderKind.Plugin.Name(id.value))

  /** The model chains and allowances in effect, and the choice before every LLM call. */
  val picker: ModelPicker = ModelPicker(ds, cfg, resolve, pools, () => knownPluginNames)(using clock)

  /** A role's chain can run at all: at least one of its models is on a plan or priced, and buildable (allowances are
    * not looked at — a run whose models are all spent queues and pauses until a window frees).
    */
  def runnable(role: Role, overrideRef: Option[ModelName]): Either[Problem, Unit] =
    picker.chainFor(role, overrideRef).flatMap { chain =>
      val tried = chain.map(m => m -> resolve(m))
      if tried.exists(_._2.isRight) then Right(())
      else
        Left(
          Problem.Unclassified(
            Legacy.Text(
              s"no model of the ${role.wire} chain can run: " + tried
                .collect { case (m, Left(w)) => s"${m.wire}: ${w.sentence}" }
                .mkString("; ")
            )
          )
        )
    }

  /** `GET /api/agents/models` — each role's chain, the model it would use now, and every allowance's use per window.
    * Sends nothing to a provider: clients are constructed, spends summed.
    */
  def models: ModelsInfo = models(PlanQuotaRepo.EpochMillis(clock.millis()))

  def models(now: PlanQuotaRepo.EpochMillis): ModelsInfo =
    val chains = picker.roleChains(now)
    val roles = chains.map { c =>
      val shown = c.active.map(_.value).orElse(c.models.headOption.map(_.model.value))
      val entry = c.models.find(e => shown.contains(e.model.value))
      RoleModel(
        Role.Wire(c.role.value),
        shown.map(RoleModel.Model(_)),
        ready = RoleModel.Ready(c.active.isDefined),
        if c.active.isDefined then None
        else
          c.problem
            .map(_.sentence)
            .orElse(
              Option.when(c.models.nonEmpty)(
                c.models.flatMap(e => e.problem.map(p => s"${e.model}: ${p.sentence}")).mkString("; ")
              )
            )
            .map((text => Problem.Unclassified(Legacy.Text(text))))
      )
    }
    val modes = List("status-change" -> cfg.approvals.statusChange, "edges" -> cfg.approvals.edges).collect {
      case (k, v) if ApprovalMode.parse(ApprovalMode.Wire(v.value)).isEmpty =>
        s"agents.approvals.$k = '$v' is neither manual nor auto; treated as manual"
    }
    val modes2 = Option
      .when(ApprovalMode.parse(ApprovalMode.Wire(cfg.approvals.articlePublish.value)).isEmpty)(
        s"agents.approvals.article-publish = '${cfg.approvals.articlePublish}' is neither manual nor auto; treated as manual"
      )
      .toList
    val problems = prices.left.toOption.map(_.sentence).toList ++ modes ++ modes2 ++ picker.allowances._2 ++
      Option.when(!cfg.enabled.value)("embedded agents are off (agents.enabled = false)")
    ModelsInfo(
      ModelsInfo.Enabled(cfg.enabled.value),
      roles,
      Pipeline.values.toList.map(p => ModelsInfo.Pipelines(p.wire.value)),
      ModelsInfo.MaxConcurrentRuns(cfg.maxConcurrentRuns.value),
      problems.map(ModelsInfo.Problems(_)),
      chains,
      picker.allowanceStatuses(now),
      ModelsInfo.Now(java.time.Instant.ofEpochMilli(now.value).toString)
    )

  // ── chains and allowances: saved over config ───────────────────────────────────────────────────

  private def roleOf(raw: Role.Wire): Either[Api.Error, Role] =
    Role
      .parse(raw)
      .toRight(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"unknown role '$raw' (one of: ${Role.values.map(_.wire).mkString(", ")})"))
        )
      )

  private def chainOf(role: Role): Either[Api.Error, ModelChains.RoleChain] =
    picker
      .roleChains(PlanQuotaRepo.EpochMillis(clock.millis()))
      .find(_.role.value == role.wire.value)
      .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"no role ${role.wire}"))))

  /** `PUT /api/agents/models/{role}` — the role's chain, saved over `agents.roles.<role>`. Every model must be a
    * `provider:model`; one that is not priced or buildable yet is accepted and shown as not ready (a price or key may
    * come next). Works with agents off.
    */
  def setChain(role: Role.Wire, in: ModelChains.ChainSet): Either[Api.Error, ModelChains.RoleChain] =
    for
      r <- roleOf(role)
      ms <- ModelChains
        .parseModels(in.models.map(m => ModelRef.Model.Spec(m.value)), knownPluginNames)
        .left
        .map(e => Api.Error(e.toNonEmpty.head))
      _ <- Either.cond(
        ms.nonEmpty,
        (),
        Api.Error(
          Problem.Unclassified(
            Legacy.Text("a chain needs at least one provider:model (reset the role to go back to config)")
          )
        )
      )
      _ = Transactor(ds).transact(ModelSettingsRepo.setChain(ModelSettingsRepo.ChainRole(r.wire.value), ms.map(_.wire)))
      c <- chainOf(r)
    yield c

  /** `DELETE /api/agents/models/{role}` — back to `agents.roles.<role>`. */
  def resetChain(role: Role.Wire): Either[Api.Error, ModelChains.RoleChain] =
    for
      r <- roleOf(role)
      _ = Transactor(ds).transact(ModelSettingsRepo.removeChain(ModelSettingsRepo.ChainRole(r.wire.value)))
      c <- chainOf(r)
    yield c

  /** `PUT /api/agents/allowances` — one model's or provider's limits and reserve, saved over config. */
  def setAllowance(in: ModelChains.AllowanceSet): Either[Api.Error, ModelChains.AllowanceStatus] =
    for
      t <- ModelChains.Target
        .parse(ModelChains.Target.Spec(in.target.value), knownPluginNames)
        .left
        .map(e => Api.Error(e.toNonEmpty.head))
      ls <- in.limits.map(ModelChains.Limit.fromSpec).partitionMap(identity) match
        case (Nil, ok) => Right(ok)
        case (errs, _) => Left(Api.Error(Problem.Unclassified(Legacy.Text(errs.map(_.sentence).mkString("; ")))))
      a <- ModelChains.Allowance.of(t, ls, in.reservePct).left.map(e => Api.Error(e))
      _ = Transactor(ds).transact(
        ModelSettingsRepo.setAllowance(
          ModelSettingsRepo.Saved.Target(t.wire.value),
          a.limits.map(_.spec),
          ModelSettingsRepo.Saved.ReservePct(a.reservePct.value)
        )
      )
      st <- picker
        .allowanceStatuses(PlanQuotaRepo.EpochMillis(clock.millis()))
        .find(_.target.value == t.wire.value)
        .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"the allowance of ${t.wire} was not recorded"))))
    yield st

  /** `DELETE /api/agents/allowances?target=…` — the saved allowance goes; config's (if any) applies again. */
  def removeAllowance(target: ModelChains.AllowanceSet.Target): Either[Api.Error, Goal.Plans.Removed] =
    ModelChains.Target
      .parse(ModelChains.Target.Spec(target.value), knownPluginNames)
      .left
      .map(e => Api.Error(e))
      .map(t =>
        Goal.Plans.Removed(
          Goal.Plans.Removed.Value(
            Transactor(ds).transact(ModelSettingsRepo.removeAllowance(ModelSettingsRepo.Saved.Target(t.wire.value)))
          )
        )
      )

  /** `GET /api/agents/doctor` — every provider checked without spending anything. */
  def doctor(offline: AgentsControllers.Offline): Agents.DoctorReport =
    val r =
      AgentDoctor(
        site.config,
        picker,
        resolve(_),
        registry = Some(site.aiProviders().map((k, v) => AgentDoctor.RegistryKey(k.value) -> v)),
        aiHttp = aiHttp,
        paths = site.userPaths
      )
        .report(online = DoctorReport.Online(!offline.value))
    val held = lease.status match
      case Some((o, u, false, true)) =>
        DoctorCheck(
          DoctorCheck.Name("agent lease"),
          CheckLevel.Ok,
          DoctorCheck.Detail(s"held by this server ($o) until $u")
        )
      case Some((o, u, false, false)) =>
        DoctorCheck(
          DoctorCheck.Name("agent lease"),
          CheckLevel.Warn,
          DoctorCheck.Detail(
            s"held by another server ($o) until $u: this one runs no agents, schedules or managers on this database"
          )
        )
      case Some((o, u, true, _)) =>
        DoctorCheck(
          DoctorCheck.Name("agent lease"),
          CheckLevel.Warn,
          DoctorCheck.Detail(s"expired ($o until $u): the next server to start takes it")
        )
      case None if cfg.enabled.value =>
        DoctorCheck(
          DoctorCheck.Name("agent lease"),
          CheckLevel.Warn,
          DoctorCheck.Detail("not held: the agents have not started")
        )
      case None =>
        DoctorCheck(DoctorCheck.Name("agent lease"), CheckLevel.Ok, DoctorCheck.Detail("not held (agents off)"))
    r.modify(_.checks).using(_ :+ held)

  // ── runs ───────────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/runs` — validated up front (pipeline, target, model, prices, budgets) so a mistake is a 400 now,
    * not a failed run later; then queued.
    */
  def startRun(req: RunRequest): Either[Api.Error, RunInfo] = startRunAs(req, Origin.Person, None)

  /** [[startRun]] for a schedule or another run: `parent` is the run that starts this one (its goal and model carry
    * over when the request names none).
    */
  def startRunAs(req: RunRequest, origin: Origin, parent: Option[RunInfo]): Either[Api.Error, RunInfo] =
    startRunWith(req, origin, parent, identity)

  /** [[startRunAs]] with the options a request does not carry set by `tweak` (a hint-effect run's hint). */
  def startRunWith(
      req: RunRequest,
      origin: Origin,
      parent: Option[RunInfo],
      tweak: RunParams => RunParams
  ): Either[Api.Error, RunInfo] =
    prepare(req, parent).map { pr =>
      val id = jobRuns.create(
        RunService.row(
          pr.pipeline,
          pr.target,
          pr.model,
          BigDecimal(pr.usd.value),
          pr.tokens,
          tweak(pr.params),
          req.acquireItem.map(i => SweepAcquireItem.Item(i.value)),
          pr.goal,
          parent.map(p => DomainRun.Id.assume(p.id.value)),
          origin,
          dryRun = pr.dryRun
        )
      )
      val run = runs.get(id).get
      engine.submit(DomainRun.Id.assume(run.id.value))
      run
    }

  /** A run request checked and resolved, ready to be created (a manager creates it inside its own transaction,
    * [[com.histmersion.util.jobs.JobRuns.createIn]], and submits it after the commit).
    */
  final case class Prepared(
      pipeline: Pipeline,
      target: AgentsControllers.Target,
      model: Option[ModelName],
      usd: BudgetUsd,
      tokens: BudgetTokens,
      params: RunParams,
      goal: Option[DomainGoal.Slug],
      dryRun: RunService.DryRun
  )

  /** Everything [[startRunAs]] checks before it queues a run — pipeline, target, models, options, goal, budgets —
    * without creating it.
    */
  def prepare(req: RunRequest, parent: Option[RunInfo]): Either[Api.Error, Prepared] =
    for
      _ <- enabled
      p <- Pipeline
        .parse(Pipeline.Wire(req.pipeline.value))
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown pipeline '${req.pipeline}' (one of: ${Pipeline.values.map(_.wire).mkString(", ")})")
            )
          )
        )
      target <- targetOf(p, AgentsControllers.Target(req.target.value))
      over <- overrides(p, req.modelFor)
      // every role the pipeline uses must have a chain with a priced model it can build, NOW — its
      // own --model-for when it has one, else --model, else its chain
      _ <- p.roles.foldLeft[Either[Api.Error, Unit]](Right(())) { (acc, role) =>
        acc.flatMap(_ =>
          runnable(role, over.get(role).orElse(req.model.map(m => ModelName(m.value)))).left
            .map(e => Api.Error(e.toNonEmpty.head))
        )
      }
      // and an override for a role the pipeline only reaches through a chained run (compile-article's
      // review) is checked the same way
      _ <- over.toList.filterNot((r, _) => p.roles.contains(r)).foldLeft[Either[Api.Error, Unit]](Right(())) {
        case (acc, (role, ref)) =>
          acc.flatMap(_ => runnable(role, Some(ref)).left.map(e => Api.Error(e)))
      }
      _ <- host.runOptions(
        p,
        req,
        ManagerContext.ItemTarget(target.value),
        AutomationHost.RunOptionsRoundsCap(roundsCap.value)
      )
      goal <- goalOf(p, target, req.goal.orElse(parent.flatMap(_.goal)).map(_.value))
      usd <- positive(
        PositiveAmount(
          req.budgetUsd.map(_.value).orElse(goal.flatMap(goalBudget).map(_.value)).getOrElse(cfg.budgets.runUsd.value)
        ),
        "budget-usd"
      )
      tok <- positive(
        PositiveAmount(
          req.budgetTokens
            .map(_.value)
            .getOrElse(cfg.budgets.runTokensFor(DomainPipeline.Wire(p.wire.value)).value)
            .toDouble
        ),
        "budget-tokens"
      )
    yield
      val clean = (xs: List[String]) => Option(xs.map(_.trim).filter(_.nonEmpty).distinct).filter(_.nonEmpty)
      val params = RunParams(
        fix = req.fix.filter(_.value),
        rounds = req.rounds,
        title = req.title.map(_.value.trim).filter(_.nonEmpty).map(RunRequest.Title(_)),
        kind = req.kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty).map(RunRequest.Kind(_)),
        query = req.query.map(_.value.trim).filter(_.nonEmpty).map(RunRequest.Query(_)),
        topics = clean(req.topics.map(_.value)).map(_.map(RunRequest.Topics(_))),
        tags = clean(req.tags.map(_.value)).map(_.map(RunRequest.Tags(_))),
        modelFor = Option.when(over.nonEmpty)(
          over.toList.sortBy(_._1.ordinal).map((r, m) => ModelOverride(r.wire, ModelOverride.Model(m.value)))
        )
      )
      // a run started by a dry run is one too (nothing it starts may write)
      val dry = req.dryRun.exists(_.value) || parent.exists(_.dryRun.value)
      Prepared(
        p,
        target,
        req.model.map(_.value.trim).filter(_.nonEmpty).map(ModelName(_)),
        BudgetUsd(usd.value),
        BudgetTokens(tok.value.toLong),
        params,
        goal,
        RunService.DryRun(dry)
      )

  /** `--model-for role=provider:model`, checked before the run is queued: a role the pipeline uses, once, with a model
    * ref (or `a > b` chain) that parses. Whether that model can run is checked with the rest of the roles (the
    * readiness `models set` shows).
    */
  private def overrides(p: Pipeline, asked: List[ModelOverride]): Either[Api.Error, Map[Role, ModelName]] =
    val roles = host.runRoles(p)
    asked.foldLeft[Either[Api.Error, Map[Role, ModelName]]](Right(Map.empty)) { (acc, o) =>
      acc.flatMap { m =>
        for
          r <- Role
            .parse(o.role)
            .toRight(
              Api.Error(
                Problem.Unclassified(
                  Legacy
                    .Text(s"--model-for: unknown role '${o.role}' (one of: ${Role.values.map(_.wire).mkString(", ")})")
                )
              )
            )
          _ <- Either.cond(
            roles.contains(r),
            (),
            Api.Error(
              Problem.Unclassified(
                Legacy.Text(
                  s"--model-for: ${p.wire} uses no ${r.wire} role " +
                    s"(its roles: ${
                        if roles.isEmpty then "none — it calls no model" else roles.map(_.wire).mkString(", ")
                      })"
                )
              )
            )
          )
          _ <- Either.cond(
            !m.contains(r),
            (),
            Api.Error(Problem.Unclassified(Legacy.Text(s"--model-for: the ${r.wire} role is given twice")))
          )
          _ <- ModelChains
            .parseChain(ModelChains.Chain.Spec(o.model.value), knownPluginNames)
            .left
            .map(e => Api.Error(Problem.Unclassified(Legacy.Text(s"--model-for ${r.wire}: ${e}"))))
        yield m + (r -> ModelName(o.model.value.trim))
      }
    }

  /** The target as the pipeline names it (its workflow's domain resolves it), or a manager. */
  private def targetOf(p: Pipeline, raw: AgentsControllers.Target): Either[Api.Error, AgentsControllers.Target] =
    host
      .runTarget(p, AutomationHost.RunTargetRaw(raw.value))
      .map(_.map(t => AgentsControllers.Target(t.value)))
      .getOrElse(
        managerStore
          .get(Managers.ManagerInfo.Id(raw.value.trim))
          .map(m => AgentsControllers.Target(m.id.value))
          .toRight(
            Api.Error(Problem.Unclassified(Legacy.Text(s"No manager '$raw'")), Some(Api.Error.Code(Write.CodeNotFound)))
          )
      )

  /** The goal a run works for: the target of a goal pipeline, else the one the request names (which must exist).
    */
  private def goalOf(
      p: Pipeline,
      target: AgentsControllers.Target,
      named: Option[String]
  ): Either[Api.Error, Option[DomainGoal.Slug]] =
    def slug(raw: String): Either[Api.Error, DomainGoal.Slug] =
      DomainGoal.Slug
        .either(raw)
        .left
        .map(_ =>
          Api.Error(Problem.Unclassified(Legacy.Text(s"No goal '$raw'")), Some(Api.Error.Code(Write.CodeNotFound)))
        )
    if p.targetKind == TargetKind.Goal then slug(target.value).map(Some(_))
    else
      named.map(_.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(g) => slug(g).flatMap(s => host.goal(s).map(_ => Some(s)))

  private def goalBudget(goal: DomainGoal.Slug): Option[BudgetUsd] =
    goals.planAndBudget(goal).flatMap(_._2).map(b => BudgetUsd(b.toDouble))

  /** Budgets are stored to the micro-dollar (numeric(12,6)): anything smaller would round to 0. */
  private def positive(v: PositiveAmount, what: String): Either[Api.Error, PositiveAmount] =
    if v.value >= 0.000001 then Right(v)
    else Left(Api.Error(Problem.Unclassified(Legacy.Text(s"$what must be at least 0.000001"))))

  private def notFound(message: Problem): Api.Error =
    Api.Error(message, Some(Api.Error.Code(Write.CodeNotFound)))

  private def searchPage: PageSize = PageSize.applyUnsafe(math.max(1, site.config.limits.search.value))

  def runs(
      status: Option[RunQuery.Status],
      limit: Option[PageSize],
      goal: Option[DomainGoal.Slug] = None
  ): Either[Api.Error, List[RunInfo]] =
    status.map(st => RunQuery.Status(st.value.trim)).filter(_.value.nonEmpty) match
      case Some(st) if RunState.parse(RunState.Wire(st.value)).isEmpty =>
        Left(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown status '$st' (one of: ${RunState.values.map(_.wire).mkString(", ")})")
            )
          )
        )
      case st => Right(runs.list(st, RunQuery.Limit(site.config.limits.page(limit, searchPage).value), goal).toList)

  /** `GET /api/agents/runs` — one page, newest first ([[Paging]]), with the per-state counts of the runs (of `goal`,
    * when given).
    */
  def runsPage(
      status: Option[RunQuery.Status],
      limit: Option[PageSize],
      goal: Option[DomainGoal.Slug] = None,
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Either[Api.Error, (List[RunInfo], Paging.Page)] =
    runsPage(
      ListFilters.RunFilter(
        status = status.toList.map(st => ListFilters.RunFilter.Status(st.value)),
        goal = goal.map(g => Goal.Slug(g.value))
      ),
      limit,
      before,
      upTo
    )

  /** `GET /api/agents/runs` filtered ([[ListFilters.RunFilter]]): one page, newest first, with the per-state counts and
    * the facet counts under the same filter.
    */
  def runsPage(
      f: ListFilters.RunFilter,
      limit: Option[PageSize],
      before: Option[Paging.Cursor],
      upTo: Option[Paging.Cursor]
  ): Either[Api.Error, (List[RunInfo], Paging.Page)] =
    for
      q <- ListQueries.runs(f, java.time.Instant.now(clock))
      page <- runs
        .page(
          q,
          RunQuery.Limit(site.config.limits.page(limit, searchPage).value),
          cursor(before).map(c => AgentRepo.PageCursor(c.value)),
          cursor(upTo).map(c => RunQuery.UpTo(c.value))
        )
        .left
        .map(p => Api.Error(p, Some(Api.Error.Code(Write.CodeNotFound))))
    yield
      val (rows, more) = page
      val (counts, facets) = runs.runFacets(q)
      (
        rows.toList,
        Paging.Page.filtered(
          counts.map((state, n) => (Paging.Page.CountState(state.value), Paging.Count(n.value))),
          q.status.map(st => Paging.Page.CountState(st.value)),
          Option.when(more)(Filter.Last(rows.last.id.value)),
          facets.map((name, values) =>
            (
              Facet.Name(name),
              values.map((v, n) => (Facet.Value(v), Facet.Count(n.value)))
            )
          )
        )
      )

  private def cursor(c: Option[Paging.Cursor]): Option[Paging.Cursor] =
    c.map(x => Paging.Cursor(x.value.trim)).filter(_.value.nonEmpty)

  def run(id: DomainRun.Id): Either[Api.Error, RunDetail] =
    runs
      .get(id)
      .toRight(notFound(Problem.Unclassified(Legacy.Text(s"No agent run '$id'"))))
      .map(r =>
        RunDetail(
          r,
          runs.steps(Job.Id.assume(id.value), AgentRepo.OutputChars(cfg.trace.maxToolResultChars.value)),
          runs.messages(Job.Id.assume(id.value))
        )
      )

  /** `GET /api/agents/compare?id=…` (#992) — runs of one pipeline on one target side by side, from their recorded steps
    * (full outputs, not the display cut). How each configured model is paid for is named from the server's prices and
    * plan pools.
    */
  def compare(ids: List[DomainRun.Id]): Either[Api.Error, RunCompare.Comparison] =
    for
      wanted <- AgentsControllers.runIds(ids.map(_.value))
      runs <- wanted.foldLeft[Either[Api.Error, List[RunDetail]]](Right(Nil)) { (acc, id) =>
        acc.flatMap(rs =>
          runs
            .get(id)
            .toRight(notFound(Problem.Unclassified(Legacy.Text(s"No agent run '$id'"))))
            .map(r => rs :+ RunDetail(r, runs.steps(Job.Id.assume(id.value), AgentRepo.OutputChars(Int.MaxValue)), Nil))
        )
      }
      compared = runs.map(CompareRunsViews.run)
      // what the runs' workflow produced is compared as its owner says
      outputs = compared.headOption
        .flatMap(r => workflows.parse(r.run.pipeline.value))
        .fold(OutputComparison.none)(_.outputs)
      cmp <- CompareRuns.compare(compared, billingOf, outputs).left.map(p => Api.Error(p))
    yield CompareRunsViews.comparison(cmp)

  /** How a configured model is paid for, as `agent models` words it (None: it cannot be read). */
  private def billingOf(ref: CompareRuns.Step.Model): Option[CompareRuns.StepSide.Billing] =
    ModelRef
      .parse(ModelRef.Wire(ref.value), knownPluginNames)
      .toOption
      .flatMap(m => prices.toOption.flatMap(ps => Agents.Prices.billing(ps, m, pools.poolOf(m)).toOption))
      .map(b => CompareRuns.StepSide.Billing(b.describe))

  def control(
      id: DomainRun.Id,
      action: Action,
      budgetUsd: Option[BudgetUsd],
      budgetTokens: Option[BudgetTokens]
  ): Either[Api.Error, RunInfo] =
    action.value.trim.toLowerCase match
      case "pause"  => engine.pause(id)
      case "resume" => enabled.flatMap(_ => engine.resume(id, budgetUsd, budgetTokens))
      case "cancel" => engine.cancel(id)
      case other    =>
        Left(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown action '$other' (one of: ${List("pause", "resume", "cancel").mkString(", ")})")
            )
          )
        )

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/approvals` — readable whether or not agents are on (a person can still decide what an earlier run
    * proposed).
    */
  def approvalsList(
      state: Option[ApprovalQuery.State],
      limit: Option[PageSize]
  ): Either[Api.Error, List[ApprovalInfo]] =
    approvals.list(
      state,
      limit.map(l => ApprovalQuery.Limit(l.value)),
      ApprovalQuery.Limit(site.config.limits.page(limit, searchPage).value)
    )

  /** One page of [[approvalsList]] ([[Paging]]), with the per-state counts of every approval. */
  def approvalsPage(
      state: Option[ApprovalQuery.State],
      limit: Option[PageSize],
      before: Option[Paging.Cursor] = None,
      upTo: Option[Paging.Cursor] = None
  ): Either[Api.Error, (List[ApprovalInfo], Paging.Page)] =
    approvalsPage(
      ListFilters.ApprovalFilter(state = state.map(st => ListFilters.ApprovalFilter.State(st.value))),
      limit,
      before,
      upTo
    )

  /** `GET /api/agents/approvals` filtered ([[ListFilters.ApprovalFilter]]): one page, with the per-state and facet
    * counts under the same filter.
    */
  def approvalsPage(
      f: ListFilters.ApprovalFilter,
      limit: Option[PageSize],
      before: Option[Paging.Cursor],
      upTo: Option[Paging.Cursor]
  ): Either[Api.Error, (List[ApprovalInfo], Paging.Page)] =
    approvals.page(
      f,
      ApprovalQuery.Limit(site.config.limits.page(limit, searchPage).value),
      cursor(before).map(c => ApprovalQuery.Cursor(c.value)),
      cursor(upTo).map(c => ApprovalQuery.UpTo(c.value))
    )

  /** `POST /api/agents/approvals/{id}/{approve|reject}`. Deciding works with agents off too: it only applies a change a
    * person accepts, through the ordinary patch.
    */
  def decide(
      id: DomainApproval.Id,
      decision: DomainApproval.Outcome,
      req: DecisionRequest
  ): Either[Api.Error, ApprovalInfo] =
    approvals.decide(id, decision, req.note.map(n => DomainApproval.Note(n.value))).map { a =>
      // a blocking approval: the run waiting on it goes back to the queue (it applies the decision)
      if a.approvalKind.exists(_.blocking.value) then a.runId.foreach(r => engine.wake(DomainRun.Id.assume(r.value)))
      // an approved plan of articles: its seeding manager gets one compile item per article (the item
      // held for it is settled by the next tick, which the nudge brings forward)
      if a.approvalKind.contains(ApprovalKind.ArticlePlan) then { val _ = managers.applyPlan(a) }
      if a.approvalKind.exists(_.aboutSubject) then managers.nudge()
      a
    }

  // ── goals: view, links, schedules (P3) ─────────────────────────────────────────────────────────

  /** `GET /api/goals/{slug}/view` — readable whether or not agents are on. */
  def goalView(slug: DomainGoal.Slug): Either[Api.Error, GoalView] =
    host.goal(slug).map { g =>
      val (plan, budget) = goals.planAndBudget(slug).getOrElse((None, None))
      GoalView(
        g,
        goals.children(slug),
        goals.links(slug),
        managing.goalSchedules(Some(slug)),
        plan.flatMap(j => RawJson.decode[Plan](j.value).toOption),
        budget.map(b => GoalView.BudgetUsd(b.toDouble))
      )
    }

  def addGoalLink(slug: DomainGoal.Slug, in: LinkAdd): Either[Api.Error, GoalLink] =
    val ref = in.ref.value.trim
    for
      _ <- host.goal(slug)
      r <- in.kind match
        case LinkKind.Source =>
          host.linkRef(LinkKind.Source, AutomationHost.LinkRefRaw(ref)).map(x => GoalLink.Ref(x.value))
        case LinkKind.Article =>
          host.linkRef(LinkKind.Article, AutomationHost.LinkRefRaw(ref)).map(x => GoalLink.Ref(x.value))
        case LinkKind.QueueItem =>
          ref.toIntOption
            .flatMap(i => site.queues.get(QueueItemId(i)))
            .map(i => GoalLink.Ref(i.id.toString))
            .toRight(notFound(Problem.Unclassified(Legacy.Text(s"No queue item '$ref'"))))
        case LinkKind.Query =>
          Either.cond(
            ref.nonEmpty && ref.length <= cfg.plan.queryChars.value,
            GoalLink.Ref(ref),
            Api.Error(Problem.Unclassified(Legacy.Text(s"a query is 1 to ${cfg.plan.queryChars} characters")))
          )
      _ = goals.link(
        slug,
        in.kind.transformInto[DomainGoal.LinkKind],
        r,
        in.note.map(_.value.trim).filter(_.nonEmpty).map(GoalLink.Note(_))
      )
      l <- goals
        .links(slug)
        .find(l => l.kind == in.kind && l.ref == r)
        .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"the link to $r was not recorded"))))
    yield l

  def removeGoalLink(slug: DomainGoal.Slug, kind: LinkKind.Wire, ref: BGoalLinkRow.Ref): Either[Api.Error, Removed] =
    for
      _ <- host.goal(slug)
      k <- LinkKind
        .parse(kind)
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown link kind '$kind' (one of: ${LinkKind.values.map(_.wire).mkString(", ")})")
            )
          )
        )
    yield Removed(
      Removed.Value(goals.unlink(slug, k.transformInto[DomainGoal.LinkKind], GoalLink.Ref(ref.value.trim)))
    )

  private def scheduleKind(kind: ScheduleKind.Wire): Either[Api.Error, ScheduleKind] =
    ScheduleKind
      .parse(kind)
      .toRight(
        Api.Error(
          Problem.Unclassified(
            Legacy.Text(s"unknown schedule '$kind' (one of: ${ScheduleKind.values.map(_.wire).mkString(", ")})")
          )
        )
      )

  /** `PUT /api/goals/{slug}/schedules/{kind}` — a sweep of the goal's watcher since phase 3
    * ([[ManagersControllers.setSchedule]]: created paused when it does not exist; the cron is checked, no more often
    * than `agents.scheduler.min-interval-minutes`). Works with agents off: nothing fires until they are on and the
    * watcher is resumed.
    */
  def setSchedule(
      slug: DomainGoal.Slug,
      kind: ScheduleKind.Wire,
      in: ScheduleSet,
      now: java.time.Instant = java.time.Instant.now(clock)
  ): Either[Api.Error, GoalSchedule] =
    for
      _ <- host.goal(slug)
      k <- scheduleKind(kind)
      _ <- in.budgetUsd.fold[Either[Api.Error, PositiveAmount]](Right(PositiveAmount(0)))(b =>
        positive(PositiveAmount(b.value), "budget-usd")
      )
      out <- managing.setSchedule(slug, k, in, now)
    yield out

  def removeSchedule(slug: DomainGoal.Slug, kind: ScheduleKind.Wire): Either[Api.Error, Removed] =
    for
      _ <- host.goal(slug)
      k <- scheduleKind(kind)
    yield managing.removeSchedule(slug, k)

  /** Every goal's schedules: its watcher's sweeps. */
  def schedules: List[GoalSchedule] = managing.goalSchedules(None)

  /** `POST /api/agents/probe` — one LLM call offered exactly one read-only tool (`get_source`) and asked for a typed
    * answer about one source. It proves, end to end, that the model is reachable, calls tools, and returns structured
    * output — and the answer is checked against the record.
    */
  def probe(req: ProbeRequest): Either[Api.Error, ProbeResult] =
    for
      _ <- enabled
      ck <- Citekey
        .either(req.citekey.value)
        .left
        .map(_ => notFound(Problem.Unclassified(Legacy.Text(s"No source '${req.citekey}'"))))
      src <- host.probeSubject(ck)
      picked <- picker.pick(Role.Probe, req.model.map(m => ModelName(m.value))).left.map {
        case PickFailure.Unavailable(m)     => Api.Error(m)
        case PickFailure.Exhausted(m, _, _) => Api.Error(m)
      }
      result <- probeWith(picked.client, picked.billing, src)
    yield result

  private def probeWith(client: LlmClient, billing: Billing, src: ProbeAnswer): Either[Api.Error, ProbeResult] =
    for
      d <- definitions.agent(StepAgent.Probe).left.map(m => Api.Error(Problem.Unclassified(Legacy.Text(m))))
      sys <- definitions.system(d, ToolStyle.Bare).left.map(m => Api.Error(Problem.Unclassified(Legacy.Text(m))))
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
  ): Either[Api.Error, ProbeResult] =
    val ask = s"Call get_source with citekey \"${src.citekey}\" and section \"summary\", then " +
      "reply with that source's citekey, exact title and publication year."
    def call(user: String) = LlmCall[ProbeAnswer](
      system = LlmCall.System(system),
      user = LlmCall.User(user),
      tools = toolbox.tools(d.tools),
      schema = AnswerSchemas.probe,
      maxIterations = LlmCall.MaxIterations(math.min(cfg.step.maxIterations.value, 3)),
      maxTokens = LlmCall.MaxTokens(math.min(cfg.step.maxTokens.value, 512)),
      agent = Some(d)
    )
    val maxAttempts = 1 + math.max(0, cfg.step.maxRetries.value)
    val t0 = timer.start()
    @annotation.tailrec
    def go(attempt: Int, user: String, used: Usage, calls: List[String]): Either[Api.Error, ProbeResult] =
      AgentsControllers.attempt(client.model)(client.run(call(user))) match
        case Left(e)    => Left(e)
        case Right(res) =>
          val use = used + LlmClient.usage(res.usage)
          val answer = res.finalAnswer.toOption
          val problems =
            res.finalAnswer.fold(
              f => List(Engine.answerProblem(f, res.finishReason).sentence),
              _.problems(src.year.map(y => ProbeAnswer.RecordYear(y.value.toInt))).map(_.sentence)
            )
          val toolCalls = calls ++ res.toolCalls.toList.map(c => s"${c.toolName} ${c.input}")
          if problems.nonEmpty && attempt < maxAttempts then
            go(
              attempt + 1,
              ask + Prompt.retry(
                problems.map((text => Problem.Unclassified(Legacy.Text(text)))),
                answer.map(a => writeToString(a))
              ),
              use,
              toolCalls
            )
          else
            Right(
              ProbeResult(
                model = ProbeResult.Model(client.model.wire.value),
                answer = answer,
                rawAnswer = ProbeResult.RawAnswer(LlmClient.rawAnswer(res.finalAnswer).take(2000)),
                matchesRecord = ProbeResult.MatchesRecord(
                  problems.isEmpty && answer.exists(a =>
                    host.sameTitle(Title(a.title.value), Title(src.title.value)) && a.year == src.year
                  )
                ),
                toolCalls = toolCalls.map(ProbeResult.ToolCalls(_)),
                iterations = ProbeResult.Iterations(res.iterations),
                finish = ProbeResult.Finish(LlmClient.finish(res.finishReason)),
                usage = use,
                costUsd = Some(ProbeResult.CostUsd(billing.cost(use).toDouble)),
                millis = ProbeResult.Millis(t0.elapsed.toMillis),
                problems = problems.map(ProbeResult.Problems(_)),
                attempts = ProbeResult.Attempts(attempt)
              )
            )
    go(1, ask, Usage.Zero, Nil)

object AgentsControllers:
  type BudgetTokens = BudgetTokens.T
  object BudgetTokens extends Newtype[Long, Pure]
  type BudgetUsd = BudgetUsd.T
  object BudgetUsd extends Newtype[Double, Pure]
  type ModelName = ModelName.T
  object ModelName extends Newtype[String, Pure]
  type Offline = Offline.T
  object Offline extends Newtype[Boolean, Pure]
  type PositiveAmount = PositiveAmount.T
  object PositiveAmount extends Newtype[Double, Pure]
  type RoundsCap = RoundsCap.T
  object RoundsCap extends Newtype[Int, Pure]
  type SourcesCount = SourcesCount.T
  object SourcesCount extends Newtype[Int, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]

  /** A run id a request names: one that is not a run id names no run. */
  def runId(raw: String): Either[Api.Error, DomainRun.Id] =
    DomainRun.Id
      .either(raw)
      .left
      .map(_ =>
        Api.Error(Problem.Unclassified(Legacy.Text(s"No agent run '$raw'")), Some(Api.Error.Code(Write.CodeNotFound)))
      )

  /** The run ids a request names (comma-separated ones too). */
  def runIds(raw: List[String]): Either[Api.Error, List[DomainRun.Id]] =
    raw
      .flatMap(_.split(','))
      .map(_.trim)
      .filter(_.nonEmpty)
      .foldLeft[Either[Api.Error, List[DomainRun.Id]]](Right(Nil))((acc, id) =>
        acc.flatMap(xs => runId(id).map(xs :+ _))
      )

  /** A goal slug a request names: one that is not a slug names no goal. */
  def goalSlug(raw: String): Either[Api.Error, DomainGoal.Slug] =
    DomainGoal.Slug
      .either(raw)
      .left
      .map(_ =>
        Api.Error(Problem.Unclassified(Legacy.Text(s"No goal '$raw'")), Some(Api.Error.Code(Write.CodeNotFound)))
      )

  /** The server's providers over one [[CliRuntime]]. */

  def serverWiring(site: EngineSite): (LlmClient.Factory, CliRuntime) =
    val rt =
      CliRuntime(site.config, site.roster, site.userPaths)(using site.stores, site.resources, site.clock, site.jvmHome)
    (LlmClient.factory(rt, site.aiProviders), rt)

  /** [[attempt]], telling the plan's own usage limit (a model to pass over, [[ModelLimitReached]]) apart from a broken
    * provider.
    */
  def call[A](model: ModelRef)(a: => A): Either[Either[ModelLimitReached, Api.Error], A] =
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
  def attempt[A](model: ModelRef)(a: => A): Either[Api.Error, A] =
    try Right(a)
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        val logged = e match
          case v: VerbatimFailure => v.description.sentence
          case _                  => s"${e.getClass.getSimpleName}: ${e.getMessage}"
        scribe.warn(s"agent call to ${model.wire} failed: $logged")
        Left(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"the call to ${model.wire} failed: ${VerbatimFailure.describe(e).sentence}".take(600))
            )
          )
        )
