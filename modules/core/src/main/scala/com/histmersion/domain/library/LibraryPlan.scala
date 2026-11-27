package com.histmersion
package domain.library

import com.histmersion.domain.MergePlanner
import com.histmersion.domain.library.findings.FindingPlan
import com.histmersion.domain.library.sources.SourcePlan
import com.histmersion.util.data.exchange.{PlanRules, PlanRun, PlanRuns}
import com.histmersion.lint.{tolerated, LintRule}

/** The library's rules of a merge plan: its sources, then their findings. */
object LibraryPlan extends PlanRules:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def start(in: MergePlanner.Input): PlanRun = PlanRuns(List(SourcePlan(in), FindingPlan(in)))
