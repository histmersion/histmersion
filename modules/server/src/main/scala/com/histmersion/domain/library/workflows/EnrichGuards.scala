package com.histmersion
package domain.library.workflows

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.config.AgentEnrichGuardsConfig
import com.histmersion.domain.EnrichSource

extension (c: AgentEnrichGuardsConfig)
  /** The findings step's guards as `agents.enrich.guards` sets them, over the gazetteer `resolves` asks. */
  def guards(resolves: (EnrichSource.PlaceQuery, Geocode.KindHint, Option[Period]) => Boolean): EnrichSource.FindingGuards =
    EnrichSource.FindingGuards(
      EnrichSource.FindingGuards.MinPlacedShare(c.minPlacedShare.value),
      EnrichSource.FindingGuards.CenturyGapYears(c.centuryGapYears.value.toLong),
      c.phrases.map(p => EnrichSource.FindingGuards.PointerPhrases(p.value)),
      resolves
    )
