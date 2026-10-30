package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventSourceRef(
    citekey: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    via: String = "citekey",
    quote: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventSourceRef:
  given Schema[EventSourceRef] = summon[KindlingsSchema[EventSourceRef]].schema
