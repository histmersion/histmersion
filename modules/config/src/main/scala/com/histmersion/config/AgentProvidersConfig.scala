package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{FeaturePlace, movesTo}

/** One entry per provider kind — the provider half of a `provider:model` reference. */
@movesTo(FeaturePlace.AutomationAgents)
final case class AgentProvidersConfig(
    anthropic: AgentProviderConfig,
    openai: AgentProviderConfig,
    local: AgentProviderConfig,
    claudeCode: ClaudeCodeConfig,
    codex: CodexConfig,
    opencode: OpencodeConfig
) derives ConfigReader
