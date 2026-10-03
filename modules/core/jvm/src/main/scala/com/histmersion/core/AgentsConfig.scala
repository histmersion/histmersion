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

/** One entry per [[Agents.ProviderKind]] — the provider half of a `provider:model` reference. */
final case class AgentProvidersConfig(anthropic: AgentProviderConfig, openai: AgentProviderConfig,
    local: AgentProviderConfig) derives ConfigReader

/** Step role → `provider:model`. Empty = no model configured for that role; a step that needs it is
  * refused with a message naming the key. */
final case class AgentRolesConfig(probe: String, extractor: String, reviewer: String, coldReader: String,
    skeptic: String, fixer: String) derives ConfigReader

/** Per run defaults (overridable per run) and the global caps checked before every LLM call. */
final case class AgentBudgetsConfig(runUsd: Double, runTokens: Long, dailyUsd: Double, monthlyUsd: Double)
    derives ConfigReader

/** One LLM step's loop: iterations of the tool loop, output tokens per call, and how often a typed
  * answer that fails code validation is retried with the validator's errors appended. */
final case class AgentStepConfig(maxIterations: Int, maxTokens: Int, maxRetries: Int) derives ConfigReader

/** The deterministic verifiers (§4.4). `quoteMinRatio` is the OCR-tolerant quote match threshold
  * (1.0 = verbatim after normalisation); `maxAbsYear` bounds a signed year a model may propose. */
final case class AgentVerifyConfig(quoteMinRatio: Double, maxAbsYear: Long) derives ConfigReader

/** What a run's trace keeps: a tool result is truncated to `maxToolResultChars` before it is stored,
  * and traced messages older than `keepMessagesDays` are deleted (0 = keep them all) — checked when
  * the run pool starts and then at most every `pruneEveryMinutes`, after a run settles. Steps (the
  * checkpoints, tokens and cost) are never pruned. */
final case class AgentTraceConfig(maxToolResultChars: Int, keepMessagesDays: Int, pruneEveryMinutes: Int)
    derives ConfigReader

/** `enrich-source`: the body is sent as at most `maxWindows` windows of `windowChars` characters (by
  * heading where the source has a table of contents); the classify step sees the first `headChars`.
  * `upstream` = check the record against OpenAlex/Crossref and run `refs`/`assess` (network calls;
  * off, those steps record that they were skipped). */
final case class AgentEnrichConfig(windowChars: Int, maxWindows: Int, headChars: Int, upstream: Boolean)
    derives ConfigReader

/** `review-article`: at most `maxRounds` panel rounds per run (a run may ask for fewer, never more
  * than `roundsCap`), the article body is sent up to `articleChars` characters (a longer one is
  * reviewed cut, and never revised), and the fixer — whose answer is the whole revised body — may
  * write up to `fixerMaxTokens` output tokens per call. */
final case class AgentReviewConfig(maxRounds: Int, roundsCap: Int, articleChars: Int, fixerMaxTokens: Int)
    derives ConfigReader

/** What a run may change without a person: `manual` (the default) records an approval and waits,
  * `auto` applies at once. `statusChange` covers every status an agent proposes beyond filling
  * `unassessed` with `current`/`foundational`; `edges` covers the disagreement edges (challenges,
  * refutes, updates, superseded_by). Anything but `auto` is manual. */
final case class AgentApprovalsConfig(statusChange: String, edges: String) derives ConfigReader:
  def statusMode: Agents.ApprovalMode = AgentApprovalsConfig.mode(statusChange)
  def edgeMode: Agents.ApprovalMode   = AgentApprovalsConfig.mode(edges)

object AgentApprovalsConfig:
  /** A value that is not `auto` — a typo included — is the safe one. */
  def mode(raw: String): Agents.ApprovalMode =
    Agents.ApprovalMode.parse(raw).getOrElse(Agents.ApprovalMode.Manual)

final case class AgentsConfig(
    enabled: Boolean,
    /** A directory that overrides bundled prompts file by file, mirroring the bundled `agents/` tree
      * (`prompts/enrich-source/classify.md`, `article-skeptic.md`, …); "" = the bundled ones only. */
    promptsDir: String,
    providers: AgentProvidersConfig,
    roles: AgentRolesConfig,
    prices: String,
    budgets: AgentBudgetsConfig,
    maxConcurrentRuns: Int,
    maxLlmCallsInFlight: Int,
    step: AgentStepConfig,
    verify: AgentVerifyConfig,
    trace: AgentTraceConfig,
    enrich: AgentEnrichConfig,
    review: AgentReviewConfig,
    approvals: AgentApprovalsConfig
) derives ConfigReader:

  /** The provider block for a kind. */
  def provider(k: Agents.ProviderKind): AgentProviderConfig = k match
    case Agents.ProviderKind.Anthropic => providers.anthropic
    case Agents.ProviderKind.OpenAI    => providers.openai
    case Agents.ProviderKind.Local     => providers.local

  /** The configured `provider:model` of a role, as written (empty = unset). */
  def role(r: Agents.Role): String = r match
    case Agents.Role.Probe      => roles.probe
    case Agents.Role.Extractor  => roles.extractor
    case Agents.Role.Reviewer   => roles.reviewer
    case Agents.Role.ColdReader => roles.coldReader
    case Agents.Role.Skeptic    => roles.skeptic
    case Agents.Role.Fixer      => roles.fixer
