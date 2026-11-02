package com.histmersion.domain.library.sources

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}
import com.histmersion.values.BundleKind

/** How a merge treats findings: the ledger names one by its id; no line points at one by uid. */
object FindingMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("library")
  val kind: BundleKind = BundleKind.Finding
  override val ledgerLabel: Option[String] = Some("SELECT id FROM findings WHERE uid = l.local_uid")
  override val planning: Option[PlanRules] = Some(LibraryPlan)
