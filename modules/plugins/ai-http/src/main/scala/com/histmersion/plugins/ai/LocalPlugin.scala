package com.histmersion
package plugins.ai

import com.histmersion.plugin.AiProvider

/** `ai-local`: any OpenAI-compatible local server — llama.cpp, LM Studio, vLLM, Ollama (`local:<model>`). */
final class LocalPlugin extends AiHttpPlugin("ai-local", "local"):
  def settings(providers: HostProviders): Option[ProviderSettings] = providers.local
  def provider(settings: ProviderSettings): AiProvider = ChatCompletionsProvider.local(settings)
