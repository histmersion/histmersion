package com.histmersion.core

import hearth.kindlings.sconfigderivation.ConfigReader

/** Embedded research agents (docs/research-embedded-agents.md §5.3). OFF by default: with `enabled =
  * false` the server refuses every agent run and the external Claude Code workflow is untouched.
  *
  * API keys come from the environment only (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`); they never reach
  * the database, `/api/about`, a log line or a trace — [[AgentProviderConfig.toString]] redacts them.
  *
  * Several values are strings that a pure parser in [[Agents]] turns into types, because an env
  * override can only ever be a string: `roles.*` are `provider:model` references ([[Agents.ModelRef]])
  * and `prices` is a price table ([[Agents.Prices]]). */
final case class AgentProviderConfig(baseUrl: String, apiKey: String, strictTools: Boolean, timeoutSeconds: Int)
    derives ConfigReader:
  override def toString: String =
    s"AgentProviderConfig($baseUrl, apiKey=${if apiKey.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"

/** The `claude-code` provider: the person's own `claude` CLI, run headlessly (`claude -p`) on their own
  * Claude login (docs/research-model-providers.md §1 — their own machine, their own use).
  *
  * - `command`: the executable (an absolute path when the server's PATH lacks it — a LaunchAgent's
  *   usually does).
  * - `configDir`: a `CLAUDE_CONFIG_DIR` of its own ("" = the user's `~/.claude`); a separate one keeps
  *   the user's hooks, memory and MCP servers out of the runs, but needs its own `/login` once.
  * - `extraArgs`: appended to every call, whitespace-separated (`--restricted` by default: the user's
  *   settings files and code-running tools stay out).
  * - `useApiKey`: false (the default) strips `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN` from the CLI's
  *   environment — `claude -p` always prefers a key that is present, which would silently bill the API.
  * - `allowNonLoopback`: the provider is refused while the server listens beyond loopback, so nobody
  *   else's request can spend the owner's plan; true = every client of this server is its owner.
  * - `mcpUrl`: the server's read-only agent MCP endpoint as the CLI reaches it ("" = `public-url` +
  *   `/mcp/agent`).
  * - `limitCooldownMinutes`: after the CLI reports the plan's usage limit without a reset time, the
  *   model is not used for this long.
  * - `disableSlashCommands`: `--disable-slash-commands` on every run — no skill or slash command is
  *   loaded (a role's skills are inlined into its generated prompt; a main-thread `--agent` gets no
  *   `skills:` preload either way).
  * - `env`: variables set in the CLI's environment on every call; a variable whose value is empty
  *   (`""` or `null`) is removed, also one the server inherited ([[ClaudeCodeConfig.envChanges]]). */
final case class ClaudeCodeConfig(command: String, configDir: String, extraArgs: String, useApiKey: Boolean,
    allowNonLoopback: Boolean, mcpUrl: String, timeoutSeconds: Int, limitCooldownMinutes: Int,
    disableSlashCommands: Boolean, env: Map[String, Option[String]]) derives ConfigReader:
  /** [[env]] as changes to an environment, sorted by name: `Some(value)` sets, `None` removes. */
  def envChanges: List[(String, Option[String])] =
    env.toList.map((k, v) => k.trim -> v.filter(_.trim.nonEmpty)).filter(_._1.nonEmpty).sortBy(_._1)

/** The `codex` provider: the person's own `codex` CLI (`codex exec --json`), on the ChatGPT plan login
  * of a `CODEX_HOME` of its own (the generated `<runtimes>/codex/home`, or `home`), never a copied
  * `auth.json` (docs/research-agent-runtimes.md §1.2).
  *
  * - `useApiKey`: false strips `OPENAI_API_KEY`/`CODEX_API_KEY` from its environment (a key bills the API).
  * - `appServer`: poll the plan's windows with `codex app-server` → `account/rateLimits/read` (it
  *   generates nothing; reset credits are never touched).
  * - `toolOutputTokens`: the generated home's `tool_output_token_limit` — the most one tool output (a
  *   code-mode `exec` batch of calls) puts in the context; 0 leaves the model's own (10,000 tokens).
  * - `baseInstructions`: `lean` writes Histmersion's own base prompt into the home
  *   (`model_instructions_file`, from `agents/_shared/codex-base-instructions.md`) in place of Codex's
  *   built-in one (~21k characters of collaboration, PR, skills and plugin guidance, re-sent on every
  *   request); `builtin` keeps Codex's. */
