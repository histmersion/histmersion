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

/** What a run's trace keeps: a tool result is truncated to `maxToolResultChars` before it is stored. */
final case class AgentTraceConfig(maxToolResultChars: Int, keepMessagesDays: Int) derives ConfigReader

/** `enrich-source`: the body is sent as at most `maxWindows` windows of `windowChars` characters (by
  * heading where the source has a table of contents); the classify step sees the first `headChars`. */
final case class AgentEnrichConfig(windowChars: Int, maxWindows: Int, headChars: Int) derives ConfigReader

final case class AgentsConfig(
    enabled: Boolean,
    providers: AgentProvidersConfig,
    roles: AgentRolesConfig,
    prices: String,
    budgets: AgentBudgetsConfig,
    maxConcurrentRuns: Int,
    maxLlmCallsInFlight: Int,
    step: AgentStepConfig,
    verify: AgentVerifyConfig,
    trace: AgentTraceConfig,
    enrich: AgentEnrichConfig
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
