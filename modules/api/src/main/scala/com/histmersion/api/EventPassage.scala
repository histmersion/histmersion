package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventPassage(start: Int, end: Int, quote: String, name: String, context: String = "")
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventPassage:
  given Schema[EventPassage] = summon[KindlingsSchema[EventPassage]].schema
