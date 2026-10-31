package com.histmersion.plugins.ai

import hearth.kindlings.sconfigderivation.ConfigReader

/** `agents.providers` as the host supplies it to one of these plugins: that plugin's own provider only. */
final case class HostProviders(
    anthropic: Option[ProviderSettings] = None,
    openai: Option[ProviderSettings] = None,
    local: Option[ProviderSettings] = None
) derives ConfigReader
