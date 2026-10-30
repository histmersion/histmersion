package com.histmersion.values

/** Who decided an approval. `Autopilot` = the checked auto approvals (their note starts [[AutopilotNote]]); `Auto` =
  * applied the moment it was proposed, by an `agents.approvals.*` mode of `auto` (its result starts [[AutoResult]]);
  * `Person` = everything else that is decided.
  */
enum ApprovalDecidedBy(val wire: String, val describe: String):
  case Person extends ApprovalDecidedBy("person", "decided by a person")
  case Autopilot extends ApprovalDecidedBy("autopilot", "decided by the autopilot's checks")
  case Auto extends ApprovalDecidedBy("auto", "applied when proposed (an auto approval mode)")

object ApprovalDecidedBy:
  /** The note every autopilot decision starts with (`AutopilotPolicy.note`). */
  val AutopilotNote = "autopilot:"

  /** The result of a proposal applied at once (`Approvals.propose`, an `auto` mode). */
  val AutoResult = "applied automatically"

  def parse(s: String): Option[ApprovalDecidedBy] = values.find(_.wire == s.trim.toLowerCase)

  /** Who decided an approval in `state` with this `note` and `result`; `None` while it is open. The SQL that filters
    * and counts by it says the same (`AgentRepo`).
    */
  def of(state: String, note: Option[String], result: Option[String]): Option[ApprovalDecidedBy] =
    if state == ApprovalState.Open.wire then None
    else if note.exists(_.startsWith(AutopilotNote)) then Some(Autopilot)
    else if note.isEmpty && result.exists(_.startsWith(AutoResult)) then Some(Auto)
    else Some(Person)

/** Who serves a model. `Local` is any OpenAI-compatible endpoint (llama.cpp, LM Studio, vLLM, Ollama). `ClaudeCode`
  * runs the person's own `claude` CLI headlessly (`claude -p`) on their own Claude login — their plan's usage, never an
  * API key (docs/research-model-providers.md §1).
  */
enum ProviderKind(val wire: String):
  case Anthropic extends ProviderKind("anthropic")
  case OpenAI extends ProviderKind("openai")
  case Local extends ProviderKind("local")
  case ClaudeCode extends ProviderKind("claude-code")

  /** The person's own `codex` CLI (`codex exec`) on its ChatGPT plan login. */
  case Codex extends ProviderKind("codex")

  /** `opencode run`; the model is `<provider>/<model>` as opencode names it (`openai/gpt-…`). */
  case Opencode extends ProviderKind("opencode")

  /** A provider a PLUGIN registered under this name (docs/research-plugins.md §22): metered, served through the host's
    * tool loop. Parsed only while such a provider is registered (`known`).
    */
  case Plugin(name: String) extends ProviderKind(name)

  /** Runs a local command-line agent (one process per step attempt) rather than an HTTP API. */
  def isCli: Boolean = this == ClaudeCode || this == Codex || this == Opencode

object ProviderKind:
  /** The host's own providers, in their order. */
  val values: List[ProviderKind] = List(Anthropic, OpenAI, Local, ClaudeCode, Codex, Opencode)

  /** A provider name: one of the host's, else one of the `known` plugin providers' (lower case). */
  def parse(s: String, known: Set[String] = Set.empty): Option[ProviderKind] =
    val t = s.trim.toLowerCase
    values.find(_.wire == t).orElse(Option.when(known(t))(Plugin(t)))

  /** The names a reference may use, for an error message: the host's, then the plugins'. */
  def names(known: Set[String] = Set.empty): String =
    (values.map(_.wire) ++ known.toList.sorted.filterNot(k => values.exists(_.wire == k))).mkString(", ")

/** What a step asks a model to be. Each role maps to a `provider:model` in config. */
enum AgentRole(val wire: String):
  case Probe extends AgentRole("probe")
  case Extractor extends AgentRole("extractor")
  case Reviewer extends AgentRole("reviewer")
  case ColdReader extends AgentRole("cold-reader")
  case Skeptic extends AgentRole("skeptic")
  case Fixer extends AgentRole("fixer")

  /** plan-goal: sub-goals, queries and candidate works for a goal; acquisition-sweep: ranks them. */
  case Planner extends AgentRole("planner")

  /** compile-article: drafts the article (or the section to append) from the verified claims. */
  case Writer extends AgentRole("writer")

