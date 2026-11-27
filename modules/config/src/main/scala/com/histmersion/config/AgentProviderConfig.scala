package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Embedded research agents (docs/research-embedded-agents.md §5.3). OFF by default: with `enabled = false` the server
  * refuses every agent run and the external Claude Code workflow is untouched.
  *
  * API keys come from the environment only (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`); they never reach the database,
  * `/api/about`, a log line or a trace — [[AgentProviderConfig.toString]] redacts them.
  *
  * Several values are strings that a pure parser in [[Agents]] turns into types, because an env override can only ever
  * be a string: `roles.*` are `provider:model` references (`Agents.ModelRef`) and `prices` is a price table
  * (`Agents.Prices`).
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class AgentProviderConfig(
    baseUrl: AgentProviderConfig.BaseUrl,
    apiKey: AgentProviderConfig.ApiKey,
    strictTools: AgentProviderConfig.StrictTools,
    timeoutSeconds: AgentProviderConfig.TimeoutSeconds
) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def toString: String =
    s"AgentProviderConfig($baseUrl, apiKey=${if apiKey.value.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"

object AgentProviderConfig:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends Newtype[String, Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends Newtype[String, Pure]
  type StrictTools = StrictTools.T
  object StrictTools extends Newtype[Boolean, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
