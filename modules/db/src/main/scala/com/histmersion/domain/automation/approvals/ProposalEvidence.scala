package com.histmersion
package domain.automation.approvals

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What every approval's list shows of a proposal about a record of a domain (a source's status or edge, the library's
  * proposals): the evidence it rests on and the passage it quotes, read from its stored payload.
  */
final case class ProposalEvidence(evidence: Option[ProposalEvidence.Evidence] = None, quote: Option[ProposalEvidence.Quote] = None)
    derives KindlingsCodecAsObject

object ProposalEvidence:
  type Evidence = Evidence.T
  object Evidence extends RefinedType[String, Pure]:
    given io.circe.Encoder[Evidence] = io.circe.Encoder.encodeString.contramap(_.value)
    given io.circe.Decoder[Evidence] = io.circe.Decoder.decodeString.map(Evidence(_))
  type Quote = Quote.T
  object Quote extends RefinedType[String, Pure]:
    given io.circe.Encoder[Quote] = io.circe.Encoder.encodeString.contramap(_.value)
    given io.circe.Decoder[Quote] = io.circe.Decoder.decodeString.map(Quote(_))
