package com.histmersion.domain.library.workflows

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.automation.approvals.ProposalEffect
import com.histmersion.domain.library.sources.SourcesControllers

/** What approving a source's proposal (a status or an edge an agent proposed) does: the patch it carries, through the
  * sources' own controllers, exactly as a person would send it.
  */
final class SourceProposalEffect(sources: => SourcesControllers) extends ProposalEffect:
  def proposal(payload: com.histmersion.domain.library.workflows.SourceProposalEffect.ProposalPayload): Option[() => Either[com.histmersion.domain.library.workflows.SourceProposalEffect.ProposalResultResultPart1, com.histmersion.domain.library.workflows.SourceProposalEffect.ProposalResultResultPart2]] =
    Jsonb
      .read[Proposal](payload)
      .map(p => () => sources.patch(p.citekey, p.patch).left.map(_.message).map(_ => p.describe))

object SourceProposalEffect:
  type ProposalResultResultPart1 = ProposalResultResultPart1.T
  object ProposalResultResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposalResultResultPart2 = ProposalResultResultPart2.T
  object ProposalResultResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposalPayload = ProposalPayload.T
  object ProposalPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
