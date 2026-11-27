package com.histmersion
package domain.automation.worklists

import com.histmersion.api.exchange.Bundle
import com.augustnagro.magnum.{sql, Frag}

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.lint.{tolerated, LintRule}

/** How a merge treats the work lists' items: the ledger names one by the start of its text. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object QueueItemMergeRules extends MergeRules:

  val owner: DataOwner = DataOwner("automation")
  val kind: Bundle.Kind = Bundle.Kind.QueueItem
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT left(text, 60) FROM queue_items WHERE uid = l.local_uid")
