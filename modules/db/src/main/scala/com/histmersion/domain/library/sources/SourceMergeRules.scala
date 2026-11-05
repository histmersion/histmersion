package com.histmersion.domain.library.sources

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.DataOwner
import com.histmersion.domain.library.LibraryPlan
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

/** How a merge treats sources: other lines point at them by uid, and the ledger names one by its citekey. */
object SourceMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Referable = Referable.T
  object Referable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val owner: DataOwner = DataOwner("library")
  val kind: Bundle.Kind = Bundle.Kind.Source
  override val ledgerLabel: Option[com.histmersion.domain.library.sources.SourceMergeRules.LedgerLabel] = Some("SELECT citekey FROM sources WHERE uid = l.local_uid")
  override val referable: com.histmersion.domain.library.sources.SourceMergeRules.Referable = true
  override val planning: Option[PlanRules] = Some(LibraryPlan)
