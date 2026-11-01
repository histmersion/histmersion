package com.histmersion.domain.catalogue.evidence

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.values.BundleKind

/** How a merge treats attestations: they travel with the sources they quote, and nothing points at them. */
object AttestationMergeRules extends MergeRules:
  override val planning: Option[PlanRules] = Some(CataloguePlan)
  val owner: DataOwner = DataOwner("catalogue")
  val kind: BundleKind = BundleKind.Attestation
