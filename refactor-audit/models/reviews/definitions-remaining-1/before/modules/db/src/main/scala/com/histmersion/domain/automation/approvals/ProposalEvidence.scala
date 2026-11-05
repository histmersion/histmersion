package com.histmersion.domain.automation.approvals

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** What every approval's list shows of a proposal about a record of a domain (a source's status or edge, the library's
  * proposals): the evidence it rests on and the passage it quotes, read from its stored payload.
  */
final case class ProposalEvidence(evidence: Option[String] = None, quote: Option[String] = None)
    derives KindlingsCodecAsObject
