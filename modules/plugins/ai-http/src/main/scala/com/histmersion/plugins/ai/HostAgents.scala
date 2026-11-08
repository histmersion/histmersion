package com.histmersion
package plugins.ai

import hearth.kindlings.sconfigderivation.ConfigReader

/** `agents` as the host supplies it: the providers. */
final case class HostAgents(providers: HostProviders) derives ConfigReader
