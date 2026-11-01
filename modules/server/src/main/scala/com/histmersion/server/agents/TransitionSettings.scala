package com.histmersion.server.agents

import com.histmersion.api.{Geocode, Period}
import com.histmersion.config.{AgentEnrichGuardsConfig, AgentInferFilterConfig, AgentSeedConfig}
import com.histmersion.domain.{EnrichSource, FilterInference, SeedPlanDefaults}
import io.scalaland.chimney.dsl.*

// The library's and research's agent settings as their pipelines read them, beside the transition adapters until
// those domains own their settings.

extension (c: AgentInferFilterConfig)
  def limits: FilterInference.Limits = FilterInference.Limits(
    math.max(1, c.minHits),
    math.max(c.minHits, c.maxHits),
    math.min(1.0, math.max(0.0, c.minCitedShare))
  )

extension (c: AgentEnrichGuardsConfig)
  def guards(resolves: (String, Geocode.KindHint, Option[Period]) => Boolean): EnrichSource.FindingGuards =
    EnrichSource.FindingGuards(c.minPlacedShare, c.centuryGapYears.toLong, c.phrases, resolves)

extension (seed: AgentSeedConfig)
  /** The seeding defaults a seed manager's plan is held to. */
  def seedPlan: SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]
