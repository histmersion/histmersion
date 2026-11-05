package com.histmersion.domain.automation.approvals

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.Approval

/** What approving one kind of proposal about a subject (an article, not a source) does, contributed by the domain the
  * subject is in: what it did, or why it failed.
  */
trait SubjectEffect:
  def kind: Approval.Kind
  def apply(a: ApprovalInfo): Either[com.histmersion.domain.automation.approvals.SubjectEffect.ApplyResultPart1, com.histmersion.domain.automation.approvals.SubjectEffect.ApplyResultPart2]

object SubjectEffect:
  type ApplyResultPart1 = ApplyResultPart1.T
  object ApplyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyResultPart2 = ApplyResultPart2.T
  object ApplyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
