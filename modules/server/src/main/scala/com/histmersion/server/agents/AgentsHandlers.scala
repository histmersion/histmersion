package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.db.AgentRepo
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.ResponseSchema

/** The ONE implementation of the embedded-agent operations (docs/research-embedded-agents.md):
  * the model check, the probe and (through [[Engine]]) pipeline runs. REST ([[AgentsRoutes]]) and the
  * CLI reach it; it is held as `Handlers.agents`.
  *
  * `clientFor` is the seam a test uses to put sttp-ai's scripted model in place of a provider. */
final class AgentsHandlers(deps: Deps, h: Handlers,
    clientFor: (AgentsConfig, ModelRef) => Either[String, LlmClient] = LlmClient.fromConfig):

  private val cfg     = deps.cfg.agents
  private val ds      = deps.ds
  val toolbox: Toolbox = Toolbox(h)

  /** The run pool. Constructed here, started by [[start]] (from `Main`, after the OCR pool). */
  val engine: Engine = Engine(ds, cfg, this,
    Map(Pipeline.EnrichSource -> EnrichSourcePipeline(h, ds, cfg)))

  /** Start the run pool — only when agents are enabled; off, no thread is started and queued or
    * paused runs simply wait. Returns how many interrupted runs were paused. */
  def start(): Int = if cfg.enabled then engine.start() else 0

  /** Off unless `agents.enabled`: every agent operation but the read-only model listing refuses. */
  private def enabled: Either[ApiError, Unit] =
    if cfg.enabled then Right(())
    else Left(ApiError("embedded agents are off — set HISTMERSION_AGENTS_ENABLED=true on the server to use them"))

  /** The price table, parsed once per call (config is immutable; parsing is microseconds). */
  private def prices: Either[String, Map[String, Price]] =
    Agents.Prices.parse(cfg.prices).left.map(m => s"agents.prices: $m")

  /** A model that can run: parsed, PRICED (an unpriced model is refused, never treated as free), and
    * with a client its provider can build (a hosted provider needs its key). */
  def resolve(ref: String): Either[String, (LlmClient, Map[String, Price])] =
    for
      m  <- ModelRef.parse(ref)
      ps <- prices
      _  <- Agents.Prices.cost(ps, m.model, Usage.Zero)
      c  <- clientFor(cfg, m)
    yield (c, ps)

  /** The model of a role: the run's override, else `agents.roles.<role>`. */
  def modelFor(role: Role, overrideRef: Option[String]): Either[String, String] =
    overrideRef.map(_.trim).filter(_.nonEmpty).orElse(Option(cfg.role(role)).map(_.trim).filter(_.nonEmpty))
      .toRight(s"no model for the ${role.wire} role — set agents.roles.${role.wire} " +
        s"(HISTMERSION_AGENTS_ROLE_${role.wire.toUpperCase.replace('-', '_')}) to provider:model, or pass one")

  /** `GET /api/agents/models` — what each role resolves to and whether it could run now. Builds no
    * connection: a client is constructed, nothing is sent. */
  def models: ModelsInfo =
    val roles = Role.values.toList.map { r =>
      modelFor(r, None) match
        case Left(m)    => RoleModel(r.wire, None, ready = false, Some(m))
        case Right(ref) => resolve(ref) match
          case Left(m)  => RoleModel(r.wire, Some(ref), ready = false, Some(m))
          case Right(_) => RoleModel(r.wire, Some(ref), ready = true, None)
    }
    val problems = prices.left.toOption.toList ++
      Option.when(!cfg.enabled)("embedded agents are off (agents.enabled = false)")
    ModelsInfo(cfg.enabled, roles, Pipeline.values.toList.map(_.wire), cfg.maxConcurrentRuns, problems)

  // ── runs ───────────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/runs` — validated up front (pipeline, target, model, prices, budgets) so a
    * mistake is a 400 now, not a failed run later; then queued. */
  def startRun(req: RunRequest): Either[ApiError, RunInfo] =
    for
      _    <- enabled
      p    <- Pipeline.parse(req.pipeline).toRight(ApiError(
                s"unknown pipeline '${req.pipeline}' (one of: ${Pipeline.values.map(_.wire).mkString(", ")})"))
      ck   <- h.getSource(req.target, body = false).map(_.source.citekey)
      ref  <- modelFor(Role.Extractor, req.model).left.map(ApiError(_))
      _    <- resolve(ref).left.map(ApiError(_))
      usd  <- positive(req.budgetUsd.getOrElse(cfg.budgets.runUsd), "budget-usd")
      tok  <- positive(req.budgetTokens.getOrElse(cfg.budgets.runTokens).toDouble, "budget-tokens")
    yield
      val run = AgentRepo.create(ds, p, ck, req.model.map(_.trim).filter(_.nonEmpty), BigDecimal(usd), tok.toLong)
      engine.submit(run.id)
      run

  /** Budgets are stored to the micro-dollar (numeric(12,6)): anything smaller would round to 0. */
  private def positive(v: Double, what: String): Either[ApiError, Double] =
    if v >= 0.000001 then Right(v) else Left(ApiError(s"$what must be at least 0.000001"))

  def runs(status: Option[String], limit: Option[Int]): Either[ApiError, List[RunInfo]] =
    status.map(_.trim).filter(_.nonEmpty) match
      case Some(st) if RunState.parse(st).isEmpty =>
        Left(ApiError(s"unknown status '$st' (one of: ${RunState.values.map(_.wire).mkString(", ")})"))
      case st => Right(AgentRepo.list(ds, st, deps.cfg.limits.page(limit, deps.cfg.limits.search)).toList)

  def run(id: String): Either[ApiError, RunDetail] =
    AgentRepo.get(ds, id).toRight(ApiError(s"No agent run '$id'", Some(Write.CodeNotFound)))
      .map(r => RunDetail(r, AgentRepo.steps(ds, id, cfg.trace.maxToolResultChars), AgentRepo.messages(ds, id)))

  def control(id: String, action: String, budgetUsd: Option[Double], budgetTokens: Option[Long]): Either[ApiError, RunInfo] =
    action.trim.toLowerCase match
      case "pause"  => engine.pause(id)
      case "resume" => enabled.flatMap(_ => engine.resume(id, budgetUsd, budgetTokens))
      case "cancel" => engine.cancel(id)
      case other    => Left(ApiError(s"unknown action '$other' (one of: ${AgentsApi.Actions.mkString(", ")})"))

  /** `POST /api/agents/probe` — one LLM call offered exactly one read-only tool (`get_source`) and
    * asked for a typed answer about one source. It proves, end to end, that the model is reachable,
    * calls tools, and returns structured output — and the answer is checked against the record. */
  def probe(req: ProbeRequest): Either[ApiError, ProbeResult] =
    for
      _       <- enabled
      src     <- h.getSource(req.citekey, body = false)
      ref     <- modelFor(Role.Probe, req.model).left.map(ApiError(_))
      resolved <- resolve(ref).left.map(ApiError(_))
      result  <- probeWith(resolved._1, resolved._2, src.source)
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
  /** A provider call that threw (unreachable host, an API error, a malformed response) → a value,
    * logged with the model but never with the request (which can carry an API key header). */
  def attempt[A](model: ModelRef)(a: => A): Either[ApiError, A] =
    try Right(a)
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"agent call to ${model.wire} failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
        Left(ApiError(s"the call to ${model.wire} failed: ${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(600)))
