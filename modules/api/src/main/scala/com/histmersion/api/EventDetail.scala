package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.events.EventCard

/** An event's page as the server answers it: the catalogue's [[EventCard]] with the event's pictures. */
final case class EventDetail(
    event: EventSummary,
    geometry: Option[String] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    wikidata: Option[String] = None,
    media: List[MediaThumb] = Nil,
    tier: String = EventTier.Attested,
    names: List[String] = Nil,
    catalogue: List[EventCatalogueRef] = Nil,
    sources: List[EventSourceRef] = Nil,
    findings: List[EventFindingRef] = Nil,
    articles: List[EventArticleRef] = Nil,
    candidates: List[EventLinkCandidate] = Nil,
    leads: List[EventLead] = Nil,
    corrections: List[Correction] = Nil
) extends EventCard derives KindlingsCodecAsObject, KindlingsSchema:
  /** The page in Markdown, with its pictures. */
  def markdown: String = com.histmersion.api.catalogue.events.EventsMarkdown.event(this, media.map(_.markdownLine))
object EventDetail:
  given Schema[EventDetail] = summon[KindlingsSchema[EventDetail]].schema
