package com.histmersion.domain.automation.goals

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.values.BundleKind

/** How a merge treats goals: other lines point at them by uid, and the ledger names one by its slug. */
object GoalMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("automation")
  val kind: BundleKind = BundleKind.Goal
  override val ledgerLabel: Option[String] = Some("SELECT slug FROM goals WHERE uid = l.local_uid")
  override val referable: Boolean = true
