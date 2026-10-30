package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Per-source dating of an event (the ChronOntology pattern). */
final case class EventDating(
    eventId: String,
    citekey: String,
    timeSpan: TimeSpan,
    method: Option[String] = None,
    note: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventDating:
  given Schema[EventDating] = summon[KindlingsSchema[EventDating]].schema
