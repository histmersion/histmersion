package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Embedded research agents (docs/research-embedded-agents.md): the pure vocabulary — providers,
  * roles, model references, prices and cost, run and step states, pipelines — and the wire DTOs of
  * `/api/agents/…`. No I/O here; the server runs the agents, the CLI and web UI only read this. */
object Agents:

  /** Who serves a model. `Local` is any OpenAI-compatible endpoint (llama.cpp, LM Studio, vLLM,
    * Ollama). `ClaudeCode` runs the person's own `claude` CLI headlessly (`claude -p`) on their own
    * Claude login — their plan's usage, never an API key (docs/research-model-providers.md §1). */
  enum ProviderKind(val wire: String):
    case Anthropic  extends ProviderKind("anthropic")
    case OpenAI     extends ProviderKind("openai")
    case Local      extends ProviderKind("local")
    case ClaudeCode extends ProviderKind("claude-code")
    /** The person's own `codex` CLI (`codex exec`) on its ChatGPT plan login. */
    case Codex      extends ProviderKind("codex")
    /** `opencode run`; the model is `<provider>/<model>` as opencode names it (`openai/gpt-…`). */
    case Opencode   extends ProviderKind("opencode")

    /** Runs a local command-line agent (one process per step attempt) rather than an HTTP API. */
    def isCli: Boolean = this == ClaudeCode || this == Codex || this == Opencode

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
    /** What counts against a run's token budget: uncached input, cache writes and output in full,
      * cache reads at `cacheReadWeight` (`input` already includes the cached tokens). */
    def budgeted(cacheReadWeight: Double): Long =
      math.max(0L, input - cacheRead) + output + math.round(cacheRead.toDouble * math.max(0.0, cacheReadWeight))

  object Usage:
    val Zero: Usage = Usage()
    given Schema[Usage] = KindlingsSchema.derived[Usage].schema

  /** USD per million tokens. Cache rates default to the input rate when not given. */
  final case class Price(input: BigDecimal, output: BigDecimal, cacheRead: Option[BigDecimal] = None,
      cacheWrite: Option[BigDecimal] = None):
    /** The cost of `u` at this price, in USD. */
    def cost(u: Usage): BigDecimal =
      val plain = math.max(u.input - u.cacheRead - u.cacheWrite, 0L)
      (BigDecimal(plain) * input + BigDecimal(u.cacheRead) * cacheRead.getOrElse(input) +
        BigDecimal(u.cacheWrite) * cacheWrite.getOrElse(input) + BigDecimal(u.output) * output) / Prices.Million

    /** `in/out[/cache-read/cache-write]`, as `agents.prices` writes it. */
    def text: String =
      val n = (d: BigDecimal) => d.bigDecimal.stripTrailingZeros.toPlainString
      (List(input, output) ++ cacheRead.zip(cacheWrite).toList.flatMap((r, w) => List(r, w))).map(n).mkString("/")

  /** How a model's calls are paid for. A model on a subscription plan (`claude-code` and `codex` on
    * their logins, `opencode:openai/…` on a ChatGPT login) is governed by its plan pool's floors
    * (docs/research-plan-quotas.md), not by USD: it needs no price and costs 0 USD against the run's
    * and the day's budgets — its tokens still count against the run's token budget. Every other model
    * is metered and must be priced. */
  enum Billing:
    case Plan(pool: PlanQuota.PoolId)
    case Metered(price: Price)

    /** What `u` costs against the USD budgets. */
    def cost(u: Usage): BigDecimal = this match
      case Plan(_)    => BigDecimal(0)
      case Metered(p) => p.cost(u)

    /** As `agent models` and the doctor show it. */
    def describe: String = this match
      case Plan(p)    => s"plan pool: ${p.wire}"
      case Metered(p) => s"price: ${p.text} USD per million tokens"

  object Prices:
    private[Agents] val Million = BigDecimal(1000000)

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
      prices.get(model).toRight(unpriced(model)).map(_.cost(u))

    /** How `m` is paid for: its plan pool when it draws on one (`pool`, from [[PlanQuota.poolOf]]) —
      * no price needed — else its price. A METERED model with no price is an error, never free: a
      * typo must not make a model silently free. */
    def billing(prices: Map[String, Price], m: ModelRef, pool: Option[PlanQuota.PoolId]): Either[String, Billing] =
      pool match
        case Some(p) => Right(Billing.Plan(p))
        case None    => prices.get(m.model).map(Billing.Metered(_)).toRight(unpriced(m.model) + metered(m))

    private def unpriced(model: String): String =
      s"model '$model' has no price — add it to agents.prices (HISTMERSION_AGENTS_PRICES), e.g. \"$model=0/0\" for a free local model"

    /** Why a command-line model is metered, when it would otherwise draw on a plan. */
    private def metered(m: ModelRef): String = m.provider match
      case ProviderKind.ClaudeCode => " (claude-code is set to use an API key, so it bills the API, not the plan)"
      case ProviderKind.Codex      => " (codex is set to use an API key, so it bills the API, not the plan)"
      case ProviderKind.Opencode   => " (an opencode model is metered unless it is openai/… on a ChatGPT login)"
      case _                       => ""

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
    /** Every model of a role's chain is below its allowance; the run resumes by itself once a window
      * frees enough (`jobs.resume_at`). */
    case Allowance   extends PauseReason("allowance")
    /** Every model of a role's chain draws on a subscription pool below its floor (or held), and the
      * chain may not fall through to a paid key; the run resumes by itself at the earliest reset. */
    case PlanQuota   extends PauseReason("plan-quota")
    /** The stall watchdog: the run gave no sign of life (no step boundary, no line from its CLI) for
      * `agents.watchdog.stall-minutes`; its processes were killed and it resumes by itself shortly. */
    case Stalled     extends PauseReason("stalled")

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

  /** Who started a run: a person (CLI, web UI, MCP), a goal's schedule, another run (a
    * compile-article run chains its review), or an ingest job (auto-enrich). A scheduled or ingest
    * run that fails or pauses leaves a backlog item; nobody was watching it. */
  enum Origin(val wire: String):
    case Person    extends Origin("person")
    case Scheduler extends Origin("scheduler")
    case Run       extends Origin("run")
    /** An ingest job queued it for the source it brought in (`agents.auto-enrich`). */
    case Ingest    extends Origin("ingest")
    /** A process manager dispatched it for one of its work items (docs/research-process-managers.md). */
    case Manager   extends Origin("manager")

    /** Nobody is watching it: a run of this origin that a restart interrupted goes on by itself, and one
      * that fails or pauses leaves a backlog item (a manager files its own). */
    def unattended: Boolean = this != Person

  object Origin:
    def parse(s: String): Option[Origin] = values.find(_.wire == s.trim.toLowerCase)

  /** The pipelines the engine can run. Each one is Scala code (steps, gates, validation) plus one
    * prompt per LLM step. */
  enum Pipeline(val wire: String, val version: Int, val targetKind: TargetKind, val roles: List[Role],
      val describe: String):
    case EnrichSource extends Pipeline("enrich-source", 2, TargetKind.Source, List(Role.Extractor),
      "read one source and propose its summary, classification, period, findings (each with a verbatim quote) and notes; code verifies and patches")
    case ReviewArticle extends Pipeline("review-article", 5, TargetKind.Article,
      List(Role.Reviewer, Role.ColdReader, Role.Skeptic, Role.Fixer),
      "the adversarial review panel on one article (rules reviewer, cold reader, skeptic in parallel), merged by code into the article's review ledger, the skeptic ruling on each open pair of its critique checklist; with fix, a fixer revises it, each reviewer verifies the findings it claims fixed and the edited passages, until no major finding remains — then a full panel confirms")
    case PlanGoal extends Pipeline("plan-goal", 1, TargetKind.Goal, List(Role.Planner),
      "propose sub-goals, search queries and candidate works (DOIs checked upstream by code) for one goal; nothing is created until a person approves the plan")
    case CompileArticle extends Pipeline("compile-article", 2, TargetKind.Article, List(Role.Extractor, Role.Writer),
      "compile a new article, or a section to append to an existing one, from the sources a filter returns: verified claim table, evidence model, draft, code self-check, then a person approves before anything is written, and a review-article run with fix follows")
    case CurrencyCheck extends Pipeline("currency-check", 1, TargetKind.Goal, Nil,
      "assess and followups over a goal's sources (no model): retractions applied as assess does, disagreement edges to sources in the corpus proposed for approval, citing papers outside it queued for acquisition")
    case AcquisitionSweep extends Pipeline("acquisition-sweep", 1, TargetKind.Goal, List(Role.Planner),
      "discover new works for a goal's queries, a model ranks them against the goal, and the relevant ones become acquisition items — never one already in the corpus or the queue; a watcher that opted in ingests the legal open-access ones (capped per day, legal open access only)")
    case InferFilter extends Pipeline("infer-filter", 1, TargetKind.Article, List(Role.Planner),
      "propose the coverage filter (query, topics, tags) of an article that records none; code runs it against the corpus and the article's own citations, and a person approves it (agents.approvals.article-filter) before it is recorded")
    case HintEffect extends Pipeline("hint-effect", 1, TargetKind.Manager, List(Role.Planner),
      "read one hint a person gave a process manager and propose the structured action it asks for (hold, prioritise or skip items, or a settings change); code checks the action names real items and settings, and it is applied only when a person approves it")
    case ProposeArticles extends Pipeline("propose-articles", 1, TargetKind.Goal, List(Role.Planner),
      "propose the articles a research area should have, each with a slug, title and filter; code checks every slug is free, every filter returns enough sources and none overlaps an article of the area by half, and a person ALWAYS approves the plan before the seeding manager compiles them")

  object Pipeline:
    def parse(s: String): Option[Pipeline] = values.find(_.wire == s.trim.toLowerCase)

  /** What a pipeline works on: a source (by citekey), an article (by slug), a goal (by slug) or a process
    * manager (by id). */
  enum TargetKind(val wire: String):
    case Source  extends TargetKind("source")
    case Article extends TargetKind("article")
    case Goal    extends TargetKind("goal")
    case Manager extends TargetKind("manager")

  object TargetKind:
    def parse(s: String): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)

  // ── wire DTOs ─────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/probe` — one LLM call with one read-only tool (`get_source`) and a typed answer:
    * the connectivity and tool-calling check for a configured model. */
  final case class ProbeRequest(citekey: String, model: Option[String] = None) derives KindlingsCodecAsObject
  object ProbeRequest:
    given Schema[ProbeRequest] = KindlingsSchema.derived[ProbeRequest].schema

  /** The typed answer the probe asks for. */
  final case class ProbeAnswer(citekey: String, title: String, year: Option[Int]) derives KindlingsCodecAsObject:
    /** What the answer leaves empty that the probe asks for: an empty citekey or title, and no year
      * for a record that has one (`recordYear`; a source with none is rightly answered with null). A
      * strict-schema provider sends `""` and `null` where it has nothing, and they must not pass. */
    def problems(recordYear: Option[Int]): List[String] = List(
      Option.when(Option(citekey).forall(_.trim.isEmpty))("the citekey is empty — give the source's citekey"),
      Option.when(Option(title).forall(_.trim.isEmpty))("the title is empty — copy the source's exact title from get_source"),
      Option.when(year.isEmpty && recordYear.isDefined)("the year is missing — give the source's publication year from get_source")
    ).flatten
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
      millis: Long,
      /** Why the (last) answer was not accepted: a field the probe asks for left empty, or no typed answer (prose, a schema mismatch). */
      problems: List[String] = Nil,
      attempts: Int = 1
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
      /** review-article: at most this many review passes — a full panel, then a verification pass after each fix (default `agents.review.max-rounds`). */
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
      goal: Option[String] = None,
      /** Do every read and every LLM step, write nothing to the corpus: each write a step would make
        * is recorded on the step instead (#992). */
      dryRun: Option[Boolean] = None,
      /** One role's model for this run only, over its chain and over `model` (#992). */
      modelFor: List[ModelOverride] = Nil
  ) derives KindlingsCodecAsObject
  object RunRequest:
    given Schema[RunRequest] = KindlingsSchema.derived[RunRequest].schema

  /** `--model-for <role>=<provider:model>`: the model (or `a > b` chain) one role of a run uses, over
    * the role's chain and over the run's `model`. The role is checked by the server. */
  final case class ModelOverride(role: String, model: String) derives KindlingsCodecAsObject:
    def text: String = s"$role=$model"
  object ModelOverride:
    given Schema[ModelOverride] = KindlingsSchema.derived[ModelOverride].schema

    /** `role=provider:model`; only the first `=` separates (a model id never holds one, a role never). */
    def parse(s: String): Either[String, ModelOverride] =
      s.indexOf('=') match
        case -1 => Left(s"'${s.trim}' is not <role>=<provider:model>")
        case i =>
          val (r, m) = (s.substring(0, i).trim.toLowerCase(java.util.Locale.ROOT), s.substring(i + 1).trim)
          if r.isEmpty then Left(s"'${s.trim}' names no role")
          else if m.isEmpty then Left(s"'${s.trim}' names no model")
          else Right(ModelOverride(r, m))

  /** A run's pipeline options, stored with the run (`jobs.params`) so a resumed run sees the same
    * ones. Every field is optional: a run started before an option existed decodes as its default. */
  final case class RunParams(fix: Option[Boolean] = None, rounds: Option[Int] = None,
      title: Option[String] = None, kind: Option[String] = None, query: Option[String] = None,
      topics: Option[List[String]] = None, tags: Option[List[String]] = None,
      modelFor: Option[List[ModelOverride]] = None,
      /** The person's notes (manager hints) snapshotted when a manager dispatched the run: appended to
        * the user message of the steps that act on them, the same on a resume. */
      notes: Option[List[String]] = None,
      /** compile-article: chain a review-article run after writing (None = `agents.compile.review`). */
      chainReview: Option[Boolean] = None,
      /** propose-articles: a proposed article's filter returns at least this many sources (None =
        * `agents.seed.min-sources`), and at most `maxArticles` are proposed (None = `agents.seed.max-articles`). */
      minSources: Option[Int] = None,
      maxArticles: Option[Int] = None,
      /** acquisition-sweep from a watcher that opted in: ingest at most this many of the legal
        * open-access works it picks (the legal chain only); the rest go to the acquisition list. Fixed
        * at dispatch (what is left of the watcher's daily cap), so a resumed run keeps it. */
      autoIngest: Option[Int] = None,
      /** hint-effect: the hint (`manager_hints.id`) whose action the run proposes. */
      hint: Option[Long] = None) derives KindlingsCodecAsObject:
    def fixes: Boolean = fix.contains(true)
    def noteList: List[String] = notes.getOrElse(Nil).map(_.trim).filter(_.nonEmpty)
    def overrides: List[ModelOverride] = modelFor.getOrElse(Nil)
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
      origin: Option[String] = None,
      /** A run paused because every model of a role is below its allowance resumes by itself then. */
      resumeAt: Option[String] = None,
      /** Writes nothing to the corpus: what it would write is recorded on its steps (#992). */
      dryRun: Boolean = false,
      /** The process manager and its work item that dispatched the run (origin `manager`). */
      managerId: Option[String] = None,
      managerItem: Option[Long] = None,
      /** The last sign of life the stall watchdog recorded (step boundary or CLI output). */
      heartbeatAt: Option[String] = None
  ) derives KindlingsCodecAsObject:
    def isWaiting: Boolean = status == RunState.WaitingApproval.wire
    /** The model ref (or chain) `role` uses in this run: its `--model-for`, else `--model`, else None
      * (the role's own chain). */
    def modelFor(role: Role): Option[String] =
      params.overrides.find(o => Role.parse(o.role).contains(role)).map(_.model).orElse(model)
  object RunInfo:
    given Schema[RunInfo] = KindlingsSchema.derived[RunInfo].schema

  /** A write a step of a DRY run skipped: what it is, and its payload (JSON, e.g. the whole patch). */
  final case class WouldWrite(what: String, payload: String) derives KindlingsCodecAsObject
  object WouldWrite:
    given Schema[WouldWrite] = KindlingsSchema.derived[WouldWrite].schema

  /** One checkpointed step. `output` is the step's recorded result (JSON), truncated for display — for
    * a rejected LLM answer, the answer that was rejected. `model` is the configured ref, `actualModel`
    * what the provider reported serving (`claude-sonnet-4-5`; several joined by ` + `, the busiest
    * first). `wouldWrite`: in a dry run, the writes the step skipped. `firstCallTokens`: the input of
    * the attempt's FIRST model call (input + cache tokens) — the fixed part every turn re-sends plus
    * the step's prompt; None when the provider cannot tell or the attempt predates it. `turns`: the
    * model turns it took. */
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
      finishedAt: String = "",
      actualModel: Option[String] = None,
      wouldWrite: List[WouldWrite] = Nil,
      firstCallTokens: Option[Long] = None,
      turns: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    /** `claude-code:haiku → claude-haiku-5-5`; the configured ref alone when the provider said the same
      * (or nothing). */
    def modelText: String = (model, actualModel) match
      case (Some(m), Some(a)) if m.drop(m.indexOf(':') + 1) != a => s"$m → $a"
      case (Some(m), _)                                          => m
      case (None, Some(a))                                       => a
      case (None, None)                                          => ""
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

  /** `roles` is each role's model NOW (the chain's first usable one, else its first); `chains` and
    * `allowances` are the whole picture: every chained model, and each allowance's use per window. */
  final case class ModelsInfo(enabled: Boolean, roles: List[RoleModel], pipelines: List[String],
      maxConcurrentRuns: Int, problems: List[String], chains: List[ModelChains.RoleChain] = Nil,
      allowances: List[ModelChains.AllowanceStatus] = Nil, now: String = "") derives KindlingsCodecAsObject
  object ModelsInfo:
    given Schema[ModelsInfo] = KindlingsSchema.derived[ModelsInfo].schema

  // ── agent doctor ───────────────────────────────────────────────────────────────────────────────

  /** How one check came out: `Warn` is a configuration that works but deserves a look (a provider
    * no role uses, a plan with no allowance), `Fail` one that cannot work as configured. */
  enum CheckLevel(val wire: String):
    case Ok   extends CheckLevel("ok")
    case Warn extends CheckLevel("warn")
    case Fail extends CheckLevel("fail")

  object CheckLevel:
    def parse(s: String): Option[CheckLevel] = values.find(_.wire == s)
    given WireEnumValues[CheckLevel]:
      def all: Array[CheckLevel] = CheckLevel.values
      def wire(e: CheckLevel): String = e.wire
      def fromWire(s: String): Option[CheckLevel] = parse(s)
    given io.circe.Encoder[CheckLevel] = WireEnum.encoder
    given io.circe.Decoder[CheckLevel] = WireEnum.decoder
    given Schema[CheckLevel]           = WireEnum.schema

  final case class DoctorCheck(name: String, level: CheckLevel, detail: String) derives KindlingsCodecAsObject
  object DoctorCheck:
    given Schema[DoctorCheck] = KindlingsSchema.derived[DoctorCheck].schema

  /** One provider: whether any role's chain uses it, the models those chains name, its checks, and the
    * models it lists upstream (when it was asked: online, and a listing endpoint answered). */
  final case class ProviderDoctor(provider: String, used: Boolean, models: List[String], checks: List[DoctorCheck],
      upstreamModels: List[String] = Nil) derives KindlingsCodecAsObject:
    def level: CheckLevel =
      if checks.exists(_.level == CheckLevel.Fail) then CheckLevel.Fail
      else if checks.exists(_.level == CheckLevel.Warn) then CheckLevel.Warn else CheckLevel.Ok
  object ProviderDoctor:
    given Schema[ProviderDoctor] = KindlingsSchema.derived[ProviderDoctor].schema

  /** `GET /api/agents/doctor[?offline=true]` — every provider, checked without spending anything:
    * keys present, the provider's free model listing (online), every chained model priced, the
    * `claude` CLI installed and logged in. `checks` holds what is not about one provider. */
  final case class DoctorReport(enabled: Boolean, online: Boolean, providers: List[ProviderDoctor],
      checks: List[DoctorCheck]) derives KindlingsCodecAsObject
  object DoctorReport:
    given Schema[DoctorReport] = KindlingsSchema.derived[DoctorReport].schema

  // ── generated runtime trees ────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/generate` — write the per-runtime trees (`claude-code`, `codex`, `opencode`;
    * none = all three) under the server's runtimes directory, or under `out` (a SERVER-side path). */
  final case class GenerateRequest(runtimes: List[String] = Nil, out: Option[String] = None) derives KindlingsCodecAsObject
  object GenerateRequest:
    given Schema[GenerateRequest] = KindlingsSchema.derived[GenerateRequest].schema

  /** Where the trees went, how many files they hold, how many changed and how many stale ones went;
    * `definitions` = the fingerprint of what they were generated from. */
  final case class GenerateResult(dir: String, runtimes: List[String], files: Int, written: Int, removed: Int,
      definitions: String) derives KindlingsCodecAsObject
  object GenerateResult:
    given Schema[GenerateResult] = KindlingsSchema.derived[GenerateResult].schema

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
    /** An article's inferred coverage filter (infer-filter): approving records it on the article. */
    case ArticleFilter  extends ApprovalKind("article-filter", false)
    /** The articles planned for a research area (propose-articles): approving lets its seeding manager
      * compile them. */
    case ArticlePlan    extends ApprovalKind("article-plan", false)
    /** A structured action for a process manager (hold, prioritise, skip, a settings change) that a MODEL
      * proposed from a hint (phase 3): applied only when a person approves it. `subject` = the manager. */
    case HintEffect     extends ApprovalKind("hint-effect", false)

    /** About a goal or an article (`subject`), not a source. */
    def aboutSubject: Boolean = this != StatusChange && this != Edge

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
      article: Option[GoalPlans.ArticleDraft] = None,
      /** article-filter: the filter proposed, with what code measured. */
      filter: Option[ArticlePlans.FilterProposal] = None,
      /** article-plan: the articles proposed for the goal. */
      articles: Option[ArticlePlans.ArticlePlan] = None,
      /** hint-effect: the action a model proposed for a manager, from a hint. */
      hintEffect: Option[Managers.HintProposal] = None
  ) derives KindlingsCodecAsObject:
    def approvalKind: Option[ApprovalKind] = ApprovalKind.parse(kind)
  object ApprovalInfo:
    given Schema[ApprovalInfo] = KindlingsSchema.derived[ApprovalInfo].schema

  /** `POST /api/agents/approvals/{id}/{approve|reject}` — an optional note kept with the decision. */
  final case class DecisionRequest(note: Option[String] = None) derives KindlingsCodecAsObject
  object DecisionRequest:
    given Schema[DecisionRequest] = KindlingsSchema.derived[DecisionRequest].schema
