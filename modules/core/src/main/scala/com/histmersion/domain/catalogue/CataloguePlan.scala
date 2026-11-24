package com.histmersion
package domain.catalogue

import com.histmersion.domain.MergePlanner
import com.histmersion.domain.catalogue.events.EventPlan
import com.histmersion.domain.catalogue.evidence.AttestationPlan
import com.histmersion.domain.catalogue.periods.PeriodPlan
import com.histmersion.domain.catalogue.places.PlacePlan
import com.histmersion.util.data.exchange.{PlanRules, PlanRun, PlanRuns}
import com.histmersion.lint.{LintRule, tolerated}

/** The catalogue's rules of a merge plan: its events, places, named periods and attestations, in that order. */
object CataloguePlan extends PlanRules:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def start(in: MergePlanner.Input): PlanRun =
    PlanRuns(List(EventPlan(in), PlacePlan(in), PeriodPlan(in), AttestationPlan(in)))