object AgentRole:
  def parse(s: String): Option[AgentRole] = values.find(_.wire == s.trim.toLowerCase)

/** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server restart, a person's
  * pause); `WaitingApproval` holds no worker and goes back to the queue when a person decides the approval it waits on;
  * `Cancelled` and the other terminal states are final.
  */
enum RunState(val wire: String):
  case Queued extends RunState("queued")
  case Running extends RunState("running")
  case Paused extends RunState("paused")
  case WaitingApproval extends RunState("waiting")
  case Done extends RunState("done")
  case Failed extends RunState("failed")
  case Cancelled extends RunState("cancelled")

  def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

object RunState:
  def parse(s: String): Option[RunState] = values.find(_.wire == s.trim.toLowerCase)

  /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
  def next(from: RunState, to: RunState): Option[RunState] = (from, to) match
    case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
    case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
    case (Running, WaitingApproval)                                                     => Some(to)
    case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
    case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
    case _                                                                              => None

/** How one attempt of a run's step ended, stored in `agent_steps.status`. */
enum StepStatus(val wire: String):
  case Done extends StepStatus("done")
  case Failed extends StepStatus("failed")

object StepStatus:
  def parse(s: String): Option[StepStatus] = values.find(_.wire == s)

/** The pipelines the engine can run. Each one is Scala code (steps, gates, validation) plus one prompt per LLM step.
  */
enum Pipeline(
    val wire: String,
    val version: Int,
    val targetKind: TargetKind,
    val roles: List[AgentRole],
    val describe: String
):
  case EnrichSource
      extends Pipeline(
        "enrich-source",
        2,
        TargetKind.Source,
        List(AgentRole.Extractor),
        "read one source and propose its summary, classification, period, findings (each with a verbatim quote) and notes; code verifies and patches"
      )
  case ReviewArticle
      extends Pipeline(
        "review-article",
        6,
        TargetKind.Article,
        List(AgentRole.Reviewer, AgentRole.ColdReader, AgentRole.Skeptic, AgentRole.Fixer),
        "the adversarial review panel on one article (rules reviewer, cold reader, skeptic in parallel), merged by code into the article's review ledger, the skeptic ruling on each open pair of its critique checklist; with fix, a fixer revises it (code adds the citations a paragraph already names and guards closed findings), each reviewer verifies the findings it claims fixed and the edited passages, until no major finding remains — then a full panel confirms, deferring what it finds new to the next run"
      )
  case PlanGoal
      extends Pipeline(
        "plan-goal",
        1,
        TargetKind.Goal,
        List(AgentRole.Planner),
        "propose sub-goals, search queries and candidate works (DOIs checked upstream by code) for one goal; nothing is created until a person approves the plan"
      )
  case CompileArticle
      extends Pipeline(
        "compile-article",
        2,
        TargetKind.Article,
        List(AgentRole.Extractor, AgentRole.Writer),
        "compile a new article, or a section to append to an existing one, from the sources a filter returns: verified claim table, evidence model, draft, code self-check, then a person approves before anything is written, and a review-article run with fix follows"
      )
  case CurrencyCheck
      extends Pipeline(
        "currency-check",
        1,
        TargetKind.Goal,
        Nil,
        "assess and followups over a goal's sources (no model): retractions applied as assess does, disagreement edges to sources in the corpus proposed for approval, citing papers outside it queued for acquisition"
      )
  case AcquisitionSweep
      extends Pipeline(
        "acquisition-sweep",
        1,
        TargetKind.Goal,
        List(AgentRole.Planner),
        "discover new works for a goal's queries, a model ranks them against the goal, and the relevant ones become acquisition items — never one already in the corpus or the queue; a watcher that opted in ingests the legal open-access ones (capped per day, legal open access only)"
      )
  case InferFilter
      extends Pipeline(
        "infer-filter",
        1,
        TargetKind.Article,
        List(AgentRole.Planner),
        "propose the coverage filter (query, topics, tags) of an article that records none; code runs it against the corpus and the article's own citations, and a person approves it (agents.approvals.article-filter) before it is recorded"
      )
  case HintEffect
      extends Pipeline(
        "hint-effect",
        1,
        TargetKind.Manager,
        List(AgentRole.Planner),
        "read one hint a person gave a process manager and propose the structured action it asks for (hold, prioritise or skip items, or a settings change); code checks the action names real items and settings, and it is applied only when a person approves it"
      )
  case ProposeArticles
      extends Pipeline(
        "propose-articles",
        1,
        TargetKind.Goal,
        List(AgentRole.Planner),
        "propose the articles a research area should have, each with a slug, title and filter; code checks every slug is free, every filter returns enough sources and none overlaps an article of the area by half, and a person ALWAYS approves the plan before the seeding manager compiles them"
      )

