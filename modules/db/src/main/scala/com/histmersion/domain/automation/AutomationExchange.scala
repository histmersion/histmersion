package com.histmersion
package domain.automation

import com.histmersion.domain.automation.goals.{GoalMergeRules, GoalsCarried}
import com.histmersion.domain.automation.worklists.{QueueCarried, QueueItemMergeRules}
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}

/** What the automation carries in a bundle and how a merge treats its records: the goals and the work lists' items. */
object AutomationExchange:

  /** The goals, the work lists' items and the goals' old slugs, in the order the registry has always listed them. */
  val carried: List[CarriedKind] = List(GoalsCarried.goals, QueueCarried.items, GoalsCarried.aliases)

  /** How a merge treats goals and the work lists' items. */
  val mergeRules: List[MergeRules] = List(GoalMergeRules, QueueItemMergeRules)
