package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.EventLinkStatus

/** One source that may discuss an event: its score, the findings near the event, the passages naming it, and the
  * reasons in words. `status`: proposed | confirmed (an attestation exists) | rejected.
  */
final case class EventLinkCandidate(
    eventId: String,
    citekey: String,
    score: Double,
    status: String = EventLinkStatus.Proposed,
    title: Option[String] = None,
    findings: List[String] = Nil,
    passages: List[EventPassage] = Nil,
    reasons: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLinkCandidate:
  given Schema[EventLinkCandidate] = summon[KindlingsSchema[EventLinkCandidate]].schema
