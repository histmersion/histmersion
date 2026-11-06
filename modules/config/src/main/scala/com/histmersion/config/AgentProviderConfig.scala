package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
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
final case class AgentProviderConfig(baseUrl: AgentProviderConfig.BaseUrl, apiKey: AgentProviderConfig.ApiKey, strictTools: AgentProviderConfig.StrictTools, timeoutSeconds: AgentProviderConfig.TimeoutSeconds)
    derives ConfigReader:
  override def toString: String =
    s"AgentProviderConfig($baseUrl, apiKey=${if apiKey.value.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"

object AgentProviderConfig:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrictTools = StrictTools.T
  object StrictTools extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
