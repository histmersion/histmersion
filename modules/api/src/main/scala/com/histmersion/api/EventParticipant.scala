package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An actor who participated in an event. */
final case class EventParticipant(
    eventId: String,
    actor: String,
    role: String = "participant",
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventParticipant:
  given Schema[EventParticipant] = summon[KindlingsSchema[EventParticipant]].schema
