package com.histmersion
package domain.library.sources

import com.histmersion.api.exchange.Bundle
import com.augustnagro.magnum.{sql, Frag}

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

/** How a merge treats sources: other lines point at them by uid, and the ledger names one by its citekey. */
object SourceMergeRules extends MergeRules:

  val owner: DataOwner = DataOwner("library")
  val kind: Bundle.Kind = Bundle.Kind.Source
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT citekey FROM sources WHERE uid = l.local_uid")
  override val referable: Boolean = true
  override val planning: Option[PlanRules] = Some(LibraryPlan)
