package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.GoalPlans.*
import com.histmersion.db.{AgentRepo, GoalLinkRepo, GoalRepo, ModelSettingsRepo, QueueRepo}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.ResponseSchema

/** The ONE implementation of the embedded-agent operations (docs/research-embedded-agents.md):
  * the model check, the probe and (through [[Engine]]) pipeline runs. REST ([[AgentsRoutes]]) and the
  * CLI reach it; it is held as `Handlers.agents`.
  *
  * `clientFor` is the seam a test uses to put sttp-ai's scripted model in place of a provider;
  * `planSeams` how the plan pools are read on purpose ([[PlanPools.Seams]]). */
final class AgentsHandlers(deps: Deps, h: Handlers, clientFor: LlmClient.Factory, val cli: CliRuntime,
    planSeams: PlanPools.Seams):

  /** A test's factory (sttp-ai's scripted model) over the server's own CLI context. No CLI is started
    * to read a plan pool (a scripted model's test must never spend a real plan on a probe). */
  def this(deps: Deps, h: Handlers, clientFor: LlmClient.Factory) =
    this(deps, h, clientFor, CliRuntime(deps.cfg), PlanPools.Seams.Offline)

  private def this(deps: Deps, h: Handlers, w: (LlmClient.Factory, CliRuntime)) = this(deps, h, w._1, w._2, PlanPools.Seams.Real)

  /** The server's own providers ([[LlmClient.factory]]: the HTTP ones and the CLI runtimes). */
  def this(deps: Deps, h: Handlers) = this(deps, h, AgentsHandlers.serverWiring(deps.cfg))

  private val cfg     = deps.cfg.agents
  private val ds      = deps.ds

  /** The acquisition toolset (OpenAlex/Crossref, refs, assess, followups). A `var` because it is built
    * AFTER [[Handlers]] (it needs the ingest queue), and attached by `Main` before the pool starts;
    * until then — and in a test that attaches nothing — the steps that need it say it is offline. */
  @volatile private var upstream: AgentUpstream = AgentUpstream.Offline

  def attach(u: AgentUpstream): Unit = upstream = u

  val definitions: AgentDefinitions = cli.definitions
  val tokens: RunTokens = cli.tokens
  /** The subscription pools (also what the CLI clients report their readings to). */
  val pools: PlanPools = PlanPools(ds, cfg, () => cli.trees.codexHome, planSeams)
  cli.pools = Some(pools)
  val toolbox: Toolbox     = Toolbox(h, () => upstream)
  val approvals: Approvals = Approvals(h, ds, cfg)

  /** The run pool. Constructed here, started by [[start]] (from `Main`, after the OCR pool). */
  val engine: Engine = Engine(ds, cfg, this, Map(
    Pipeline.EnrichSource     -> EnrichSourcePipeline(h, ds, cfg, () => upstream, approvals),
    Pipeline.ReviewArticle    -> ReviewArticlePipeline(h, ds, cfg, approvals, definitions),
    Pipeline.PlanGoal         -> PlanGoalPipeline(h, ds, cfg, () => upstream),
    Pipeline.CompileArticle   -> CompileArticlePipeline(h, ds, cfg,
                                   (req, parent) => startRunAs(req, Origin.Run, Some(parent))),
    Pipeline.CurrencyCheck    -> CurrencyCheckPipeline(h, ds, cfg, () => upstream, approvals),
    Pipeline.AcquisitionSweep -> AcquisitionSweepPipeline(h, ds, cfg, () => upstream)))

  /** The goal schedules (started with the pool, and only when agents are enabled). */
  val scheduler: Scheduler = Scheduler(ds, cfg, this, h)

  /** Start the run pool and the scheduler — only when agents are enabled; off, no thread is started
    * and queued or paused runs simply wait. Returns how many interrupted runs were paused. */
  def start(): Int =
    if !cfg.enabled then 0
    else
      if cfg.runtimes.generate then
        generate(GenerateRequest()) match
          case Right(r) => scribe.info(s"agent runtimes generated in ${r.dir}: ${r.files} file(s), ${r.written} changed, ${r.removed} removed")
          case Left(e)  => scribe.warn(s"agent runtimes not generated: ${e.message}")
      val paused = engine.start()
      scheduler.start()
      paused

  // ── the generated runtime trees ────────────────────────────────────────────────────────────────

  /** The MCP tools `/mcp` advertises — what a placeholder may name. A `var` because the tool list is
    * built after the handlers ([[com.histmersion.server.McpTools]] needs them); `Main` attaches it
    * before [[start]]. Until then, the agent tools. */
  @volatile private var knownTools: Set[String] = ToolName.values.map(_.wire).toSet

  def attachTools(names: Set[String]): Unit = knownTools = names ++ ToolName.values.map(_.wire)

  /** `POST /api/agents/generate` — works with agents off too: the interactive plugin the research
    * workspace installs is one of the trees. */
  def generate(req: GenerateRequest): Either[ApiError, GenerateResult] =
    for
      rts <- req.runtimes.map(r => AgentRuntime.parse(r).filter(AgentRuntime.Generated.contains).toRight(r))
               .partitionMap(identity) match
               case (Nil, ok) => Right(if ok.isEmpty then AgentRuntime.Generated else ok)
               case (bad, _)  => Left(ApiError(s"unknown runtime(s) ${bad.mkString(", ")} (one of: ${AgentRuntime.Generated.map(_.wire).mkString(", ")})"))
      out  = req.out.map(_.trim).filter(_.nonEmpty).map(o => java.nio.file.Path.of(AgentTrees.expand(o)).toAbsolutePath.normalize)
      r   <- cli.trees.generate(knownTools, rts, out).left.map(ApiError(_))
    yield r

  // ── plan pools ─────────────────────────────────────────────────────────────────────────────────

  private def covers(p: PlanQuota.PoolId): List[String] = p match
    case PlanQuota.PoolId.ClaudePlan  => List(s"${ProviderKind.ClaudeCode.wire}:*")
    case PlanQuota.PoolId.ChatgptPlan =>
      s"${ProviderKind.Codex.wire}:*" :: Option.when(cfg.providers.opencode.chatgptLogin)(s"${ProviderKind.Opencode.wire}:openai/*").toList

  /** `GET /api/agents/quota` — readable with agents off. Nothing is spent: the ChatGPT pool is read
    * from the app-server (throttled), the Claude pool from what runs and mods reported. */
  def quota: PlanQuota.QuotaInfo =
    val now = System.currentTimeMillis()
    PlanQuota.QuotaInfo(pools.statuses(covers, now), picker.allowanceStatuses(now),
      Role.values.toList.filter(picker.fallthrough).map(_.wire), java.time.Instant.ofEpochMilli(now).toString)

  private def poolOf(raw: String): Either[ApiError, PlanQuota.PoolId] =
    PlanQuota.PoolId.parse(raw).toRight(ApiError(s"unknown pool '$raw' (one of: ${PlanQuota.PoolId.values.map(_.wire).mkString(", ")})"))

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

  /** `POST /api/agents/quota/refresh` — a fresh reading of one pool (None: every pool) now, whatever
    * its age: the ChatGPT plan from the app-server (free), the Claude plan from its probe (one minimal
    * `claude -p` turn) unless `refresh = off`. The login is checked again first; a pool without one is
    * not read. */
  def refreshQuota(in: PlanQuota.QuotaRefresh): Either[ApiError, PlanQuota.QuotaRefreshed] =
    in.pool.map(_.trim).filter(_.nonEmpty).map(poolOf(_).map(List(_))).getOrElse(Right(PlanQuota.PoolId.values.toList))
      .map { ps =>
        val done = ps.map(p => p -> pools.refreshPool(p, PlanQuota.RefreshTrigger.Command, force = true))
        val after = pools.statuses(covers).map(st => st.pool -> st).toMap
        PlanQuota.QuotaRefreshed(done.flatMap { (p, r) =>
          after.get(p.wire).map(st => r match
            case Right(x) => PlanQuota.PoolRefreshed(p.wire, x.ok, x.text, st)
            case Left(why) => PlanQuota.PoolRefreshed(p.wire, refreshed = false, s"not read: $why", st))
        })
      }

  /** `POST /api/agents/quota/claude` — a mod's report, answered with go on or stop. */
  def guard(r: PlanQuota.ModReport): PlanQuota.GuardAnswer = pools.guard(r)

  /** Stop the scheduler and the pool (tests; the server process just exits). */
  def stop(): Unit =
    scheduler.stop()
    engine.stop()

  /** Off unless `agents.enabled`: every agent operation but the read-only model listing refuses. */
  private def enabled: Either[ApiError, Unit] =
    if cfg.enabled then Right(())
    else Left(ApiError("embedded agents are off — set HISTMERSION_AGENTS_ENABLED=true on the server to use them"))

  /** The price table, parsed once per call (config is immutable; parsing is microseconds). */
  private def prices: Either[String, Map[String, Price]] =
    Agents.Prices.parse(cfg.prices).left.map(m => s"agents.prices: $m")

  /** A model that can run: on a plan pool or PRICED (an unpriced metered model is refused, never
    * treated as free), and with a client its provider can build (a hosted provider needs its key;
    * `claude-code` a loopback server). */
  def resolve(m: ModelRef): Either[String, (LlmClient, Billing)] =
    for
      ps <- prices
      b  <- Agents.Prices.billing(ps, m, pools.poolOf(m))
      c  <- clientFor(cfg, m)
    yield (c, b)

  /** The model chains and allowances in effect, and the choice before every LLM call. */
  val picker: ModelPicker = ModelPicker(ds, cfg, resolve, pools)

  /** A role's chain can run at all: at least one of its models is on a plan or priced, and buildable (allowances
    * are not looked at — a run whose models are all spent queues and pauses until a window frees). */
  def runnable(role: Role, overrideRef: Option[String]): Either[String, Unit] =
    picker.chainFor(role, overrideRef).flatMap { chain =>
      val tried = chain.map(m => m -> resolve(m))
      if tried.exists(_._2.isRight) then Right(())
      else Left(s"no model of the ${role.wire} chain can run: " + tried.collect { case (m, Left(w)) => s"${m.wire}: $w" }.mkString("; "))
    }

  /** `GET /api/agents/models` — each role's chain, the model it would use now, and every allowance's
    * use per window. Sends nothing to a provider: clients are constructed, spends summed. */
  def models: ModelsInfo = models(System.currentTimeMillis())

  def models(now: Long): ModelsInfo =
    val chains = picker.roleChains(now)
    val roles = chains.map { c =>
      val shown = c.active.orElse(c.models.headOption.map(_.model))
      val entry = c.models.find(e => shown.contains(e.model))
      RoleModel(c.role, shown, ready = c.active.isDefined,
        if c.active.isDefined then None
        else c.problem.orElse(Option.when(c.models.nonEmpty)(c.models.flatMap(e => e.problem.map(p => s"${e.model}: $p")).mkString("; "))))
    }
    val modes = List("status-change" -> cfg.approvals.statusChange, "edges" -> cfg.approvals.edges).collect {
      case (k, v) if ApprovalMode.parse(v).isEmpty => s"agents.approvals.$k = '$v' is neither manual nor auto; treated as manual"
    }
    val modes2 = Option.when(ApprovalMode.parse(cfg.approvals.articlePublish).isEmpty)(
      s"agents.approvals.article-publish = '${cfg.approvals.articlePublish}' is neither manual nor auto; treated as manual").toList
    val problems = prices.left.toOption.toList ++ modes ++ modes2 ++ picker.allowances._2 ++
      Option.when(!cfg.enabled)("embedded agents are off (agents.enabled = false)")
    ModelsInfo(cfg.enabled, roles, Pipeline.values.toList.map(_.wire), cfg.maxConcurrentRuns, problems,
      chains, picker.allowanceStatuses(now), java.time.Instant.ofEpochMilli(now).toString)

  // ── chains and allowances: saved over config ───────────────────────────────────────────────────

  private def roleOf(raw: String): Either[ApiError, Role] =
    Role.parse(raw).toRight(ApiError(s"unknown role '$raw' (one of: ${Role.values.map(_.wire).mkString(", ")})"))

  private def chainOf(role: Role): Either[ApiError, ModelChains.RoleChain] =
    picker.roleChains(System.currentTimeMillis()).find(_.role == role.wire).toRight(ApiError(s"no role ${role.wire}"))

  /** `PUT /api/agents/models/{role}` — the role's chain, saved over `agents.roles.<role>`. Every model
    * must be a `provider:model`; one that is not priced or buildable yet is accepted and shown as not
    * ready (a price or key may come next). Works with agents off. */
  def setChain(role: String, in: ModelChains.ChainSet): Either[ApiError, ModelChains.RoleChain] =
    for
      r  <- roleOf(role)
      ms <- ModelChains.parseModels(in.models).left.map(ApiError(_))
      _  <- Either.cond(ms.nonEmpty, (), ApiError("a chain needs at least one provider:model (reset the role to go back to config)"))
      _ = ModelSettingsRepo.setChain(ds, r.wire, ms.map(_.wire))
      c  <- chainOf(r)
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
      t  <- ModelChains.Target.parse(in.target).left.map(ApiError(_))
      ls <- in.limits.map(ModelChains.Limit.fromSpec).partitionMap(identity) match
              case (Nil, ok) => Right(ok)
              case (errs, _) => Left(ApiError(errs.mkString("; ")))
      a  <- ModelChains.Allowance.of(t, ls, in.reservePct).left.map(ApiError(_))
      _ = ModelSettingsRepo.setAllowance(ds, t.wire, a.limits.map(_.spec), a.reservePct)
      st <- picker.allowanceStatuses(System.currentTimeMillis()).find(_.target == t.wire)
              .toRight(ApiError(s"the allowance of ${t.wire} was not recorded"))
    yield st

  /** `DELETE /api/agents/allowances?target=…` — the saved allowance goes; config's (if any) applies again. */
  def removeAllowance(target: String): Either[ApiError, GoalPlans.Removed] =
    ModelChains.Target.parse(target).left.map(ApiError(_))
      .map(t => GoalPlans.Removed(ModelSettingsRepo.removeAllowance(ds, t.wire)))

  /** `GET /api/agents/doctor` — every provider checked without spending anything. */
  def doctor(offline: Boolean): Agents.DoctorReport =
    AgentDoctor(deps.cfg, picker, resolve).report(online = !offline)

  // ── runs ───────────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/runs` — validated up front (pipeline, target, model, prices, budgets) so a
    * mistake is a 400 now, not a failed run later; then queued. */
  def startRun(req: RunRequest): Either[ApiError, RunInfo] = startRunAs(req, Origin.Person, None)

  /** [[startRun]] for a schedule or another run: `parent` is the run that starts this one (its goal
    * and model carry over when the request names none). */
  def startRunAs(req: RunRequest, origin: Origin, parent: Option[RunInfo]): Either[ApiError, RunInfo] =
    for
      _      <- enabled
      p      <- Pipeline.parse(req.pipeline).toRight(ApiError(
                  s"unknown pipeline '${req.pipeline}' (one of: ${Pipeline.values.map(_.wire).mkString(", ")})"))
      target <- targetOf(p, req.target)
      over   <- overrides(p, req.modelFor)
      // every role the pipeline uses must have a chain with a priced model it can build, NOW — its
      // own --model-for when it has one, else --model, else its chain
      _      <- p.roles.foldLeft[Either[ApiError, Unit]](Right(())) { (acc, role) =>
                  acc.flatMap(_ => runnable(role, over.get(role).orElse(req.model)).left.map(ApiError(_)))
                }
      // and an override for a role the pipeline only reaches through a chained run (compile-article's
      // review) is checked the same way
      _      <- over.toList.filterNot((r, _) => p.roles.contains(r)).foldLeft[Either[ApiError, Unit]](Right(())) {
                  case (acc, (role, ref)) => acc.flatMap(_ => runnable(role, Some(ref)).left.map(ApiError(_)))
                }
      _      <- options(p, req, target)
      goal   <- goalOf(p, target, req.goal.orElse(parent.flatMap(_.goal)))
      usd    <- positive(req.budgetUsd.orElse(goal.flatMap(goalBudget)).getOrElse(cfg.budgets.runUsd), "budget-usd")
      tok    <- positive(req.budgetTokens.getOrElse(cfg.budgets.runTokens).toDouble, "budget-tokens")
    yield
      val clean = (xs: List[String]) => Option(xs.map(_.trim).filter(_.nonEmpty).distinct).filter(_.nonEmpty)
      val params = RunParams(fix = req.fix.filter(identity), rounds = req.rounds,
        title = req.title.map(_.trim).filter(_.nonEmpty), kind = req.kind.map(_.trim.toLowerCase).filter(_.nonEmpty),
        query = req.query.map(_.trim).filter(_.nonEmpty), topics = clean(req.topics), tags = clean(req.tags),
        modelFor = Option.when(over.nonEmpty)(over.toList.sortBy(_._1.ordinal).map((r, m) => ModelOverride(r.wire, m))))
      // a run started by a dry run is one too (nothing it starts may write)
      val dry = req.dryRun.contains(true) || parent.exists(_.dryRun)
      val run = AgentRepo.create(ds, p, target, req.model.map(_.trim).filter(_.nonEmpty), BigDecimal(usd), tok.toLong,
        params, req.acquireItem, goal, parent.map(_.id), origin, dryRun = dry)
      engine.submit(run.id)
      run

  /** `agents.auto-enrich`: after an ingest job stored `citekey`, queue an `enrich-source` run for it
    * ([[AutoEnrich]] decides). Returns the note for the job's progress line — the run queued, or why
    * not — or None when nothing was asked for (agents or the switch off). An ordinary run: budgets,
    * plan floors and `max-calls-in-flight` apply. One at a time, so two imports of the same file
    * finishing together cannot both queue one. Never throws: the ingest is done whatever happens here. */
  def autoEnrich(citekey: String): Option[String] =
    try autoEnrichLock.synchronized {
      h.getSource(citekey, body = false) match
        case Left(e) => Some(AutoEnrich.notQueued(e.message))
        case Right(src) =>
          val ck   = src.source.citekey
          // a dry run wrote nothing: it neither enriched the source nor stands for a run that will
          val runs = AgentRepo.forTarget(ds, Pipeline.EnrichSource, ck).toList.filterNot(_.dryRun).flatMap(r =>
            RunState.parse(r.status).map(AutoEnrich.PriorRun(r.id, _)))
          AutoEnrich.decide(cfg.enabled, cfg.autoEnrich, src.findings.size, src.source.notes, runs) match
            case Left(skip) => AutoEnrich.note(skip)
            case Right(()) =>
              Some(startRunAs(RunRequest(Pipeline.EnrichSource.wire, ck), Origin.Ingest, None)
                .fold(e => AutoEnrich.notQueued(e.message), r => AutoEnrich.queued(r.id)))
    }
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"auto-enrich of $citekey failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
        Some(AutoEnrich.notQueued(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300)))

  private val autoEnrichLock = new Object

  /** The roles a run of `p` may give a model of its own: its own, and those of the run it chains
    * (compile-article starts a review-article run with fix). */
  private def overridable(p: Pipeline): List[Role] =
    (p.roles ++ (if p == Pipeline.CompileArticle then Pipeline.ReviewArticle.roles else Nil)).distinct

  /** `--model-for role=provider:model`, checked before the run is queued: a role the pipeline uses,
    * once, with a model ref (or `a > b` chain) that parses. Whether that model can run is checked with
    * the rest of the roles (the readiness `models set` shows). */
  private def overrides(p: Pipeline, asked: List[ModelOverride]): Either[ApiError, Map[Role, String]] =
    val roles = overridable(p)
    asked.foldLeft[Either[ApiError, Map[Role, String]]](Right(Map.empty)) { (acc, o) =>
      acc.flatMap { m =>
        for
          r <- Role.parse(o.role).toRight(ApiError(s"--model-for: unknown role '${o.role}' (one of: ${Role.values.map(_.wire).mkString(", ")})"))
          _ <- Either.cond(roles.contains(r), (), ApiError(s"--model-for: ${p.wire} uses no ${r.wire} role " +
                 s"(its roles: ${if roles.isEmpty then "none — it calls no model" else roles.map(_.wire).mkString(", ")})"))
          _ <- Either.cond(!m.contains(r), (), ApiError(s"--model-for: the ${r.wire} role is given twice"))
          _ <- ModelChains.parseChain(o.model).left.map(e => ApiError(s"--model-for ${r.wire}: $e"))
        yield m + (r -> o.model.trim)
      }
    }

  /** The target as the pipeline names it: an existing source or article, a goal — or, for
    * compile-article, any valid slug (a new article is created, an existing one extended). */
  private def targetOf(p: Pipeline, raw: String): Either[ApiError, String] =
    p.targetKind match
      case TargetKind.Source => h.getSource(raw, body = false).map(_.source.citekey)
      case TargetKind.Article if p == Pipeline.CompileArticle => Articles.slugOf(raw).left.map(ApiError(_))
      case TargetKind.Article => h.getArticle(raw).map(_.article.slug)
      case TargetKind.Goal    => h.getGoal(raw.trim).map(_.slug)

  /** The goal a run works for: the target of a goal pipeline, else the one the request names (which
    * must exist). */
  private def goalOf(p: Pipeline, target: String, named: Option[String]): Either[ApiError, Option[String]] =
    if p.targetKind == TargetKind.Goal then Right(Some(target))
    else named.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(g) => h.getGoal(g).map(x => Some(x.slug))

  private def goalBudget(goal: String): Option[Double] =
    GoalLinkRepo.planAndBudget(ds, goal).flatMap(_._2).map(_.toDouble)

  /** The options a pipeline understands, checked before the run is queued: `fix`/`rounds` belong to
    * review-article, an acquisition item to enrich-source (and must be an open acquisition item), a
    * title, kind and filter to compile-article (a NEW article needs a filter and a valid kind). */
  private def options(p: Pipeline, req: RunRequest, target: String): Either[ApiError, Unit] =
    val wrong = (what: String, owner: Pipeline) => Left(ApiError(s"$what is an option of ${owner.wire}, not of ${p.wire}"))
    val compileOpts = req.title.isDefined || req.kind.isDefined || req.query.isDefined || req.topics.nonEmpty || req.tags.nonEmpty
    p match
      case _ if p != Pipeline.ReviewArticle && req.fix.contains(true) => wrong("fix", Pipeline.ReviewArticle)
      case _ if p != Pipeline.ReviewArticle && req.rounds.isDefined   => wrong("rounds", Pipeline.ReviewArticle)
      case _ if p != Pipeline.EnrichSource && req.acquireItem.isDefined => wrong("an acquisition item", Pipeline.EnrichSource)
      case _ if p != Pipeline.CompileArticle && compileOpts => wrong("a title, kind or filter", Pipeline.CompileArticle)
      case _ if req.rounds.exists(n => n < 1 || n > cfg.review.roundsCap) =>
        Left(ApiError(s"rounds must be between 1 and ${cfg.review.roundsCap} (agents.review.rounds-cap)"))
      case Pipeline.CompileArticle =>
        val exists = AgentRepo.slugExists(ds, target)
        val filter = req.query.exists(_.trim.nonEmpty) || req.topics.exists(_.trim.nonEmpty) || req.tags.exists(_.trim.nonEmpty)
        for
          _ <- req.kind.fold[Either[ApiError, String]](Right(""))(k => Articles.kindOf(k).left.map(ApiError(_)))
          _ <- Either.cond(exists || filter, (), ApiError(s"'$target' is a new article: give a filter (query, topics or tags) " +
                 "for the sources to compile it from"))
          _ <- Either.cond(exists || req.title.exists(_.trim.nonEmpty), (), ApiError(s"'$target' is a new article: give its title"))
        yield ()
      case _ =>
        req.acquireItem.fold(Right(()))(id =>
          Queue.fulfillable(id, QueueRepo.get(ds, id)).map(_ => ()).left.map(ApiError(_)))

  /** Budgets are stored to the micro-dollar (numeric(12,6)): anything smaller would round to 0. */
  private def positive(v: Double, what: String): Either[ApiError, Double] =
    if v >= 0.000001 then Right(v) else Left(ApiError(s"$what must be at least 0.000001"))

  def runs(status: Option[String], limit: Option[Int], goal: Option[String] = None): Either[ApiError, List[RunInfo]] =
    status.map(_.trim).filter(_.nonEmpty) match
      case Some(st) if RunState.parse(st).isEmpty =>
        Left(ApiError(s"unknown status '$st' (one of: ${RunState.values.map(_.wire).mkString(", ")})"))
      case st => Right(AgentRepo.list(ds, st, deps.cfg.limits.page(limit, deps.cfg.limits.search), goal).toList)

  def run(id: String): Either[ApiError, RunDetail] =
    AgentRepo.get(ds, id).toRight(ApiError(s"No agent run '$id'", Some(Write.CodeNotFound)))
      .map(r => RunDetail(r, AgentRepo.steps(ds, id, cfg.trace.maxToolResultChars), AgentRepo.messages(ds, id)))

  /** `GET /api/agents/compare?id=…` (#992) — runs of one pipeline on one target side by side, from
    * their recorded steps (full outputs, not the display cut). How each configured model is paid for
    * is named from the server's prices and plan pools. */
  def compare(ids: List[String]): Either[ApiError, RunCompare.Comparison] =
    val wanted = ids.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)
    for
      runs <- wanted.foldLeft[Either[ApiError, List[RunDetail]]](Right(Nil)) { (acc, id) =>
                acc.flatMap(rs => AgentRepo.get(ds, id).toRight(ApiError(s"No agent run '$id'", Some(Write.CodeNotFound)))
                  .map(r => rs :+ RunDetail(r, AgentRepo.steps(ds, r.id, Int.MaxValue), Nil)))
              }
      cmp  <- CompareRuns.compare(runs, billingOf).left.map(ApiError(_))
    yield cmp

  /** How a configured model is paid for, as `agent models` words it (None: it cannot be read). */
  private def billingOf(ref: String): Option[String] =
    ModelRef.parse(ref).toOption.flatMap(m => prices.toOption.flatMap(ps => Agents.Prices.billing(ps, m, pools.poolOf(m)).toOption))
      .map(_.describe)

  def control(id: String, action: String, budgetUsd: Option[Double], budgetTokens: Option[Long]): Either[ApiError, RunInfo] =
    action.trim.toLowerCase match
      case "pause"  => engine.pause(id)
      case "resume" => enabled.flatMap(_ => engine.resume(id, budgetUsd, budgetTokens))
      case "cancel" => engine.cancel(id)
      case other    => Left(ApiError(s"unknown action '$other' (one of: ${AgentsApi.Actions.mkString(", ")})"))

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/approvals` — readable whether or not agents are on (a person can still decide
    * what an earlier run proposed). */
  def approvalsList(state: Option[String], limit: Option[Int]): Either[ApiError, List[ApprovalInfo]] =
    approvals.list(state, limit, deps.cfg.limits.page(limit, deps.cfg.limits.search))

  /** `POST /api/agents/approvals/{id}/{approve|reject}`. Deciding works with agents off too: it only
    * applies a change a person accepts, through the ordinary patch. */
  def decide(id: Long, decision: String, req: DecisionRequest): Either[ApiError, ApprovalInfo] =
    approvals.decide(id, decision, req.note).map { a =>
      // a blocking approval: the run waiting on it goes back to the queue (it applies the decision)
      if a.approvalKind.exists(_.blocking) then a.runId.foreach(r => engine.wake(r))
      a
    }

  // ── goals: view, links, schedules (P3) ─────────────────────────────────────────────────────────

  /** `GET /api/goals/{slug}/view` — readable whether or not agents are on. */
  def goalView(slug: String): Either[ApiError, GoalView] =
    h.getGoal(slug).map { g =>
      val (plan, budget) = GoalLinkRepo.planAndBudget(ds, g.slug).getOrElse((None, None))
      GoalView(g, GoalRepo.children(ds, g.slug), GoalLinkRepo.links(ds, g.slug), GoalLinkRepo.schedules(ds, Some(g.slug)),
        plan.flatMap(j => io.circe.parser.decode[Plan](j).toOption), budget.map(_.toDouble))
    }

  def addGoalLink(slug: String, in: LinkAdd): Either[ApiError, GoalLink] =
    val ref = in.ref.trim
    for
      g <- h.getGoal(slug)
      r <- in.kind match
             case LinkKind.Source    => h.getSource(ref, body = false).map(_.source.citekey)
             case LinkKind.Article   => h.getArticle(ref).map(_.article.slug)
             case LinkKind.QueueItem => ref.toIntOption.flatMap(QueueRepo.get(ds, _)).map(_.id.toString)
                                          .toRight(ApiError(s"No queue item '$ref'", Some(Write.CodeNotFound)))
             case LinkKind.Query     => Either.cond(ref.nonEmpty && ref.length <= cfg.plan.queryChars, ref,
                                          ApiError(s"a query is 1 to ${cfg.plan.queryChars} characters"))
      _ = GoalLinkRepo.link(ds, g.slug, in.kind, r, in.note.map(_.trim).filter(_.nonEmpty))
      l <- GoalLinkRepo.links(ds, g.slug).find(l => l.kind == in.kind && l.ref == r)
             .toRight(ApiError(s"the link to $r was not recorded"))
    yield l

  def removeGoalLink(slug: String, kind: String, ref: String): Either[ApiError, Removed] =
    for
      g <- h.getGoal(slug)
      k <- LinkKind.parse(kind).toRight(ApiError(s"unknown link kind '$kind' (one of: ${LinkKind.values.map(_.wire).mkString(", ")})"))
    yield Removed(GoalLinkRepo.unlink(ds, g.slug, k, ref.trim))

  private def scheduleKind(kind: String): Either[ApiError, ScheduleKind] =
    ScheduleKind.parse(kind).toRight(ApiError(s"unknown schedule '$kind' (one of: ${ScheduleKind.values.map(_.wire).mkString(", ")})"))

  /** `PUT /api/goals/{slug}/schedules/{kind}` — the cron is parsed and its next firing computed now;
    * a schedule that would fire more often than `agents.scheduler.min-interval-minutes` is refused.
    * Setting one works with agents off: it simply does not fire until they are on. */
  def setSchedule(slug: String, kind: String, in: ScheduleSet, now: java.time.Instant = java.time.Instant.now()): Either[ApiError, GoalSchedule] =
    for
      g    <- h.getGoal(slug)
      k    <- scheduleKind(kind)
      cron <- Cron.parse(in.cron).left.map(ApiError(_))
      _    <- cron.shortestGapMinutes(now).filter(_ < cfg.scheduler.minIntervalMinutes) match
                case Some(gap) => Left(ApiError(s"'${in.cron}' would fire every $gap minute(s); the least is " +
                                    s"${cfg.scheduler.minIntervalMinutes} (agents.scheduler.min-interval-minutes)"))
                case None => Right(())
      _    <- in.budgetUsd.fold[Either[ApiError, Double]](Right(0))(positive(_, "budget-usd"))
      _ = GoalLinkRepo.setSchedule(ds, g.slug, k, cron.text, in.enabled, in.budgetUsd.map(BigDecimal(_)),
            Option.when(in.enabled)(cron.next(now)).flatten)
      out  <- GoalLinkRepo.schedule(ds, g.slug, k).toRight(ApiError("the schedule was not recorded"))
    yield out

  def removeSchedule(slug: String, kind: String): Either[ApiError, Removed] =
    for
      g <- h.getGoal(slug)
      k <- scheduleKind(kind)
    yield Removed(GoalLinkRepo.removeSchedule(ds, g.slug, k))

  def schedules: List[GoalSchedule] = GoalLinkRepo.schedules(ds)

  /** `POST /api/agents/probe` — one LLM call offered exactly one read-only tool (`get_source`) and
    * asked for a typed answer about one source. It proves, end to end, that the model is reachable,
    * calls tools, and returns structured output — and the answer is checked against the record. */
  def probe(req: ProbeRequest): Either[ApiError, ProbeResult] =
    for
      _       <- enabled
      src     <- h.getSource(req.citekey, body = false)
      picked  <- picker.pick(Role.Probe, req.model).left.map {
                   case PickFailure.Unavailable(m)  => ApiError(m)
                   case PickFailure.Exhausted(m, _, _) => ApiError(m)
                 }
      result  <- probeWith(picked.client, picked.billing, src.source)
    yield result

  private def probeWith(client: LlmClient, billing: Billing, src: SourceDetail): Either[ApiError, ProbeResult] =
    for
      d   <- definitions.agent(StepAgent.Probe).left.map(ApiError(_))
      sys <- definitions.system(d, ToolStyle.Bare).left.map(ApiError(_))
      r   <- probeCall(client, billing, src, d, sys)
    yield r

  private def probeCall(client: LlmClient, billing: Billing, src: SourceDetail, d: AgentDef,
      system: String): Either[ApiError, ProbeResult] =
    val call = LlmCall[ProbeAnswer](
      system = system,
      user = s"Call get_source with citekey \"${src.citekey}\" and section \"summary\", then " +
        "reply with that source's citekey, exact title and publication year.",
      tools = toolbox.tools(d.tools),
      schema = ResponseSchema.derived[ProbeAnswer](),
      maxIterations = math.min(cfg.step.maxIterations, 3),
      maxTokens = math.min(cfg.step.maxTokens, 512),
      agent = Some(d))
    val t0 = System.nanoTime()
    AgentsHandlers.attempt(client.model)(client.run(call)).map { res =>
      val use    = LlmClient.usage(res.usage)
      val answer = res.finalAnswer.toOption
      ProbeResult(
        model = client.model.wire,
        answer = answer,
        rawAnswer = LlmClient.rawAnswer(res.finalAnswer).take(2000),
        matchesRecord = answer.exists(a => AgentVerify.sameTitle(a.title, src.title) && a.year == src.year),
        toolCalls = res.toolCalls.toList.map(c => s"${c.toolName} ${c.input}"),
        iterations = res.iterations,
        finish = LlmClient.finish(res.finishReason),
        usage = use,
        costUsd = Some(billing.cost(use).toDouble),
        millis = (System.nanoTime() - t0) / 1000000)
    }

object AgentsHandlers:
  /** The server's providers over one [[CliRuntime]]. */
  def serverWiring(app: com.histmersion.core.AppConfig): (LlmClient.Factory, CliRuntime) =
    val rt = CliRuntime(app)
    (LlmClient.factory(rt), rt)

  /** [[attempt]], telling the plan's own usage limit (a model to pass over, [[ModelLimitReached]])
    * apart from a broken provider. */
  def call[A](model: ModelRef)(a: => A): Either[Either[ModelLimitReached, ApiError], A] =
    try Right(a)
    catch
      case e: ModelLimitReached =>
        scribe.warn(s"agent call to ${model.wire}: ${e.getMessage}")
        Left(Left(e))
      case e: InterruptedException => throw e
      case e: Exception => attempt(model)(throw e).left.map(Right(_))

  /** A provider call that threw (unreachable host, an API error, a malformed response) → a value,
    * logged with the model but never with the request (which can carry an API key header). */
  def attempt[A](model: ModelRef)(a: => A): Either[ApiError, A] =
    try Right(a)
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"agent call to ${model.wire} failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
        Left(ApiError(s"the call to ${model.wire} failed: ${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(600)))
