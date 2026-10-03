package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Embedded research agents (docs/research-embedded-agents.md): the pure vocabulary — providers,
  * roles, model references, prices and cost, run and step states, pipelines — and the wire DTOs of
  * `/api/agents/…`. No I/O here; the server runs the agents, the CLI and web UI only read this. */
object Agents:

  /** Who serves a model. `Local` is any OpenAI-compatible endpoint (Ollama, llama.cpp, vLLM). */
  enum ProviderKind(val wire: String):
    case Anthropic extends ProviderKind("anthropic")
    case OpenAI    extends ProviderKind("openai")
    case Local     extends ProviderKind("local")

  object ProviderKind:
    def parse(s: String): Option[ProviderKind] = values.find(_.wire == s.trim.toLowerCase)

  /** What a step asks a model to be. Each role maps to a `provider:model` in config. */
  enum Role(val wire: String):
    case Probe      extends Role("probe")
    case Extractor  extends Role("extractor")
    case Reviewer   extends Role("reviewer")
    case ColdReader extends Role("cold-reader")
    case Skeptic    extends Role("skeptic")
    case Fixer      extends Role("fixer")

  object Role:
    def parse(s: String): Option[Role] = values.find(_.wire == s.trim.toLowerCase)

  /** `provider:model`. The model id may itself contain colons (`local:qwen2.5-coder:32b`), so only the
    * FIRST colon separates the provider. */
  final case class ModelRef(provider: ProviderKind, model: String):
    def wire: String = s"${provider.wire}:$model"

  object ModelRef:
    def parse(s: String): Either[String, ModelRef] =
      val t = s.trim
      t.indexOf(':') match
        case -1 => Left(s"'$t' is not provider:model (provider: ${ProviderKind.values.map(_.wire).mkString(", ")})")
        case i =>
          val (p, m) = (t.substring(0, i), t.substring(i + 1).trim)
          ProviderKind.parse(p) match
            case None                 => Left(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.values.map(_.wire).mkString(", ")})")
            case Some(_) if m.isEmpty => Left(s"'$t' names no model")
            case Some(k)              => Right(ModelRef(k, m))

  // ── usage, prices and cost ─────────────────────────────────────────────────────────────────────

  /** Tokens of one or more LLM calls. `input` counts ALL prompt tokens, cached ones included (the
    * provider-normalised convention sttp-ai uses); `cacheRead`/`cacheWrite` are subsets of it. */
  final case class Usage(input: Long = 0, output: Long = 0, cacheRead: Long = 0, cacheWrite: Long = 0)
      derives KindlingsCodecAsObject:
    def +(o: Usage): Usage = Usage(input + o.input, output + o.output, cacheRead + o.cacheRead, cacheWrite + o.cacheWrite)
    def total: Long = input + output

  object Usage:
    val Zero: Usage = Usage()
    given Schema[Usage] = KindlingsSchema.derived[Usage].schema

  /** USD per million tokens. Cache rates default to the input rate when not given. */
  final case class Price(input: BigDecimal, output: BigDecimal, cacheRead: Option[BigDecimal] = None,
      cacheWrite: Option[BigDecimal] = None)

  object Prices:
    private val Million = BigDecimal(1000000)

    /** `"<model>=<in>/<out>[/<cache-read>/<cache-write>]; …"` → model id → [[Price]]. Blank = no prices.
      * Every malformed entry is reported, not skipped: a typo must not make a model silently free. */
    def parse(s: String): Either[String, Map[String, Price]] =
      val entries = s.split(";").toList.map(_.trim).filter(_.nonEmpty)
      val parsed = entries.map { e =>
        e.lastIndexOf('=') match
          case -1 => Left(s"'$e' is not <model>=<input>/<output>")
          case i =>
            val model = e.substring(0, i).trim
            val nums  = e.substring(i + 1).split("/").toList.map(_.trim)
            val dec   = nums.map(n => scala.util.Try(BigDecimal(n)).toOption.filter(_ >= 0))
            if model.isEmpty then Left(s"'$e' names no model")
            else if dec.exists(_.isEmpty) then Left(s"'$e': prices must be non-negative numbers")
            else dec.flatten match
              case List(in, out)         => Right(model -> Price(in, out))
              case List(in, out, cr, cw) => Right(model -> Price(in, out, Some(cr), Some(cw)))
              case _                     => Left(s"'$e': give 2 prices (input/output) or 4 (input/output/cache-read/cache-write)")
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(parsed.collect { case Right(kv) => kv }.toMap)
        case errs => Left(errs.mkString("; "))

    /** The cost of `u` on `model`. An UNPRICED model is an error, never zero — the inverse of
      * sttp-ai's `PriceTable`, which counts an unknown model as free and so would never trip a cost
      * budget. */
    def cost(prices: Map[String, Price], model: String, u: Usage): Either[String, BigDecimal] =
      prices.get(model).toRight(s"model '$model' has no price — add it to agents.prices (HISTMERSION_AGENTS_PRICES), e.g. \"$model=0/0\" for a free local model").map { p =>
        val plain = math.max(u.input - u.cacheRead - u.cacheWrite, 0L)
        (BigDecimal(plain) * p.input + BigDecimal(u.cacheRead) * p.cacheRead.getOrElse(p.input) +
          BigDecimal(u.cacheWrite) * p.cacheWrite.getOrElse(p.input) + BigDecimal(u.output) * p.output) / Million
      }

  // ── runs and steps ─────────────────────────────────────────────────────────────────────────────

  /** The `jobs.kind` of an agent run. */
  val JobKind = "agent"

  /** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server
    * restart, a person's pause); `Cancelled` and the other terminal states are not. */
  enum RunState(val wire: String):
    case Queued    extends RunState("queued")
    case Running   extends RunState("running")
    case Paused    extends RunState("paused")
    case Done      extends RunState("done")
    case Failed    extends RunState("failed")
    case Cancelled extends RunState("cancelled")

    def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

  object RunState:
    def parse(s: String): Option[RunState] = values.find(_.wire == s.trim.toLowerCase)

    /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
    def next(from: RunState, to: RunState): Option[RunState] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                    => Some(to)
      case _                                                                          => None

  /** Why a run is paused — the reason is shown and decides whether a plain resume can help. */
  enum PauseReason(val wire: String):
    case Budget      extends PauseReason("budget")
    case Interrupted extends PauseReason("interrupted")
    case Requested   extends PauseReason("requested")
    case Provider    extends PauseReason("provider")

  enum StepKind(val wire: String):
    case Code extends StepKind("code")
    case Llm  extends StepKind("llm")

  object StepKind:
    def parse(s: String): Option[StepKind] = values.find(_.wire == s)

  enum StepStatus(val wire: String):
    case Done   extends StepStatus("done")
    case Failed extends StepStatus("failed")

  object StepStatus:
    def parse(s: String): Option[StepStatus] = values.find(_.wire == s)

  /** The pipelines the engine can run. Each one is Scala code (steps, gates, validation) plus one
    * prompt per LLM step. */
  enum Pipeline(val wire: String, val version: Int, val targetKind: String, val describe: String):
    case EnrichSource extends Pipeline("enrich-source", 1, "source",
      "read one source and propose its summary, classification, period, findings (each with a verbatim quote) and notes; code verifies and patches")

  object Pipeline:
    def parse(s: String): Option[Pipeline] = values.find(_.wire == s.trim.toLowerCase)

  // ── wire DTOs ─────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/probe` — one LLM call with one read-only tool (`get_source`) and a typed answer:
    * the connectivity and tool-calling check for a configured model. */
  final case class ProbeRequest(citekey: String, model: Option[String] = None) derives KindlingsCodecAsObject
  object ProbeRequest:
    given Schema[ProbeRequest] = KindlingsSchema.derived[ProbeRequest].schema

  /** The typed answer the probe asks for. */
  final case class ProbeAnswer(citekey: String, title: String, year: Option[Int]) derives KindlingsCodecAsObject
  object ProbeAnswer:
    given Schema[ProbeAnswer] = KindlingsSchema.derived[ProbeAnswer].schema

  final case class ProbeResult(
      model: String,
      answer: Option[ProbeAnswer],
      rawAnswer: String,
      matchesRecord: Boolean,
      toolCalls: List[String],
      iterations: Int,
      finish: String,
      usage: Usage,
      costUsd: Option[Double],
      millis: Long
  ) derives KindlingsCodecAsObject
  object ProbeResult:
    given Schema[ProbeResult] = KindlingsSchema.derived[ProbeResult].schema

  /** `POST /api/agents/runs` — start a pipeline on a target. `model` overrides every role of the run
    * (`provider:model`); the budgets override `agents.budgets.run-*`. */
  final case class RunRequest(
      pipeline: String,
      target: String,
      model: Option[String] = None,
      budgetUsd: Option[Double] = None,
      budgetTokens: Option[Long] = None
  ) derives KindlingsCodecAsObject
  object RunRequest:
    given Schema[RunRequest] = KindlingsSchema.derived[RunRequest].schema

  /** One run, as listed. Amounts are totals over every recorded step. */
  final case class RunInfo(
      id: String,
      pipeline: String,
      target: String,
      status: String,
      progress: Option[String] = None,
      error: Option[String] = None,
      model: Option[String] = None,
      usage: Usage = Usage.Zero,
      costUsd: Double = 0,
      budgetUsd: Double = 0,
      budgetTokens: Long = 0,
      warnings: List[String] = Nil,
      createdAt: String = "",
      updatedAt: String = ""
  ) derives KindlingsCodecAsObject
  object RunInfo:
    given Schema[RunInfo] = KindlingsSchema.derived[RunInfo].schema

  /** One checkpointed step. `output` is the step's recorded result (JSON), truncated for display. */
  final case class StepInfo(
      seq: Int,
      name: String,
      kind: String,
      attempt: Int,
      status: String,
      model: Option[String] = None,
      promptHash: Option[String] = None,
      usage: Usage = Usage.Zero,
      costUsd: Double = 0,
      error: Option[String] = None,
      output: Option[String] = None,
      startedAt: String = "",
      finishedAt: String = ""
  ) derives KindlingsCodecAsObject
  object StepInfo:
    given Schema[StepInfo] = KindlingsSchema.derived[StepInfo].schema

  /** One traced message of an LLM step: the assistant's text, or a tool call with its (truncated)
    * result. */
  final case class MessageInfo(seq: Int, n: Int, role: String, content: Option[String] = None,
      toolName: Option[String] = None, toolInput: Option[String] = None, toolResult: Option[String] = None,
      latencyMs: Option[Int] = None) derives KindlingsCodecAsObject
  object MessageInfo:
    given Schema[MessageInfo] = KindlingsSchema.derived[MessageInfo].schema

  final case class RunDetail(run: RunInfo, steps: List[StepInfo], messages: List[MessageInfo])
      derives KindlingsCodecAsObject
  object RunDetail:
    given Schema[RunDetail] = KindlingsSchema.derived[RunDetail].schema

  /** `GET /api/agents/models` — each role, the model it resolves to, and whether it can run. */
  final case class RoleModel(role: String, model: Option[String], ready: Boolean, problem: Option[String])
      derives KindlingsCodecAsObject
  object RoleModel:
    given Schema[RoleModel] = KindlingsSchema.derived[RoleModel].schema

  final case class ModelsInfo(enabled: Boolean, roles: List[RoleModel], pipelines: List[String],
      maxConcurrentRuns: Int, problems: List[String]) derives KindlingsCodecAsObject
  object ModelsInfo:
    given Schema[ModelsInfo] = KindlingsSchema.derived[ModelsInfo].schema
