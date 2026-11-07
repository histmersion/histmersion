package com.histmersion.domain.library.workflows

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.automation.approvals.ProposalEffect
import com.histmersion.domain.library.sources.SourcesControllers

/** What approving a source's proposal (a status or an edge an agent proposed) does: the patch it carries, through the
  * sources' own controllers, exactly as a person would send it.
  */
final class SourceProposalEffect(sources: => SourcesControllers) extends ProposalEffect:
  def proposal(payload: com.histmersion.domain.automation.approvals.ApprovalEffects.Payload): Option[() => Either[com.histmersion.domain.automation.approvals.ApprovalEffects.Problem, String]] =
    Jsonb
      .read[Proposal](Jsonb.JsonText(payload.value))
      .map(p =>
        () =>
          sources
            .patch(p.citekey, p.patch)
            .left
            .map(e => com.histmersion.domain.automation.approvals.ApprovalEffects.Problem(e.message.value))
            .map(_ => p.describe.value)
      )
