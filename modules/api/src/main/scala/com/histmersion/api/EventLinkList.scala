package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventLinkList(eventId: String, label: String, candidates: List[EventLinkCandidate])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLinkList:
  given Schema[EventLinkList] = summon[KindlingsSchema[EventLinkList]].schema
