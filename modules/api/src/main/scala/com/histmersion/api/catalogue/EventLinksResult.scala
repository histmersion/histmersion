package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventLinksResult(
    links: EventLinkList,
    attested: List[Attestation] = Nil,
    refused: List[EventLinksResult.Refused] = Nil,
    rejected: List[EventLinksResult.Rejected] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventLinksResult:
  type Refused = Refused.T
  object Refused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rejected = Rejected.T
  object Rejected extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
