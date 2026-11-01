package com.histmersion.domain.automation.managers

import com.histmersion.config.{AgentManagersConfig, AgentSchedulerConfig, AgentSeedConfig}
import com.histmersion.domain.{ManagerDefaults, SchedulerDefaults, SeedPlanDefaults}
import io.scalaland.chimney.dsl.*

object ManagerDefaultsViews:
  def defaults(managers: AgentManagersConfig): ManagerDefaults = managers.transformInto[ManagerDefaults]

  def seedPlan(seed: AgentSeedConfig): SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]

  def scheduler(c: AgentSchedulerConfig): SchedulerDefaults =
    c.into[SchedulerDefaults]
      .withFieldConst(_.builtInWeights, AgentSchedulerConfig.DefaultWeights)
      .withFieldConst(_.builtInPriorities, AgentSchedulerConfig.DefaultPriorities)
      .transform
