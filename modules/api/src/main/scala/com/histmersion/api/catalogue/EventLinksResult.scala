package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventLinksResult(
    links: EventLinkList,
    attested: List[Attestation] = Nil,
    refused: List[String] = Nil,
    rejected: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLinksResult:
  given Schema[EventLinksResult] = summon[KindlingsSchema[EventLinksResult]].schema
