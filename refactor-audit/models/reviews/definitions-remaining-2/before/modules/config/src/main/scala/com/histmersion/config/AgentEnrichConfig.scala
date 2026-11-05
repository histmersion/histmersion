package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `enrich-source`: the body is sent as at most `maxWindows` windows of `windowChars` characters (by heading where the
  * source has a table of contents); the classify step sees the first `headChars`. `upstream` = check the record against
  * OpenAlex/Crossref and run `refs`/`assess` (network calls; off, those steps record that they were skipped). `guards`
  * are the findings step's extra checks ([[AgentEnrichGuardsConfig]]).
  */
final case class AgentEnrichConfig(
    windowChars: Int,
    maxWindows: Int,
    headChars: Int,
    upstream: Boolean,
    guards: AgentEnrichGuardsConfig
) derives ConfigReader
