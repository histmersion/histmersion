package com.histmersion.domain.library.sources

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

/** How a merge treats findings: the ledger names one by its id; no line points at one by uid. */
object FindingMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val owner: DataOwner = DataOwner("library")
  val kind: Bundle.Kind = Bundle.Kind.Finding
  override val ledgerLabel: Option[com.histmersion.domain.library.sources.FindingMergeRules.LedgerLabel] = Some("SELECT id FROM findings WHERE uid = l.local_uid")
  override val planning: Option[PlanRules] = Some(LibraryPlan)