final case class CodexConfig(command: String, home: String, useApiKey: Boolean, timeoutSeconds: Int,
    appServer: Boolean, toolOutputTokens: Int = 0, baseInstructions: String = "lean") derives ConfigReader:
  /** `builtin` (any case) keeps Codex's own base prompt; anything else is `lean`. */
  def leanBase: Boolean = !baseInstructions.trim.equalsIgnoreCase("builtin")

/** The `opencode` provider: `opencode run --agent <role> --format json` on the generated config.
  * `chatgptLogin`: its `openai/…` models are signed in with a ChatGPT plan, so they draw on the same
  * plan pool as Codex (otherwise they are metered). */
final case class OpencodeConfig(command: String, timeoutSeconds: Int, chatgptLogin: Boolean) derives ConfigReader

/** One entry per [[Agents.ProviderKind]] — the provider half of a `provider:model` reference. */
final case class AgentProvidersConfig(anthropic: AgentProviderConfig, openai: AgentProviderConfig,
    local: AgentProviderConfig, claudeCode: ClaudeCodeConfig, codex: CodexConfig, opencode: OpencodeConfig)
    derives ConfigReader

/** The generated per-runtime trees (docs/research-agent-runtimes.md §2.2): written under `dir` ("" =
  * `<data dir>/agents`) when the server starts if `generate`; `claudeMod` puts the quota-guard mod in
  * the headless Claude Code plugin, `interactiveMod` the reporting mod in the interactive one. */
final case class AgentRuntimesConfig(dir: String, generate: Boolean, claudeMod: Boolean, interactiveMod: Boolean)
    derives ConfigReader

/** One plan pool: a floor (% that must stay LEFT) per window kind (`five_hour=25, seven_day=25`;
  * Codex windows by length, `300m`, `10080m`), and how many LLM calls may draw on it at once. */
final case class PlanPoolConfig(floors: String, maxCallsInFlight: Int) derives ConfigReader

/** The Claude plan pool: [[PlanPoolConfig]]'s two, and how it takes a fresh reading when a run wants
  * the pool and its last one is too old to decide on (docs/research-plan-quotas.md §8). `refresh` is
  * `auto` (a zero-cost read if the CLI has one — 2.1.289 has none — else the probe), `probe`, or `off`
  * (wait for a run's own reading, as before); the probe is one `claude -p` turn on `probeModel` with
  * at most `probeMaxTokens` output tokens, given up after `probeTimeoutSeconds`. */
final case class ClaudePlanConfig(floors: String, maxCallsInFlight: Int, refresh: String, probeModel: String,
    probeMaxTokens: Int, probeTimeoutSeconds: Int) derives ConfigReader

/** Subscription pools (docs/research-plan-quotas.md §5): a snapshot older than `staleMinutes` is
  * unknown; a window with no floor of its own has `defaultFloor`; a stale reading lets a run go on only
  * with `margin` points to spare; `fallthrough` names the roles whose chain may fall from a plan below
  * its floor to a paid key (the others pause). */
final case class QuotaConfig(staleMinutes: Int, defaultFloor: Int, margin: Int, pollSeconds: Int,
    claudePlan: ClaudePlanConfig, chatgptPlan: PlanPoolConfig, fallthrough: String) derives ConfigReader

/** Step role → its model CHAIN, `provider:model > provider:model > …` (one model is a chain of one;
  * [[ModelChains.parseChain]]). Empty = no model configured for that role; a step that needs it is
  * refused with a message naming the key. A chain saved in the database (`agent models set`) wins. */
final case class AgentRolesConfig(probe: String, extractor: String, reviewer: String, coldReader: String,
    skeptic: String, fixer: String, planner: String, writer: String) derives ConfigReader

