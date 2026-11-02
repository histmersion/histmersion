package com.histmersion.domain.automation.approvals

/** What approving a source's proposal does, contributed by the library: the stored payload read (None: none this server
  * can read), ready to apply — what applying it did, or why it failed.
  */
trait ProposalEffect:
  def proposal(payload: String): Option[() => Either[String, String]]
