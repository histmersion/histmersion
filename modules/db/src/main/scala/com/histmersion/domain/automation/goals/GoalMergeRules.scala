package com.histmersion.domain.automation.goals

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules

/** How a merge treats goals: other lines point at them by uid, and the ledger names one by its slug. */
object GoalMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Referable = Referable.T
  object Referable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val owner: DataOwner = DataOwner("automation")
  val kind: Bundle.Kind = Bundle.Kind.Goal
  override val ledgerLabel: Option[com.histmersion.domain.automation.goals.GoalMergeRules.LedgerLabel] = Some("SELECT slug FROM goals WHERE uid = l.local_uid")
  override val referable: com.histmersion.domain.automation.goals.GoalMergeRules.Referable = true