/** Per run defaults (overridable per run) and the global caps checked before every LLM call. */
final case class AgentBudgetsConfig(runUsd: Double, runTokens: Long, dailyUsd: Double, monthlyUsd: Double,
    pipelineRunTokens: String = "", cacheReadWeight: Double = 1.0) derives ConfigReader:
  /** `run-tokens` for a run of `pipeline` that asks for none: its `pipeline-run-tokens` entry, else
    * `run-tokens`. An entry that does not parse (or is not positive) is ignored. */
  def runTokensFor(pipeline: String): Long =
    AgentBudgetsConfig.parsePipelineTokens(pipelineRunTokens).getOrElse(pipeline, runTokens)

object AgentBudgetsConfig:
  /** `"review-article=1200000, compile-article=800000"` → the map; malformed entries dropped. */
  def parsePipelineTokens(s: String): Map[String, Long] =
    s.split(",").toList.map(_.trim).filter(_.nonEmpty).flatMap(e => e.split("=", 2) match
      case Array(k, v) => v.trim.replace("_", "").toLongOption.filter(_ > 0).map(k.trim -> _)
      case _           => None).toMap

/** One LLM step's loop: iterations of the tool loop, output tokens per call, and how often a typed
  * answer that fails code validation is retried with the validator's errors appended. */
final case class AgentStepConfig(maxIterations: Int, maxTokens: Int, maxRetries: Int) derives ConfigReader

/** The deterministic verifiers (§4.4). `quoteMinRatio` is the OCR-tolerant quote match threshold
  * (1.0 = verbatim after normalisation); `maxAbsYear` bounds a signed year a model may propose. */
final case class AgentVerifyConfig(quoteMinRatio: Double, maxAbsYear: Long) derives ConfigReader

/** What one tool call of an agent step may return (every runtime: the agent MCP endpoint a run token
  * reaches and the API tool loop). Every later turn of the step re-sends the result, so a long one is
  * cut at a line with a trailer saying how to read on: `resultMaxChars` for any result, `bodyChars` the
  * body slice a `get_source section=body` without `chars` reads, `queryMaxRows` the rows of a `query`. */
final case class AgentToolsConfig(resultMaxChars: Int, bodyChars: Int, queryMaxRows: Int) derives ConfigReader
object AgentToolsConfig:
  /** No cap (0 = off): what a tool returns is what the handler returned. */
  val Off: AgentToolsConfig = AgentToolsConfig(0, 0, 0)

/** What a run's trace keeps: a tool result is truncated to `maxToolResultChars` before it is stored,
  * and traced messages older than `keepMessagesDays` are deleted (0 = keep them all) — checked when
  * the run pool starts and then at most every `pruneEveryMinutes`, after a run settles. Steps (the
  * checkpoints, tokens and cost) are never pruned. */
final case class AgentTraceConfig(maxToolResultChars: Int, keepMessagesDays: Int, pruneEveryMinutes: Int)
    derives ConfigReader

/** `enrich-source`: the body is sent as at most `maxWindows` windows of `windowChars` characters (by
  * heading where the source has a table of contents); the classify step sees the first `headChars`.
  * `upstream` = check the record against OpenAlex/Crossref and run `refs`/`assess` (network calls;
  * off, those steps record that they were skipped). `guards` are the findings step's extra checks
  * ([[AgentEnrichGuardsConfig]]). */
final case class AgentEnrichConfig(windowChars: Int, maxWindows: Int, headChars: Int, upstream: Boolean,
    guards: AgentEnrichGuardsConfig) derives ConfigReader

/** The findings step's guards against the failure kinds a smaller extractor showed (#1424): an answer
  * whose places mostly do not resolve in the gazetteer — fewer than `minPlacedShare` (0..1) of the
  * findings that name a place — is asked again; a year below 100 is taken for a century stored as a
  * year when the finding mentions a century, or when the source's own period starts at least
  * `centuryGapYears` later; and a claim containing one of `pointerPhrases` (comma-separated, matched
  * as whole words, ignoring case) points at a table instead of standing alone. Each is sent back while
  * attempts remain and kept with a warning on the last one ([[EnrichSource.FindingGuards]]). */
