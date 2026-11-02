package com.histmersion.domain.library

import com.histmersion.domain.MergePlanner
import com.histmersion.domain.library.findings.FindingPlan
import com.histmersion.domain.library.sources.SourcePlan
import com.histmersion.util.data.exchange.{PlanRules, PlanRun, PlanRuns}

/** The library's rules of a merge plan: its sources, then their findings. */
object LibraryPlan extends PlanRules:
  def start(in: MergePlanner.Input): PlanRun = PlanRuns(List(SourcePlan(in), FindingPlan(in)))
