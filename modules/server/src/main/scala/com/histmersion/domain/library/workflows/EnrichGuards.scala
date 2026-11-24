package com.histmersion
package domain.library.workflows

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.config.AgentEnrichGuardsConfig
import com.histmersion.domain.EnrichSource
import com.histmersion.lint.{LintRule, tolerated}

extension (c: AgentEnrichGuardsConfig)
  /** The findings step's guards as `agents.enrich.guards` sets them, over the gazetteer `resolves` asks. */
  @tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def guards(
      resolves: (EnrichSource.PlaceQuery, Geocode.KindHint, Option[Period]) => Boolean
  ): EnrichSource.FindingGuards =
    EnrichSource.FindingGuards(
      EnrichSource.FindingGuards.MinPlacedShare(c.minPlacedShare.value),
      EnrichSource.FindingGuards.CenturyGapYears(c.centuryGapYears.value.toLong),
      c.phrases.map(p => EnrichSource.FindingGuards.PointerPhrases(p.value)),
      resolves
    )
