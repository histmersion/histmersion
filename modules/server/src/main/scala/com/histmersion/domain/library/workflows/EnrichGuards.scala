package com.histmersion
package domain.library.workflows

import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.domain.enrichsource.FindingGuards

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.config.AgentEnrichGuardsConfig
import com.histmersion.domain.EnrichSource
import com.histmersion.lint.{tolerated, LintRule}

extension (c: AgentEnrichGuardsConfig)
  /** The findings step's guards as `agents.enrich.guards` sets them, over the gazetteer `resolves` asks. */
  @tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def guards(
      resolves: (EnrichSource.PlaceQuery, KindHint, Option[Period]) => Boolean
  ): FindingGuards =
    FindingGuards(
      FindingGuards.MinPlacedShare(c.minPlacedShare.value),
      FindingGuards.CenturyGapYears(c.centuryGapYears.value.toLong),
      c.phrases.map(p => FindingGuards.PointerPhrases(p.value)),
      resolves
    )
