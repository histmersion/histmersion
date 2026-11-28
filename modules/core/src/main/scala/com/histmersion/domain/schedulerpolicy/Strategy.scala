package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.SchedulerPolicy
import com.histmersion.domain.schedulerpolicy.PassState
import com.histmersion.domain.schedulerpolicy.Scored
import com.histmersion.domain.schedulerpolicy.Settings

trait Strategy:
  def name: SchedulerPolicy.StrategyName
  def describe: String
  def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)]

  /** Why `x` stands behind `ahead` (the first candidate before it in the order), in a sentence. */
  def behindWhy(x: Scored, ahead: Scored, s: Settings): String
