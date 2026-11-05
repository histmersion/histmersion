package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `plan-goal`: at most this many sub-goals, queries and works in one plan (a longer answer is sent back), each query
  * at most `queryChars` long.
  */
final case class AgentPlanConfig(maxSubGoals: Int, maxQueries: Int, maxWorks: Int, queryChars: Int) derives ConfigReader
