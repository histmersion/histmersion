package com.histmersion.domain.automation.approvals

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** What every approval's list shows of a proposal about a record of a domain (a source's status or edge, the library's
  * proposals): the evidence it rests on and the passage it quotes, read from its stored payload.
  */
final case class ProposalEvidence(evidence: Option[ProposalEvidence.Evidence] = None, quote: Option[ProposalEvidence.Quote] = None)
    derives KindlingsCodecAsObject

object ProposalEvidence:
  type Evidence = Evidence.T
  object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quote = Quote.T
  object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
