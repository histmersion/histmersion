package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.lint.{tolerated, LintRule}

/** How a merge treats attestations: they travel with the sources they quote, and nothing points at them. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AttestationMergeRules extends MergeRules:
  override val planning: Option[PlanRules] = Some(CataloguePlan)
  val owner: DataOwner = DataOwner("catalogue")
  val kind: Bundle.Kind = Bundle.Kind.Attestation