object Pipeline:
  def parse(s: String): Option[Pipeline] = values.find(_.wire == s.trim.toLowerCase)

/** What a pipeline works on: a source (by citekey), an article (by slug), a goal (by slug) or a process manager (by
  * id).
  */
enum TargetKind(val wire: String):
  case Source extends TargetKind("source")
  case Article extends TargetKind("article")
  case Goal extends TargetKind("goal")
  case Manager extends TargetKind("manager")

object TargetKind:
  def parse(s: String): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)

/** A change an agent run proposed that a person may have to accept before code applies it: a source's status, a typed
  * disagreement edge between two sources, a goal's plan, or an article a run compiled. `blocking` = the run WAITS for
  * the decision (`waiting_approval`) and applies it itself when it resumes; the other kinds are applied by the decision
  * and the run never waits.
  */
enum ApprovalKind(val wire: String, val blocking: Boolean):
  case StatusChange extends ApprovalKind("status-change", false)
  case Edge extends ApprovalKind("edge", false)
  case GoalPlan extends ApprovalKind("goal-plan", true)
  case ArticlePublish extends ApprovalKind("article-publish", true)

  /** An article's inferred coverage filter (infer-filter): approving records it on the article. */
  case ArticleFilter extends ApprovalKind("article-filter", false)

  /** The articles planned for a research area (propose-articles): approving lets its seeding manager compile them.
    */
  case ArticlePlan extends ApprovalKind("article-plan", false)

  /** A structured action for a process manager (hold, prioritise, skip, a settings change) that a MODEL proposed from a
    * hint (phase 3): applied only when a person approves it. `subject` = the manager.
    */
  case HintEffect extends ApprovalKind("hint-effect", false)

  /** About a goal or an article (`subject`), not a source. */
  def aboutSubject: Boolean = this != StatusChange && this != Edge

object ApprovalKind:
  def parse(s: String): Option[ApprovalKind] = values.find(_.wire == s.trim.toLowerCase)

  given WireEnumValues[ApprovalKind]:
    def all: Array[ApprovalKind] = ApprovalKind.values
    def wire(e: ApprovalKind): String = e.wire
    def fromWire(s: String): Option[ApprovalKind] = parse(s)

/** `Failed` = a person approved it but applying it failed (the source changed or went away); the reason is kept in
  * `result`.
  */
enum ApprovalState(val wire: String):
  case Open extends ApprovalState("open")
  case Approved extends ApprovalState("approved")
  case Rejected extends ApprovalState("rejected")
  case Failed extends ApprovalState("failed")

object ApprovalState:
  def parse(s: String): Option[ApprovalState] = values.find(_.wire == s.trim.toLowerCase)

/** How a kind of proposal is handled: `Manual` waits for a person, `Auto` is applied at once (and still recorded,
  * already approved).
  */
enum ApprovalMode(val wire: String):
  case Manual extends ApprovalMode("manual")
  case Auto extends ApprovalMode("auto")

object ApprovalMode:
  def parse(s: String): Option[ApprovalMode] = values.find(_.wire == s.trim.toLowerCase)
