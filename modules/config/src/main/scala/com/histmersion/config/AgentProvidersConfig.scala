package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** One entry per provider kind — the provider half of a `provider:model` reference. */
final case class AgentProvidersConfig(
    anthropic: AgentProviderConfig,
    openai: AgentProviderConfig,
    local: AgentProviderConfig,
    claudeCode: ClaudeCodeConfig,
    codex: CodexConfig,
    opencode: OpencodeConfig
) derives ConfigReader
