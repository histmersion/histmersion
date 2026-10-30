package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventLeadList(total: Int, leads: List[EventLead]) derives KindlingsCodecAsObject, KindlingsSchema
object EventLeadList:
  given Schema[EventLeadList] = summon[KindlingsSchema[EventLeadList]].schema
