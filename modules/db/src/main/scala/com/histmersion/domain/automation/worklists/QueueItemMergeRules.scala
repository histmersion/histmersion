package com.histmersion.domain.automation.worklists

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.util.data.exchange.Bundle

/** How a merge treats the work lists' items: the ledger names one by the start of its text. */
object QueueItemMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("automation")
  val kind: Bundle.Kind = Bundle.Kind.QueueItem
  override val ledgerLabel: Option[String] = Some("SELECT left(text, 60) FROM queue_items WHERE uid = l.local_uid")
