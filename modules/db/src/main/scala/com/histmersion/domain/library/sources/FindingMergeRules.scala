package com.histmersion
package domain.library.sources

import com.histmersion.api.exchange.Bundle
import com.augustnagro.magnum.{sql, Frag}

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

/** How a merge treats findings: the ledger names one by its id; no line points at one by uid. */
object FindingMergeRules extends MergeRules:

  val owner: DataOwner = DataOwner("library")
  val kind: Bundle.Kind = Bundle.Kind.Finding
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT id FROM findings WHERE uid = l.local_uid")
  override val planning: Option[PlanRules] = Some(LibraryPlan)
