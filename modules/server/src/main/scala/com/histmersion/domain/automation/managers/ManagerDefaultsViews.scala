package com.histmersion
package domain.automation.managers

import com.histmersion.config.{AgentManagersConfig, AgentSchedulerConfig}
import com.histmersion.domain.{ManagerDefaults, SchedulerDefaults}
import io.scalaland.chimney.dsl.*

object ManagerDefaultsViews:
  def defaults(managers: AgentManagersConfig): ManagerDefaults = managers.transformInto[ManagerDefaults]

  def scheduler(c: AgentSchedulerConfig): SchedulerDefaults =
    c.into[SchedulerDefaults]
      .withFieldConst(_.builtInWeights, SchedulerDefaults.BuiltInWeights(AgentSchedulerConfig.DefaultWeights.value))
      .withFieldConst(
        _.builtInPriorities,
        SchedulerDefaults.BuiltInPriorities(AgentSchedulerConfig.DefaultPriorities.value)
      )
      .transform
