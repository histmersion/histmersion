package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
final case class AgentProviderConfig(baseUrl: String, apiKey: String, strictTools: Boolean, timeoutSeconds: Int)
    derives ConfigReader:
  override def toString: String =
    s"AgentProviderConfig($baseUrl, apiKey=${if apiKey.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"
