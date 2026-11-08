package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `plan-goal`: at most this many sub-goals, queries and works in one plan (a longer answer is sent back), each query
  * at most `queryChars` long.
  */
final case class AgentPlanConfig(
    maxSubGoals: AgentPlanConfig.MaxSubGoals,
    maxQueries: AgentPlanConfig.MaxQueries,
    maxWorks: AgentPlanConfig.MaxWorks,
    queryChars: AgentPlanConfig.QueryChars
) derives ConfigReader

object AgentPlanConfig:
  type MaxSubGoals = MaxSubGoals.T
  object MaxSubGoals extends Newtype[Int, Pure]
  type MaxQueries = MaxQueries.T
  object MaxQueries extends Newtype[Int, Pure]
  type MaxWorks = MaxWorks.T
  object MaxWorks extends Newtype[Int, Pure]
  type QueryChars = QueryChars.T
  object QueryChars extends Newtype[Int, Pure]
