package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `plan-goal`: at most this many sub-goals, queries and works in one plan (a longer answer is sent back), each query
  * at most `queryChars` long.
  */
final case class AgentPlanConfig(maxSubGoals: AgentPlanConfig.MaxSubGoals, maxQueries: AgentPlanConfig.MaxQueries, maxWorks: AgentPlanConfig.MaxWorks, queryChars: AgentPlanConfig.QueryChars) derives ConfigReader

object AgentPlanConfig:
  type MaxSubGoals = MaxSubGoals.T
  object MaxSubGoals extends RefinedType[Int, Pure]
  type MaxQueries = MaxQueries.T
  object MaxQueries extends RefinedType[Int, Pure]
  type MaxWorks = MaxWorks.T
  object MaxWorks extends RefinedType[Int, Pure]
  type QueryChars = QueryChars.T
  object QueryChars extends RefinedType[Int, Pure]
