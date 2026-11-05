package com.histmersion.domain.automation.approvals

/** What approving a source's proposal does, contributed by the library: the stored payload read (None: none this server
  * can read), ready to apply — what applying it did, or why it failed.
  */
trait ProposalEffect:
  def proposal(payload: com.histmersion.domain.automation.approvals.ProposalEffect.ProposalPayload): Option[() => Either[com.histmersion.domain.automation.approvals.ProposalEffect.ProposalResultResultPart1, com.histmersion.domain.automation.approvals.ProposalEffect.ProposalResultResultPart2]]

object ProposalEffect:
  type ProposalResultResultPart1 = ProposalResultResultPart1.T
  object ProposalResultResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposalResultResultPart2 = ProposalResultResultPart2.T
  object ProposalResultResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposalPayload = ProposalPayload.T
  object ProposalPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
