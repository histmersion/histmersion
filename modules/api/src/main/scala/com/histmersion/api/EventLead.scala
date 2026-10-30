package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventLead(
    eventId: String,
    label: String,
    kind: String,
    tier: String,
    years: String,
    text: String,
    actions: List[EventLeadAction] = Nil,
    findingsNear: Int = 0,
    candidates: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLead:
  given Schema[EventLead] = summon[KindlingsSchema[EventLead]].schema
