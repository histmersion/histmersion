package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.automation.approvals.ProposalEffect
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.automation.approvals.ApprovalEffects

/** What approving a source's proposal (a status or an edge an agent proposed) does: the patch it carries, through the
  * sources' own controllers, exactly as a person would send it.
  */
final class SourceProposalEffect(sources: => SourcesControllers) extends ProposalEffect:
  def proposal(payload: ApprovalEffects.Payload): Option[() => Either[Problems, String]] =
    Jsonb
      .read[Proposal](Jsonb.JsonText(payload.value))
      .map(p =>
        () =>
          sources
            .patch(p.citekey, p.patch)
            .left
            .map(e => e.problems)
            .map(_ => p.describe.value)
      )
