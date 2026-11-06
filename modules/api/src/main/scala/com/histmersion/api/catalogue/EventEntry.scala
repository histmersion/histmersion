package com.histmersion.api.catalogue
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

/** The catalogue's record of one event: the row with its geometry, the hierarchy, the evidence and the context its card
  * shows (the articles citing its sources, filled by whoever shows the card). Not on the wire: the server answers the
  * page (`EventDetail`), which adds the event's pictures.
  */
final case class EventEntry(
    event: EventSummary,
    geometry: Option[com.histmersion.api.catalogue.EventGeometry.Geojson] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    wikidata: Option[EventEntry.Wikidata] = None,
    tier: com.histmersion.api.catalogue.EventGeo.Tier = _root_.com.histmersion.api.catalogue.EventGeo.Tier(EventTier.Attested),
    names: List[EventEntry.Names] = Nil,
    catalogue: List[EventCatalogueRef] = Nil,
    sources: List[EventSourceRef] = Nil,
    findings: List[EventFindingRef] = Nil,
    articles: List[EventArticleRef] = Nil,
    candidates: List[EventLinkCandidate] = Nil,
    leads: List[EventLead] = Nil,
    corrections: List[Correction] = Nil
) extends EventCard

object EventEntry:
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
