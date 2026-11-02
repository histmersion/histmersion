package com.histmersion.domain.library.workflows

import com.histmersion.api.{Geocode, Period}
import com.histmersion.config.AgentEnrichGuardsConfig
import com.histmersion.domain.EnrichSource

extension (c: AgentEnrichGuardsConfig)
  /** The findings step's guards as `agents.enrich.guards` sets them, over the gazetteer `resolves` asks. */
  def guards(resolves: (String, Geocode.KindHint, Option[Period]) => Boolean): EnrichSource.FindingGuards =
    EnrichSource.FindingGuards(c.minPlacedShare, c.centuryGapYears.toLong, c.phrases, resolves)
