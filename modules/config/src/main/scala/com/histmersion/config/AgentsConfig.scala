package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

given ConfigReader[ScheduledRefresh] = summon[ConfigReader[Minutes]].emap(m => ScheduledRefresh.fromMinutes(m).left.map(_.value))

final case class AgentsConfig(
    enabled: AgentsConfig.Enabled,
    /** Every source an ingest job brings in (DOI, upload, archive, Calibre) is enriched, when agents are enabled: an
      * item of the enrich manager (phase 2 of docs/research-process-managers.md; with `managers.enabled = false`, an
      * `enrich-source` run queued at once, as before).
      */
    autoEnrich: AgentsConfig.AutoEnrich,
    /** A directory that overrides bundled prompts file by file, mirroring the bundled `agents/` tree
      * (`prompts/enrich-source/classify.md`, `article-skeptic.md`, …); "" = the bundled ones only.
      */
    promptsDir: AgentsConfig.PromptsDir,
    providers: AgentProvidersConfig,
    roles: AgentRolesConfig,
    prices: AgentsConfig.Prices,
    /** Per model or provider: tokens/USD per rolling window and a reserve ([[ModelChains.parseAllowances]]); an
      * allowance saved in the database for the same target wins.
      */
    allowances: AgentsConfig.Allowances,
    /** How often paused runs waiting for an allowance window are looked at (and resumed when it freed). */
    allowanceCheckSeconds: AgentsConfig.AllowanceCheckSeconds,
    /** `agent doctor`'s per-request timeout (a model listing, `claude --version`). */
    doctorTimeoutSeconds: AgentsConfig.DoctorTimeoutSeconds,
    budgets: AgentBudgetsConfig,
    maxConcurrentRuns: AgentsConfig.MaxConcurrentRuns,
    maxLlmCallsInFlight: AgentsConfig.MaxLlmCallsInFlight,
    step: AgentStepConfig,
    tools: AgentToolsConfig,
    trace: AgentTraceConfig,
    approvals: AgentApprovalsConfig,
    plan: AgentPlanConfig,
    scheduler: AgentSchedulerConfig,
    watchdog: AgentWatchdogConfig,
    managers: AgentManagersConfig,
    runtimes: AgentRuntimesConfig,
    quota: QuotaConfig,
    autopilot: AgentAutopilotConfig = AgentAutopilotConfig()
) derives ConfigReader

object AgentsConfig:
  type Enabled = Enabled.T
  object Enabled extends RefinedType[Boolean, Pure]
  type AutoEnrich = AutoEnrich.T
  object AutoEnrich extends RefinedType[Boolean, Pure]
  type PromptsDir = PromptsDir.T
  object PromptsDir extends RefinedType[String, Pure]
  type Prices = Prices.T
  object Prices extends RefinedType[String, Pure]
  type Allowances = Allowances.T
  object Allowances extends RefinedType[String, Pure]
  type AllowanceCheckSeconds = AllowanceCheckSeconds.T
  object AllowanceCheckSeconds extends RefinedType[Int, Pure]
  type DoctorTimeoutSeconds = DoctorTimeoutSeconds.T
  object DoctorTimeoutSeconds extends RefinedType[Int, Pure]
  type MaxConcurrentRuns = MaxConcurrentRuns.T
  object MaxConcurrentRuns extends RefinedType[Int, Pure]
  type MaxLlmCallsInFlight = MaxLlmCallsInFlight.T
  object MaxLlmCallsInFlight extends RefinedType[Int, Pure]
