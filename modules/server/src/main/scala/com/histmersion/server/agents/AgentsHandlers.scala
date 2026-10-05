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
  * `clientFor` is the seam a test uses to put sttp-ai's scripted model in place of a provider. */
final class AgentsHandlers(deps: Deps, h: Handlers, clientFor: LlmClient.Factory):

  /** The server's own providers ([[LlmClient.factory]]: the HTTP ones and `claude-code`). */
  def this(deps: Deps, h: Handlers) = this(deps, h, LlmClient.factory(deps.cfg))

  private val cfg     = deps.cfg.agents
  private val ds      = deps.ds

  /** The acquisition toolset (OpenAlex/Crossref, refs, assess, followups). A `var` because it is built
    * AFTER [[Handlers]] (it needs the ingest queue), and attached by `Main` before the pool starts;
    * until then — and in a test that attaches nothing — the steps that need it say it is offline. */
  @volatile private var upstream: AgentUpstream = AgentUpstream.Offline

  def attach(u: AgentUpstream): Unit = upstream = u

  val prompts: Prompts     = Prompts(cfg.promptsDir)
  val toolbox: Toolbox     = Toolbox(h, () => upstream)
  val approvals: Approvals = Approvals(h, ds, cfg)

  /** The run pool. Constructed here, started by [[start]] (from `Main`, after the OCR pool). */
  val engine: Engine = Engine(ds, cfg, this, Map(
    Pipeline.EnrichSource     -> EnrichSourcePipeline(h, ds, cfg, prompts, () => upstream, approvals),
    Pipeline.ReviewArticle    -> ReviewArticlePipeline(h, ds, cfg, prompts, approvals),
    Pipeline.PlanGoal         -> PlanGoalPipeline(h, ds, cfg, prompts, () => upstream),
    Pipeline.CompileArticle   -> CompileArticlePipeline(h, ds, cfg, prompts,
                                   (req, parent) => startRunAs(req, Origin.Run, Some(parent))),
    Pipeline.CurrencyCheck    -> CurrencyCheckPipeline(h, ds, cfg, () => upstream, approvals),
    Pipeline.AcquisitionSweep -> AcquisitionSweepPipeline(h, ds, cfg, prompts, () => upstream)))

  /** The goal schedules (started with the pool, and only when agents are enabled). */
  val scheduler: Scheduler = Scheduler(ds, cfg, this, h)

  /** Start the run pool and the scheduler — only when agents are enabled; off, no thread is started
    * and queued or paused runs simply wait. Returns how many interrupted runs were paused. */
  def start(): Int =
    if !cfg.enabled then 0
    else
      val paused = engine.start()
      scheduler.start()
      paused

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

  /** A model that can run: PRICED (an unpriced model is refused, never treated as free), and with a
    * client its provider can build (a hosted provider needs its key; `claude-code` a loopback server). */
  def resolve(m: ModelRef): Either[String, (LlmClient, Map[String, Price])] =
    for
      ps <- prices
      _  <- Agents.Prices.cost(ps, m.model, Usage.Zero)
      c  <- clientFor(cfg, m)
    yield (c, ps)

  /** The model chains and allowances in effect, and the choice before every LLM call. */
  val picker: ModelPicker = ModelPicker(ds, cfg, resolve)

  /** A role's chain can run at all: at least one of its models is priced and buildable (allowances
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
      // every role the pipeline uses must have a chain with a priced model it can build, NOW
      _      <- p.roles.foldLeft[Either[ApiError, Unit]](Right(())) { (acc, role) =>
                  acc.flatMap(_ => runnable(role, req.model).left.map(ApiError(_)))
                }
      _      <- options(p, req, target)
      goal   <- goalOf(p, target, req.goal.orElse(parent.flatMap(_.goal)))
      usd    <- positive(req.budgetUsd.orElse(goal.flatMap(goalBudget)).getOrElse(cfg.budgets.runUsd), "budget-usd")
      tok    <- positive(req.budgetTokens.getOrElse(cfg.budgets.runTokens).toDouble, "budget-tokens")
    yield
      val clean = (xs: List[String]) => Option(xs.map(_.trim).filter(_.nonEmpty).distinct).filter(_.nonEmpty)
      val params = RunParams(fix = req.fix.filter(identity), rounds = req.rounds,
        title = req.title.map(_.trim).filter(_.nonEmpty), kind = req.kind.map(_.trim.toLowerCase).filter(_.nonEmpty),
        query = req.query.map(_.trim).filter(_.nonEmpty), topics = clean(req.topics), tags = clean(req.tags))
      val run = AgentRepo.create(ds, p, target, req.model.map(_.trim).filter(_.nonEmpty), BigDecimal(usd), tok.toLong,
        params, req.acquireItem, goal, parent.map(_.id), origin)
      engine.submit(run.id)
      run

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
                   case PickFailure.Exhausted(m, _) => ApiError(m)
                 }
      result  <- probeWith(picked.client, picked.prices, src.source)
    yield result

  private def probeWith(client: LlmClient, ps: Map[String, Price], src: SourceDetail): Either[ApiError, ProbeResult] =
    val call = LlmCall[ProbeAnswer](
      system = "You answer questions about a research database. Use the get_source tool to read the " +
        "record; never answer from memory. Reply with the JSON object the response schema describes.",
      user = s"Call get_source with citekey \"${src.citekey}\" and section \"summary\", then " +
        "reply with that source's citekey, exact title and publication year.",
      tools = toolbox.tools(List(ToolName.GetSource)),
      schema = ResponseSchema.derived[ProbeAnswer](),
      maxIterations = math.min(cfg.step.maxIterations, 3),
      maxTokens = math.min(cfg.step.maxTokens, 512))
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
        costUsd = Agents.Prices.cost(ps, client.model.model, use).toOption.map(_.toDouble),
        millis = (System.nanoTime() - t0) / 1000000)
    }

object AgentsHandlers:
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
