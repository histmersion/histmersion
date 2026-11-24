package com.histmersion
package domain.automation.approvals
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.domain.Creator

/** What approving a source's proposal does, contributed by the library: the stored payload read (None: none this server
  * can read), ready to apply under the proposal's creator — what applying it did, or why it failed.
  */
trait ProposalEffect:
  def proposal(payload: ApprovalEffects.Payload): Option[Creator.New ?=> Either[Problems, String]]
