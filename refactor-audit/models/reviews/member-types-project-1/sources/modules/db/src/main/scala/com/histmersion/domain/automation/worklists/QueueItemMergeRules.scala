package com.histmersion.domain.automation.worklists

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules

/** How a merge treats the work lists' items: the ledger names one by the start of its text. */
object QueueItemMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val owner: DataOwner = DataOwner("automation")
  val kind: Bundle.Kind = Bundle.Kind.QueueItem
  override val ledgerLabel: Option[com.histmersion.domain.automation.worklists.QueueItemMergeRules.LedgerLabel] = Some("SELECT left(text, 60) FROM queue_items WHERE uid = l.local_uid")
