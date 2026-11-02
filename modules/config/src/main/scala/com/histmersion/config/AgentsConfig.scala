package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

given ConfigReader[ScheduledRefresh] = summon[ConfigReader[Int]].emap(ScheduledRefresh.fromMinutes)

final case class AgentsConfig(
    enabled: Boolean,
    /** Every source an ingest job brings in (DOI, upload, archive, Calibre) is enriched, when agents are enabled: an
      * item of the enrich manager (phase 2 of docs/research-process-managers.md; with `managers.enabled = false`, an
      * `enrich-source` run queued at once, as before).
      */
    autoEnrich: Boolean,
    /** A directory that overrides bundled prompts file by file, mirroring the bundled `agents/` tree
      * (`prompts/enrich-source/classify.md`, `article-skeptic.md`, …); "" = the bundled ones only.
      */
    promptsDir: String,
    providers: AgentProvidersConfig,
    roles: AgentRolesConfig,
    prices: String,
    /** Per model or provider: tokens/USD per rolling window and a reserve ([[ModelChains.parseAllowances]]); an
      * allowance saved in the database for the same target wins.
      */
    allowances: String,
    /** How often paused runs waiting for an allowance window are looked at (and resumed when it freed). */
    allowanceCheckSeconds: Int,
    /** `agent doctor`'s per-request timeout (a model listing, `claude --version`). */
    doctorTimeoutSeconds: Int,
    budgets: AgentBudgetsConfig,
    maxConcurrentRuns: Int,
    maxLlmCallsInFlight: Int,
    step: AgentStepConfig,
    tools: AgentToolsConfig,
    trace: AgentTraceConfig,
    review: AgentReviewConfig,
    approvals: AgentApprovalsConfig,
    plan: AgentPlanConfig,
    compile: AgentCompileConfig,
    inferFilter: AgentInferFilterConfig,
    scheduler: AgentSchedulerConfig,
    watchdog: AgentWatchdogConfig,
    managers: AgentManagersConfig,
    runtimes: AgentRuntimesConfig,
    quota: QuotaConfig,
    autopilot: AgentAutopilotConfig = AgentAutopilotConfig()
) derives ConfigReader
