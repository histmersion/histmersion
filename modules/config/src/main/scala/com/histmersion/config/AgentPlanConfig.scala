package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `plan-goal`: at most this many sub-goals, queries and works in one plan (a longer answer is sent back), each query
  * at most `queryChars` long.
  */
final case class AgentPlanConfig(maxSubGoals: AgentPlanConfig.MaxSubGoals, maxQueries: AgentPlanConfig.MaxQueries, maxWorks: AgentPlanConfig.MaxWorks, queryChars: AgentPlanConfig.QueryChars) derives ConfigReader

object AgentPlanConfig:
  type MaxSubGoals = MaxSubGoals.T
  object MaxSubGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxQueries = MaxQueries.T
  object MaxQueries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxWorks = MaxWorks.T
  object MaxWorks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryChars = QueryChars.T
  object QueryChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