final case class AgentEnrichGuardsConfig(minPlacedShare: Double, centuryGapYears: Int, pointerPhrases: String)
    derives ConfigReader:
  def phrases: List[String] = PeriodsConfig.split(pointerPhrases).map(_.toLowerCase(java.util.Locale.ROOT)).distinct

/** `review-article`: at most `maxRounds` panel rounds per run (a run may ask for fewer, never more
  * than `roundsCap`), the article body is sent up to `articleChars` characters (a longer one is
  * reviewed cut, and never revised), and the fixer — whose answer is a list of edits — may write up
  * to `fixerMaxTokens` output tokens per call. The fixer's tool-loop turns are its agent's
  * `max-turns` plus `fixerTurnsPerFinding` for each major or factual minor finding, at most
  * `fixerTurnsCap`. A verification pass's re-check of one reviewer gets `recheckTurns` plus one per
  * finding it checks, never more than its agent's `max-turns`; the ledger's closed findings reach a
  * prompt as at most `ledgerClosedLines` lines; `finalPanel` says when a fixing run ends with a full
  * panel (docs/research-review-convergence.md §6.2). A full pass's rules reviewer must report
  * `spotChecks` cited claims, each resting on a get_source/passages call the step made (fewer when the
  * article cites fewer sources; 0 = not enforced, #1840). The skeptic of a full pass is shown at most
  * `critiquePairs` open pairs of the critique checklist in full (the rest by name) and at most
  * `critiqueReasoned` of its reasoned ones (§6.8). */
final case class AgentReviewConfig(maxRounds: Int, roundsCap: Int, articleChars: Int, fixerMaxTokens: Int,
    fixerTurnsPerFinding: Int, fixerTurnsCap: Int, recheckTurns: Int, ledgerClosedLines: Int, finalPanel: String,
    spotChecks: Int, critiquePairs: Int, critiqueReasoned: Int)
    derives ConfigReader:
  def finalPanelMode: FinalPanel = FinalPanel.parse(finalPanel).getOrElse(FinalPanel.Clear)

/** When a fixing `review-article` run ends with a full panel: only when the re-checks left no MAJOR
  * open (`clear`, the default), after every fix (`always`), or never. */
enum FinalPanel(val wire: String):
  case Clear  extends FinalPanel("clear")
  case Always extends FinalPanel("always")
  case Never  extends FinalPanel("never")

object FinalPanel:
  def parse(s: String): Option[FinalPanel] = values.find(_.wire == Option(s).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))

/** What a run may change without a person: `manual` (the default) records an approval and waits,
  * `auto` applies at once. `statusChange` covers every status an agent proposes beyond filling
  * `unassessed` with `current`/`foundational`; `edges` covers the disagreement edges (challenges,
  * refutes, updates, superseded_by). Anything but `auto` is manual. */
final case class AgentApprovalsConfig(statusChange: String, edges: String, articlePublish: String,
    articleFilter: String = "manual", articlePlan: String = "manual", goalPlan: String = "manual",
    auto: AutoApprovalsConfig = AutoApprovalsConfig()) derives ConfigReader:
  def statusMode: Agents.ApprovalMode  = AgentApprovalsConfig.mode(statusChange)
  def edgeMode: Agents.ApprovalMode    = AgentApprovalsConfig.mode(edges)
  /** `compile-article`: `manual` (the default) waits for a person before the article is written. */
  def publishMode: Agents.ApprovalMode = AgentApprovalsConfig.mode(articlePublish)
  /** `infer-filter`: `manual` (the default) records the filter only once a person approves it. */
  def filterMode: Agents.ApprovalMode  = AgentApprovalsConfig.mode(articleFilter)

  /** Whether the managers' supervisor decides an open approval of `kind` by its code checks (the
    * autopilot's auto approvals, docs/research-process-managers.md "As built: the autopilot"):
    * `article-plan` and `goal-plan` = `auto` (or `checked`); `article-publish` = `checked` — its `auto`
    * approves in the run at once, unchecked, as before, and the supervisor only decides what was left
    * open. Any other kind, or any other value, is a person's. */
  def checked(kind: Agents.ApprovalKind): Boolean = kind match
    case Agents.ApprovalKind.ArticlePlan    => AgentApprovalsConfig.isChecked(articlePlan)
    case Agents.ApprovalKind.GoalPlan       => AgentApprovalsConfig.isChecked(goalPlan)
    case Agents.ApprovalKind.ArticlePublish => AgentApprovalsConfig.isChecked(articlePublish)
    case _                                  => false

