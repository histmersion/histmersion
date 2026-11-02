package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventPassage(start: Int, end: Int, quote: String, name: String, context: String = "")
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventPassage:
  given Schema[EventPassage] = summon[KindlingsSchema[EventPassage]].schema
