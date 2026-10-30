package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a links call did: the candidates now, the attestations written, and the sources refused with why.
  */
final case class EventLinksResult(
    links: EventLinkList,
    attested: List[Attestation] = Nil,
    refused: List[String] = Nil,
    rejected: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLinksResult:
  given Schema[EventLinksResult] = summon[KindlingsSchema[EventLinksResult]].schema
