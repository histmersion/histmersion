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
    /** plan-goal: sub-goals, queries and candidate works for a goal; acquisition-sweep: ranks them. */
    case Planner    extends Role("planner")
    /** compile-article: drafts the article (or the section to append) from the verified claims. */
    case Writer     extends Role("writer")

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
    * restart, a person's pause); `WaitingApproval` holds no worker and goes back to the queue when a
    * person decides the approval it waits on; `Cancelled` and the other terminal states are final. */
  enum RunState(val wire: String):
    case Queued          extends RunState("queued")
    case Running         extends RunState("running")
    case Paused          extends RunState("paused")
    case WaitingApproval extends RunState("waiting")
    case Done            extends RunState("done")
    case Failed          extends RunState("failed")
    case Cancelled       extends RunState("cancelled")

    def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

  object RunState:
    def parse(s: String): Option[RunState] = values.find(_.wire == s.trim.toLowerCase)

    /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
    def next(from: RunState, to: RunState): Option[RunState] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Running, WaitingApproval)                                                => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                    => Some(to)
      case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                  => Some(to)
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

  /** Who started a run: a person (CLI, web UI, MCP), a goal's schedule, or another run (a
    * compile-article run chains its review). A scheduled run that fails or pauses leaves a backlog
    * item; nobody was watching it. */
  enum Origin(val wire: String):
    case Person    extends Origin("person")
    case Scheduler extends Origin("scheduler")
    case Run       extends Origin("run")

  object Origin:
    def parse(s: String): Option[Origin] = values.find(_.wire == s.trim.toLowerCase)

  /** The pipelines the engine can run. Each one is Scala code (steps, gates, validation) plus one
    * prompt per LLM step. */
  enum Pipeline(val wire: String, val version: Int, val targetKind: TargetKind, val roles: List[Role],
      val describe: String):
    case EnrichSource extends Pipeline("enrich-source", 2, TargetKind.Source, List(Role.Extractor),
      "read one source and propose its summary, classification, period, findings (each with a verbatim quote) and notes; code verifies and patches")
    case ReviewArticle extends Pipeline("review-article", 1, TargetKind.Article,
      List(Role.Reviewer, Role.ColdReader, Role.Skeptic, Role.Fixer),
      "the adversarial review panel on one article (rules reviewer, cold reader, skeptic in parallel), merged by code; with fix, a fixer revises it and the panel re-runs until no major finding remains")
    case PlanGoal extends Pipeline("plan-goal", 1, TargetKind.Goal, List(Role.Planner),
      "propose sub-goals, search queries and candidate works (DOIs checked upstream by code) for one goal; nothing is created until a person approves the plan")
    case CompileArticle extends Pipeline("compile-article", 1, TargetKind.Article, List(Role.Extractor, Role.Writer),
      "compile a new article, or a section to append to an existing one, from the sources a filter returns: verified claim table, evidence model, draft, code self-check, then a person approves before anything is written, and a review-article run with fix follows")
    case CurrencyCheck extends Pipeline("currency-check", 1, TargetKind.Goal, Nil,
      "assess and followups over a goal's sources (no model): retractions applied as assess does, disagreement edges to sources in the corpus proposed for approval, citing papers outside it queued for acquisition")
    case AcquisitionSweep extends Pipeline("acquisition-sweep", 1, TargetKind.Goal, List(Role.Planner),
      "discover new works for a goal's queries, a model ranks them against the goal, and the relevant ones become acquisition items — never one already in the corpus or the queue")

  object Pipeline:
    def parse(s: String): Option[Pipeline] = values.find(_.wire == s.trim.toLowerCase)

  /** What a pipeline works on: a source (by citekey), an article (by slug) or a goal (by slug). */
  enum TargetKind(val wire: String):
    case Source  extends TargetKind("source")
    case Article extends TargetKind("article")
    case Goal    extends TargetKind("goal")

  object TargetKind:
    def parse(s: String): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)

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
      budgetTokens: Option[Long] = None,
      /** review-article: let the fixer revise the article between rounds (default: review only). */
      fix: Option[Boolean] = None,
      /** review-article: at most this many panel rounds (default `agents.review.max-rounds`). */
      rounds: Option[Int] = None,
      /** enrich-source: the acquisition item this run fulfils; ticked off when the run finishes. */
      acquireItem: Option[Int] = None,
      /** compile-article: the title and kind of a NEW article (ignored when the slug exists). */
      title: Option[String] = None,
      kind: Option[String] = None,
      /** compile-article: the filter whose sources the article is compiled from — free text, and
        * topics/tags (all-of). At least one for a new article; an existing one defaults to its title. */
      query: Option[String] = None,
      topics: List[String] = Nil,
      tags: List[String] = Nil,
      /** The goal the run works for (its links and log carry it). */
      goal: Option[String] = None
  ) derives KindlingsCodecAsObject
  object RunRequest:
    given Schema[RunRequest] = KindlingsSchema.derived[RunRequest].schema

  /** A run's pipeline options, stored with the run (`jobs.params`) so a resumed run sees the same
    * ones. Every field is optional: a run started before an option existed decodes as its default. */
  final case class RunParams(fix: Option[Boolean] = None, rounds: Option[Int] = None,
      title: Option[String] = None, kind: Option[String] = None, query: Option[String] = None,
      topics: Option[List[String]] = None, tags: Option[List[String]] = None) derives KindlingsCodecAsObject:
    def fixes: Boolean = fix.contains(true)
  object RunParams:
    given Schema[RunParams] = KindlingsSchema.derived[RunParams].schema

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
      updatedAt: String = "",
      targetKind: String = "",
      params: RunParams = RunParams(),
      acquireItem: Option[Int] = None,
      /** The goal the run works for, the run that started it, and who did. */
      goal: Option[String] = None,
      parent: Option[String] = None,
      origin: Option[String] = None
  ) derives KindlingsCodecAsObject:
    def isWaiting: Boolean = status == RunState.WaitingApproval.wire
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

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  /** A change an agent run proposed that a person may have to accept before code applies it: a
    * source's status, a typed disagreement edge between two sources, a goal's plan, or an article a
    * run compiled. `blocking` = the run WAITS for the decision (`waiting_approval`) and applies it
    * itself when it resumes; the other kinds are applied by the decision and the run never waits. */
  enum ApprovalKind(val wire: String, val blocking: Boolean):
    case StatusChange   extends ApprovalKind("status-change", false)
    case Edge           extends ApprovalKind("edge", false)
    case GoalPlan       extends ApprovalKind("goal-plan", true)
    case ArticlePublish extends ApprovalKind("article-publish", true)

  object ApprovalKind:
    def parse(s: String): Option[ApprovalKind] = values.find(_.wire == s.trim.toLowerCase)

    given WireEnumValues[ApprovalKind]:
      def all: Array[ApprovalKind] = ApprovalKind.values
      def wire(e: ApprovalKind): String = e.wire
      def fromWire(s: String): Option[ApprovalKind] = parse(s)

    given io.circe.Encoder[ApprovalKind] = WireEnum.encoder
    given io.circe.Decoder[ApprovalKind] = WireEnum.decoder
    given Schema[ApprovalKind]           = WireEnum.schema

  /** `Failed` = a person approved it but applying it failed (the source changed or went away); the
    * reason is kept in `result`. */
  enum ApprovalState(val wire: String):
    case Open     extends ApprovalState("open")
    case Approved extends ApprovalState("approved")
    case Rejected extends ApprovalState("rejected")
    case Failed   extends ApprovalState("failed")

  object ApprovalState:
    def parse(s: String): Option[ApprovalState] = values.find(_.wire == s.trim.toLowerCase)

  /** How a kind of proposal is handled: `Manual` waits for a person, `Auto` is applied at once (and
    * still recorded, already approved). */
  enum ApprovalMode(val wire: String):
    case Manual extends ApprovalMode("manual")
    case Auto   extends ApprovalMode("auto")

  object ApprovalMode:
    def parse(s: String): Option[ApprovalMode] = values.find(_.wire == s.trim.toLowerCase)

  /** The decisions a person can take on an open approval. */
  enum Decision(val wire: String):
    case Approve extends Decision("approve")
    case Reject  extends Decision("reject")

  object Decision:
    def parse(s: String): Option[Decision] = values.find(_.wire == s.trim.toLowerCase)

  /** One proposal, as listed. For a status change `status` is the proposed status (and `current` the
    * one it had when proposed); for an edge, `edgeKind` runs from `citekey` to `target`. `quote` is
    * the passage of `citekey`'s text the proposal rests on, verified by code before it was recorded.
    * A goal plan or an article to publish is about `subject` (a goal or article slug; `citekey` is
    * empty) and carries its proposal in `plan` or `article`. */
  final case class ApprovalInfo(
      id: Long,
      runId: Option[String],
      kind: String,
      citekey: String,
      status: Option[String] = None,
      current: Option[String] = None,
      edgeKind: Option[String] = None,
      target: Option[String] = None,
      evidence: String = "",
      quote: Option[String] = None,
      state: String = ApprovalState.Open.wire,
      note: Option[String] = None,
      result: Option[String] = None,
      createdAt: String = "",
      decidedAt: Option[String] = None,
      subject: Option[String] = None,
      plan: Option[GoalPlans.Plan] = None,
      article: Option[GoalPlans.ArticleDraft] = None
  ) derives KindlingsCodecAsObject:
    def approvalKind: Option[ApprovalKind] = ApprovalKind.parse(kind)
  object ApprovalInfo:
    given Schema[ApprovalInfo] = KindlingsSchema.derived[ApprovalInfo].schema

  /** `POST /api/agents/approvals/{id}/{approve|reject}` — an optional note kept with the decision. */
  final case class DecisionRequest(note: Option[String] = None) derives KindlingsCodecAsObject
  object DecisionRequest:
    given Schema[DecisionRequest] = KindlingsSchema.derived[DecisionRequest].schema
