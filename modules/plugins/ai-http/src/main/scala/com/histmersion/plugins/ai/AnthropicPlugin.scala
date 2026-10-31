package com.histmersion.plugins.ai

import com.histmersion.plugin.AiProvider

/** `ai-anthropic`: the Anthropic Messages API (`anthropic:<model>`). */
final class AnthropicPlugin extends AiHttpPlugin("ai-anthropic", "anthropic"):
  def settings(providers: HostProviders): Option[ProviderSettings] = providers.anthropic
  def provider(settings: ProviderSettings): AiProvider = AnthropicProvider(settings)
