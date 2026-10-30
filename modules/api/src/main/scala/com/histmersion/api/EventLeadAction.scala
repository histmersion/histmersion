package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventLeadAction(kind: String, label: String, command: String, query: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLeadAction:
  given Schema[EventLeadAction] = summon[KindlingsSchema[EventLeadAction]].schema
