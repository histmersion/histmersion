package com.histmersion.server.agents

import com.histmersion.config.{AgentManagersConfig, AgentSeedConfig}
import com.histmersion.domain.{ManagerDefaults, SeedPlanDefaults}
import io.scalaland.chimney.dsl.*

object ManagerDefaultsViews:
  def defaults(managers: AgentManagersConfig): ManagerDefaults = managers.transformInto[ManagerDefaults]

  def seedPlan(seed: AgentSeedConfig): SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]