/** The caps and the thresholds of the checked auto approvals ([[AgentApprovalsConfig.checked]]): at
  * most `articlesPerGoalPerDay` planned articles of one research area, `publishPerDay` article drafts and
  * `goalPlansPerDay` goal plans approved automatically per UTC day (what is over waits for a person, or
  * the next day); a draft is published only when its filter returns at least `publishMinSources`
  * sources; a goal plan may propose sub-goals only for a goal less than `maxGoalDepth` levels below a
  * root goal (0 = a root). */
final case class AutoApprovalsConfig(articlesPerGoalPerDay: Int = 6, publishPerDay: Int = 10, goalPlansPerDay: Int = 5,
    publishMinSources: Int = 3, maxGoalDepth: Int = 2) derives ConfigReader

object AgentApprovalsConfig:
  /** `auto` or `checked`, in any case. */
  def isChecked(raw: String): Boolean =
    val v = Option(raw).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    v == "auto" || v == "checked"

  /** A value that is not `auto` — a typo included — is the safe one. */
  def mode(raw: String): Agents.ApprovalMode =
    Agents.ApprovalMode.parse(raw).getOrElse(Agents.ApprovalMode.Manual)

/** `plan-goal`: at most this many sub-goals, queries and works in one plan (a longer answer is sent
  * back), each query at most `queryChars` long. */
final case class AgentPlanConfig(maxSubGoals: Int, maxQueries: Int, maxWorks: Int, queryChars: Int)
    derives ConfigReader:
  def caps: PlanGoal.Caps = PlanGoal.Caps(maxSubGoals, maxQueries, maxWorks, queryChars)

/** `compile-article`: the filter is read up to `maxSources` sources (one extraction each), each
  * extraction sees `recordChars` of the record (summary, findings, notes, then the body) and keeps at
  * most `maxClaims` claims; the writer may write `draftMaxTokens` output tokens; `review` = chain a
  * review-article run with fix after the article is written. */
final case class AgentCompileConfig(maxSources: Int, recordChars: Int, maxClaims: Int, draftMaxTokens: Int,
    review: Boolean) derives ConfigReader

/** `infer-filter`: a proposed filter must return at least `minHits` and at most `maxHits` sources, and
  * at least `minCitedShare` (0..1) of the sources the article cites; `sample` sources it returns that
  * the article does not cite are shown with the proposal. */
final case class AgentInferFilterConfig(minHits: Int, maxHits: Int, minCitedShare: Double, sample: Int) derives ConfigReader:
  def limits: FilterInference.Limits = FilterInference.Limits(math.max(1, minHits), math.max(minHits, maxHits),
    math.min(1.0, math.max(0.0, minCitedShare)))

/** `propose-articles`: at most `maxArticles` per plan; each one's filter returns at least `minSources`
  * sources and shares less than `maxOverlap` (0..1) of them with any article of the research area. */
final case class AgentSeedConfig(maxArticles: Int, minSources: Int, maxOverlap: Double) derives ConfigReader

/** The crons and the goal sweeps. Since phase 3 a goal's schedules are its watcher (`watch:<goal>`) and
  * every manager's cron fires from the managers' tick (docs/research-process-managers.md, "As built
  * (phase 3)"); the scheduler thread and its `tick-seconds` are gone. `enabled = false` stops every
  * manager's cron at once (they are also off whenever agents are); a cron may not fire more often than
  * every `minIntervalMinutes`; a currency check looks at up to `currencySources` of a goal's sources and
  * `followupsPerSource` citing papers each; an acquisition sweep asks `discoverPerQuery` works per query
  * and offers at most `sweepCandidates` to the ranking model. */
final case class AgentSchedulerConfig(enabled: Boolean, minIntervalMinutes: Int,
    currencySources: Int, followupsPerSource: Int, discoverPerQuery: Int, sweepCandidates: Int) derives ConfigReader

