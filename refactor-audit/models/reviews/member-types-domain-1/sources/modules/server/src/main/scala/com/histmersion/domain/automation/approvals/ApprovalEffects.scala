package com.histmersion.domain.automation.approvals

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.Approval

/** What approving a proposal does, contributed by the domain the proposal is about: a source's status or edge (the
  * library), an article's coverage filter (research). The engine claims the decision and records the outcome; the
  * domain applies the change through its own operations, as a person's patch would.
  */
trait ApprovalEffects:
  /** A source's proposal as its stored payload reads (None: none this server can read), ready to apply: what applying
    * it did, or why it failed.
    */
  def proposal(payload: com.histmersion.domain.automation.approvals.ApprovalEffects.ProposalPayload): Option[() => Either[com.histmersion.domain.automation.approvals.ApprovalEffects.ProposalResultResultPart1, com.histmersion.domain.automation.approvals.ApprovalEffects.ProposalResultResultPart2]]

  /** The kinds of proposal about a subject (an article, not a source) this applies. */
  def subjectKinds: Set[Approval.Kind]

  /** Apply an approved proposal of one of [[subjectKinds]]: what it did, or why it failed. */
  def applySubject(a: ApprovalInfo): Either[com.histmersion.domain.automation.approvals.ApprovalEffects.ApplySubjectResultPart1, com.histmersion.domain.automation.approvals.ApprovalEffects.ApplySubjectResultPart2]

object ApprovalEffects:
  /** The effects the domains contribute, as one: the library's source proposals and each subject kind's own. */
  type ProposalResultResultPart1 = ProposalResultResultPart1.T
  object ProposalResultResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposalResultResultPart2 = ProposalResultResultPart2.T
  object ProposalResultResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposalPayload = ProposalPayload.T
  object ProposalPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySubjectResultPart1 = ApplySubjectResultPart1.T
  object ApplySubjectResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySubjectResultPart2 = ApplySubjectResultPart2.T
  object ApplySubjectResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(proposals: ProposalEffect, subjects: List[SubjectEffect]): ApprovalEffects = new ApprovalEffects:
    def proposal(payload: String): Option[() => Either[String, String]] = proposals.proposal(payload)
    val subjectKinds: Set[Approval.Kind] = subjects.map(_.kind).toSet
    def applySubject(a: ApprovalInfo): Either[String, String] =
      subjects.find(s => Approval.Kind.parse(a.kind).contains(s.kind)) match
        case Some(s) => s.apply(a)
        case None    => Left(s"no domain applies a ${a.kind} approval")
