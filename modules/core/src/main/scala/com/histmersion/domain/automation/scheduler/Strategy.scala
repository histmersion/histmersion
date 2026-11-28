package com.histmersion
package domain.automation.scheduler

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.SchedulerPolicy
import com.histmersion.domain.automation.scheduler.PassState
import com.histmersion.domain.automation.scheduler.Scored
import com.histmersion.domain.automation.scheduler.Settings

trait Strategy:
  def name: SchedulerPolicy.StrategyName
  def describe: String
  def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)]

  def behindWhy(x: Scored, ahead: Scored, s: Settings): String