/** The stall watchdog (docs/research-process-managers.md §3.3). `stallMinutes` 0 = the longest
  * provider timeout plus 5 minutes ([[stallMinutesFor]]). */
final case class AgentWatchdogConfig(stallMinutes: Int, resumeMinutes: Int, maxStalls: Int, heartbeatSeconds: Int,
    interruptedResumeMinutes: Int) derives ConfigReader:
  def stallMinutesFor(p: AgentProvidersConfig): Int =
    if stallMinutes > 0 then stallMinutes
    else
      val longest = List(p.anthropic.timeoutSeconds, p.openai.timeoutSeconds, p.local.timeoutSeconds,
        p.claudeCode.timeoutSeconds, p.codex.timeoutSeconds, p.opencode.timeoutSeconds).max
      (longest + 59) / 60 + 5

/** The review manager's defaults (a value saved in the manager's config wins). `staleAfterDays`: a
  * converged article is selected again (lowest priority) after this many days, or when its filter gained
  * sources (0 = never); looked for at most every `staleScanMinutes`. `inferFilter`: an article without a
  * coverage filter gets an infer-filter item before its review. `extendMinNew`: an article whose filter
  * returns at least this many sources it neither cites nor gives a reason for gets an `article-extend`
  * item (0 = never), at most every `extendCooldownDays` per article. */
final case class ReviewManagerConfig(maxRuns: Int, rounds: Int, maxRunsPerArticle: Int, noProgressRuns: Int,
    reopenLimit: Int, cooldownMinutes: Int, lowWater: Int, refillBatch: Int, staleAfterDays: Int,
    inferFilter: Boolean = true, staleScanMinutes: Int = 60, extendMinNew: Int = 0, extendCooldownDays: Int = 14)
    derives ConfigReader

/** A library-wide or per-area manager with nothing but its runs at once to set: the enrich manager
  * (`agents.managers.enrich`) and the seeding managers (`agents.managers.seed`). */
final case class SimpleManagerConfig(maxRuns: Int) derives ConfigReader

/** A research area's watcher (`agents.managers.watch`, docs/research-process-managers.md §4 (c)): its runs
  * at once, the cron a new one gets (UTC), and the default daily cap of its OPT-IN auto-ingest of legal
  * open-access works (a watcher's own `autoIngestPerDay` wins; auto-ingest itself is off unless the watcher
  * turns it on), and how many works of the watchers' ingest backlog one managers' tick ingests at most
  * (`drainPerTick`: each fetches a file, so a tick takes only a few; the daily caps bound the day). */
final case class WatchManagerConfig(maxRuns: Int, cron: String, autoIngestPerDay: Int, drainPerTick: Int = 3)
    derives ConfigReader

/** The process managers' supervisor (docs/research-process-managers.md §3). */
final case class AgentManagersConfig(enabled: Boolean, tickSeconds: Int, keepEventsDays: Int, backoffMinutes: Int,
    maxBackoffMinutes: Int, maxFailures: Int, review: ReviewManagerConfig, enrich: SimpleManagerConfig,
    seed: SimpleManagerConfig, watch: WatchManagerConfig, dailyUsd: Double = 0) derives ConfigReader

/** The autopilot (docs/research-process-managers.md "As built: the autopilot"): ONE switch, kept in the
  * database (`histmersion agent autopilot on|off`), that makes the managers carry every research goal
  * from planned to reviewed. These are its settings while it is on:
  *
  * - `ensureMinutes`: how often the supervisor makes sure every goal has its managers and moves the goal
  *   statuses on from the facts (also at once when it is turned on);
  * - `ingestPerDay`: legal open-access works ALL watchers together may ingest per UTC day (0 = only each
  *   watcher's own cap); `watchIngestPerDay`: a watcher's own cap when the autopilot creates it;
  * - `watchCron`, `seedCron`: the cron a watcher and a seeding manager it creates gets (an `@daily` /
  *   `@weekly` / `@monthly` is spread over the hours and days per goal, so 60 watchers do not fire at
  *   the same minute); `finishedWatchCron`: a reviewed goal's watcher;
  * - `extendMinNew`: what the review manager's `extendMinNew` is set to (when it has none);
  * - `backupHours`, `lintHours`: a backup (pg_dump, the configured pruning) and the safe `lint --fix`
  *   repairs every so many hours (0 = never), never while a backup, a vacuum or a restore runs. */
