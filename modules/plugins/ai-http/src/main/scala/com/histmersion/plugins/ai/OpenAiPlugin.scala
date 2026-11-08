package com.histmersion
package plugins.ai

import com.histmersion.plugin.AiProvider

/** `ai-openai`: the OpenAI chat-completions API (`openai:<model>`). */
final class OpenAiPlugin extends AiHttpPlugin("ai-openai", "openai"):
  def settings(providers: HostProviders): Option[ProviderSettings] = providers.openai
  def provider(settings: ProviderSettings): AiProvider = ChatCompletionsProvider.openAi(settings)
