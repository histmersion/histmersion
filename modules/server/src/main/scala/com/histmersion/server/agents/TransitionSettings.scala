package com.histmersion.server.agents

import com.histmersion.config.AgentSeedConfig
import com.histmersion.domain.SeedPlanDefaults
import io.scalaland.chimney.dsl.*

// The seeding settings as the seed kind reads them, beside the transition adapters until its owner reads its own.

extension (seed: AgentSeedConfig)
  /** The seeding defaults a seed manager's plan is held to. */
  def seedPlan: SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]
