package com.histmersion
package domain.automation.goals

import com.histmersion.api.exchange.Bundle
import com.augustnagro.magnum.{sql, Frag}

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.MergeRules
import com.histmersion.lint.{LintRule, tolerated}

/** How a merge treats goals: other lines point at them by uid, and the ledger names one by its slug. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object GoalMergeRules extends MergeRules:

  val owner: DataOwner = DataOwner("automation")
  val kind: Bundle.Kind = Bundle.Kind.Goal
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT slug FROM goals WHERE uid = l.local_uid")
  override val referable: Boolean = true