final case class AgentAutopilotConfig(ensureMinutes: Int = 5, ingestPerDay: Int = 20, watchIngestPerDay: Int = 5,
    watchCron: String = "@weekly", seedCron: String = "@weekly", finishedWatchCron: String = "@monthly",
    extendMinNew: Int = 3, backupHours: Int = 24, lintHours: Int = 24) derives ConfigReader

final case class AgentsConfig(
    enabled: Boolean,
    /** Every source an ingest job brings in (DOI, upload, archive, Calibre) is enriched, when agents are
      * enabled: an item of the enrich manager (phase 2 of docs/research-process-managers.md; with
      * `managers.enabled = false`, an `enrich-source` run queued at once, as before). */
    autoEnrich: Boolean,
    /** A directory that overrides bundled prompts file by file, mirroring the bundled `agents/` tree
      * (`prompts/enrich-source/classify.md`, `article-skeptic.md`, …); "" = the bundled ones only. */
    promptsDir: String,
    providers: AgentProvidersConfig,
    roles: AgentRolesConfig,
    prices: String,
    /** Per model or provider: tokens/USD per rolling window and a reserve ([[ModelChains.parseAllowances]]);
      * an allowance saved in the database for the same target wins. */
    allowances: String,
    /** How often paused runs waiting for an allowance window are looked at (and resumed when it freed). */
    allowanceCheckSeconds: Int,
    /** `agent doctor`'s per-request timeout (a model listing, `claude --version`). */
    doctorTimeoutSeconds: Int,
    budgets: AgentBudgetsConfig,
    maxConcurrentRuns: Int,
    maxLlmCallsInFlight: Int,
    step: AgentStepConfig,
    verify: AgentVerifyConfig,
    tools: AgentToolsConfig,
    trace: AgentTraceConfig,
    enrich: AgentEnrichConfig,
    review: AgentReviewConfig,
    approvals: AgentApprovalsConfig,
    plan: AgentPlanConfig,
    compile: AgentCompileConfig,
    inferFilter: AgentInferFilterConfig,
    seed: AgentSeedConfig,
    scheduler: AgentSchedulerConfig,
    watchdog: AgentWatchdogConfig,
    managers: AgentManagersConfig,
    runtimes: AgentRuntimesConfig,
    quota: QuotaConfig,
    autopilot: AgentAutopilotConfig = AgentAutopilotConfig()
) derives ConfigReader:

  /** The HTTP provider block for a kind; the CLI providers have none ([[providers]]). */
  def provider(k: Agents.ProviderKind): Option[AgentProviderConfig] = k match
    case Agents.ProviderKind.Anthropic  => Some(providers.anthropic)
    case Agents.ProviderKind.OpenAI     => Some(providers.openai)
    case Agents.ProviderKind.Local      => Some(providers.local)
    case Agents.ProviderKind.ClaudeCode | Agents.ProviderKind.Codex | Agents.ProviderKind.Opencode |
         Agents.ProviderKind.Plugin(_) => None

  /** The configured `provider:model` of a role, as written (empty = unset). */
  def role(r: Agents.Role): String = r match
    case Agents.Role.Probe      => roles.probe
    case Agents.Role.Extractor  => roles.extractor
    case Agents.Role.Reviewer   => roles.reviewer
    case Agents.Role.ColdReader => roles.coldReader
    case Agents.Role.Skeptic    => roles.skeptic
    case Agents.Role.Fixer      => roles.fixer
    case Agents.Role.Planner    => roles.planner
    case Agents.Role.Writer     => roles.writer

  /** How the command-line providers are signed in — which models draw on a plan pool. */
  def logins: PlanQuota.Logins =
    PlanQuota.Logins(providers.claudeCode.useApiKey, providers.codex.useApiKey, providers.opencode.chatgptLogin)
