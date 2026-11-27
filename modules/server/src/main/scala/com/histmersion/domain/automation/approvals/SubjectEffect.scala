package com.histmersion
package domain.automation.approvals
import com.histmersion.util.problem.Problem

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What approving one kind of proposal about a subject (an article, not a source) does, contributed by the domain the
  * subject is in: what it did, or why it failed.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait SubjectEffect:
  def kind: Approval.Kind
  def apply(a: ApprovalInfo)(using Creator.New): Either[Problem, String]
