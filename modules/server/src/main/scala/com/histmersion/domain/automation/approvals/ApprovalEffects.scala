package com.histmersion
package domain.automation.approvals
import com.histmersion.util.problem.{Legacy, Problem, Problems}

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.Approval
import io.github.iltotore.iron.constraint.any.Pure

/** What approving a proposal does, contributed by the domain the proposal is about: a source's status or edge (the
  * library), an article's coverage filter (research). The engine claims the decision and records the outcome; the
  * domain applies the change through its own operations, as a person's patch would.
  */
trait ApprovalEffects:
  /** A source's proposal as its stored payload reads (None: none this server can read), ready to apply: what applying
    * it did, or why it failed.
    */
  def proposal(payload: ApprovalEffects.Payload): Option[() => Either[Problems, String]]

  /** The kinds of proposal about a subject (an article, not a source) this applies. */
  def subjectKinds: Set[Approval.Kind]

  /** Apply an approved proposal of one of [[subjectKinds]]: what it did, or why it failed. */
  def applySubject(a: ApprovalInfo): Either[Problem, String]

object ApprovalEffects:
  type Payload = Payload.T
  object Payload extends Newtype[String, Pure]

  /** The effects the domains contribute, as one: the library's source proposals and each subject kind's own. */

  def of(proposals: ProposalEffect, subjects: List[SubjectEffect]): ApprovalEffects = new ApprovalEffects:
    def proposal(payload: Payload): Option[() => Either[Problems, String]] = proposals.proposal(payload)
    val subjectKinds: Set[Approval.Kind] = subjects.map(_.kind).toSet
    def applySubject(a: ApprovalInfo): Either[Problem, String] =
      subjects.find(s => Approval.Kind.parse(Approval.Kind.Wire(a.kind.value)).contains(s.kind)) match
        case Some(s) => s.apply(a)
        case None    => Left(Problem.Unclassified(Legacy.Text(s"no domain applies a ${a.kind} approval")))
