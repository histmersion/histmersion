package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.EventCard
import com.histmersion.api.catalogue.{
  Correction,
  EventArticleRef,
  EventCatalogueRef,
  EventDating,
  EventEdge,
  EventFindingRef,
  EventLead,
  EventLinkCandidate,
  EventParticipant,
  EventSourceRef,
  EventSummary,
  EventTier
}
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** An event's page as the server answers it: the catalogue's [[EventCard]] with the event's pictures. */
final case class EventDetail(
    event: EventSummary,
    geometry: Option[com.histmersion.api.catalogue.EventGeometry.Geojson] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    wikidata: Option[com.histmersion.api.catalogue.EventEntry.Wikidata] = None,
    media: List[MediaThumb] = Nil,
    tier: com.histmersion.api.catalogue.EventGeo.Tier = _root_.com.histmersion.api.catalogue.EventGeo.Tier(EventTier.Attested),
    names: List[com.histmersion.api.catalogue.EventEntry.Names] = Nil,
    catalogue: List[EventCatalogueRef] = Nil,
    sources: List[EventSourceRef] = Nil,
    findings: List[EventFindingRef] = Nil,
    articles: List[EventArticleRef] = Nil,
    candidates: List[EventLinkCandidate] = Nil,
    leads: List[EventLead] = Nil,
    corrections: List[Correction] = Nil
) extends EventCard derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema:
  /** The page in Markdown, with its pictures. */
  def markdown: com.histmersion.api.views.EventDetail.MarkdownResult = _root_.com.histmersion.api.views.EventDetail.MarkdownResult(com.histmersion.api.catalogue.EventsMarkdown.event(this, media.map(_.markdownLine.value)))

object EventDetail:
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

