package com.histmersion
package util.data.exchange

import com.histmersion.domain.MergePlanner

/** A domain's own rules of a merge plan ([[MergePlanner.plan]]): the matching, folding and questions of its kinds of
  * record. Each plan starts its own run.
  */
trait PlanRules:
  def start(in: MergePlanner.Input): PlanRun
