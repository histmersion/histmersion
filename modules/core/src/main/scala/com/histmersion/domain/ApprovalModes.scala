package com.histmersion
package domain

import com.histmersion.domain.automation.Approval
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}
@movesTo(FeaturePlace.AutomationApprovals)
object ApprovalModes:
  /** A value that is not `auto` — a typo included — is the safe one. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def configured(raw: Approval.Mode.Wire): Approval.Mode = Approval.Mode.parse(raw).getOrElse(Approval.Mode.Manual)
