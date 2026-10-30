package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventCandidatesRequest(all: Boolean = false, event: Option[String] = None, limit: Option[Int] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventCandidatesRequest:
  given Schema[EventCandidatesRequest] = summon[KindlingsSchema[EventCandidatesRequest]].schema
