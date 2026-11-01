package com.histmersion.domain.automation.approvals

import com.histmersion.api.Agents.ApprovalInfo
import com.histmersion.values.ApprovalKind

/** What approving a proposal does, contributed by the domain the proposal is about: a source's status or edge (the
  * library), an article's coverage filter (research). The engine claims the decision and records the outcome; the
  * domain applies the change through its own operations, as a person's patch would.
  */
trait ApprovalEffects:
  /** A source's proposal as its stored payload reads (None: none this server can read), ready to apply: what applying
    * it did, or why it failed.
    */
  def proposal(payload: String): Option[() => Either[String, String]]

  /** The kinds of proposal about a subject (an article, not a source) this applies. */
  def subjectKinds: Set[ApprovalKind]

  /** Apply an approved proposal of one of [[subjectKinds]]: what it did, or why it failed. */
  def applySubject(a: ApprovalInfo): Either[String, String]
