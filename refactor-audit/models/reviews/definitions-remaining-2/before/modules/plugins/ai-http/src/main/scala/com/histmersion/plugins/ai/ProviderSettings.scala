package com.histmersion.plugins.ai

import hearth.kindlings.sconfigderivation.ConfigReader

/** One HTTP provider's settings, Histmersion's own `agents.providers.<name>`: where it listens, its key (from the
  * server's environment: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`), whether tool schemas are sent in OpenAI's strict form,
  * and how long one call may take. The key never reaches a log line: [[toString]] redacts it.
  */
final case class ProviderSettings(baseUrl: String, apiKey: String, strictTools: Boolean, timeoutSeconds: Int)
    derives ConfigReader:
  override def toString: String =
    s"ProviderSettings($baseUrl, apiKey=${if apiKey.isEmpty then "unset" else "<redacted>"}, " +
      s"strictTools=$strictTools, timeoutSeconds=$timeoutSeconds)"
