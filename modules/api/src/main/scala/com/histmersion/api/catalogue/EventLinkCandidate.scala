package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.domain.catalogue.Event

final case class EventLinkCandidate(
    eventId: String,
    citekey: String,
    score: Double,
    status: String = Event.LinkStatus.Proposed,
    title: Option[String] = None,
    findings: List[String] = Nil,
    passages: List[EventPassage] = Nil,
    reasons: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLinkCandidate:
  given Schema[EventLinkCandidate] = summon[KindlingsSchema[EventLinkCandidate]].schema
