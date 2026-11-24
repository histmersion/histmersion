package com.histmersion
package domain.automation.managers

import com.histmersion.config.{AgentManagersConfig, AgentSchedulerConfig}
import com.histmersion.domain.{ManagerDefaults, SchedulerDefaults}
import io.scalaland.chimney.dsl.*
import com.histmersion.lint.{LintRule, tolerated}

object ManagerDefaultsViews:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def defaults(managers: AgentManagersConfig): ManagerDefaults = managers.transformInto[ManagerDefaults]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def scheduler(c: AgentSchedulerConfig): SchedulerDefaults =
    c.into[SchedulerDefaults]
      .withFieldConst(_.builtInWeights, SchedulerDefaults.BuiltInWeights(AgentSchedulerConfig.DefaultWeights.value))
      .withFieldConst(
        _.builtInPriorities,
        SchedulerDefaults.BuiltInPriorities(AgentSchedulerConfig.DefaultPriorities.value)
      )
      .transform
