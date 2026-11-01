package com.histmersion.domain.automation.managers

import com.histmersion.config.{AgentManagersConfig, AgentSchedulerConfig}
import com.histmersion.domain.{ManagerDefaults, SchedulerDefaults}
import io.scalaland.chimney.dsl.*

object ManagerDefaultsViews:
  def defaults(managers: AgentManagersConfig): ManagerDefaults = managers.transformInto[ManagerDefaults]

  def scheduler(c: AgentSchedulerConfig): SchedulerDefaults =
    c.into[SchedulerDefaults]
      .withFieldConst(_.builtInWeights, AgentSchedulerConfig.DefaultWeights)
      .withFieldConst(_.builtInPriorities, AgentSchedulerConfig.DefaultPriorities)
      .transform
