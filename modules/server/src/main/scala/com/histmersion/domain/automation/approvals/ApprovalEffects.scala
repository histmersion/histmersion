package com.histmersion
package domain.automation.approvals
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.Approval
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What approving a proposal does, contributed by the domain the proposal is about: a source's status or edge (the
  * library), an article's coverage filter (research). The engine claims the decision and records the outcome; the
  * domain applies the change through its own operations, as a person's patch would.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait ApprovalEffects:
  /** A source's proposal as its stored payload reads (None: none this server can read), ready to apply: what applying
    * it did, or why it failed.
    */
  def proposal(payload: ApprovalEffects.Payload): Option[Creator.New ?=> Either[Problems, String]]

  /** The kinds of proposal about a subject (an article, not a source) this applies. */
  def subjectKinds: Set[Approval.Kind]

  /** Apply an approved proposal of one of [[subjectKinds]]: what it did, or why it failed. */
  def applySubject(a: ApprovalInfo)(using Creator.New): Either[Problem, String]

object ApprovalEffects:
  type Payload = Payload.T
  object Payload extends Newtype[String, Pure]

  /** The effects the domains contribute, as one: the library's source proposals and each subject kind's own. */

  def of(proposals: ProposalEffect, subjects: List[SubjectEffect]): ApprovalEffects = new ApprovalEffects:
    def proposal(payload: Payload): Option[Creator.New ?=> Either[Problems, String]] = proposals.proposal(payload)
    val subjectKinds: Set[Approval.Kind] = subjects.map(_.kind).toSet
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def applySubject(a: ApprovalInfo)(using Creator.New): Either[Problem, String] =
      subjects.find(s => Approval.Kind.parse(Approval.Kind.Wire(a.kind.value)).contains(s.kind)) match
        case Some(s) => s.apply(a)
        case None    =>
          Left(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(Approval.Kind.Wire),
              Problem.Raw.of(a.kind),
              subjects.toVector.map(s => Problem.Raw.of(s.kind.wire))
            )
          )
