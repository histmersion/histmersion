package com.histmersion.domain.automation.approvals

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.values.ApprovalKind

/** What approving one kind of proposal about a subject (an article, not a source) does, contributed by the domain the
  * subject is in: what it did, or why it failed.
  */
trait SubjectEffect:
  def kind: ApprovalKind
  def apply(a: ApprovalInfo): Either[String, String]
