package com.histmersion.server.agents

import com.histmersion.config.{AgentInferFilterConfig, AgentSeedConfig}
import com.histmersion.domain.{FilterInference, SeedPlanDefaults}
import io.scalaland.chimney.dsl.*

// Research's agent settings as its pipelines read them, beside the transition adapters until research owns its
// settings.

extension (c: AgentInferFilterConfig)
  def limits: FilterInference.Limits = FilterInference.Limits(
    math.max(1, c.minHits),
    math.max(c.minHits, c.maxHits),
    math.min(1.0, math.max(0.0, c.minCitedShare))
  )

extension (seed: AgentSeedConfig)
  /** The seeding defaults a seed manager's plan is held to. */
  def seedPlan: SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]
