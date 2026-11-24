package com.histmersion
package api.catalogue
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
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation

/** The catalogue's record of one event: the row with its geometry, the hierarchy, the evidence and the context its card
  * shows (the articles citing its sources, filled by whoever shows the card). Not on the wire: the server answers the
  * page (`EventDetail`), which adds the event's pictures.
  */
final case class EventEntry(
    event: EventSummary,
    geometry: Option[EventGeometry.Geojson] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    wikidata: Option[EventEntry.Wikidata] = None,
    tier: EventGeo.Tier = EventGeo.Tier(EventTier.Attested),
    names: List[EventEntry.Names] = Nil,
    catalogue: List[EventCatalogueRef] = Nil,
    sources: List[EventSourceRef] = Nil,
    findings: List[EventFindingRef] = Nil,
    articles: List[EventArticleRef] = Nil,
    candidates: List[EventLinkCandidate] = Nil,
    leads: List[EventLead] = Nil,
    corrections: List[Correction] = Nil,
    creator: Option[Creation] = None,
    createdAt: Option[Creation.At] = None
) extends EventCard

object EventEntry:
  type Wikidata = Wikidata.T
  object Wikidata extends Newtype[String, Pure]
  type Names = Names.T
  object Names extends Newtype[String, Pure]
