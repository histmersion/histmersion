package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/events/candidates` (a job): the candidates of every event — or the catalogue ones only, or those not
  * computed yet — so the cards and `events leads` read stored answers.
  */
final case class EventCandidatesRequest(all: Boolean = false, event: Option[String] = None, limit: Option[Int] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventCandidatesRequest:
  given Schema[EventCandidatesRequest] = summon[KindlingsSchema[EventCandidatesRequest]].schema
