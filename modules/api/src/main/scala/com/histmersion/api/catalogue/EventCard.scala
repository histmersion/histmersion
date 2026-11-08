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
  EventSummary
}

/** What an event's page shows of the catalogue: the event, its evidence and its context. The catalogue's own record
  * ([[EventEntry]]) and the page the server answers (`EventDetail`, which adds the event's pictures) both are one, so
  * the page renders from either.
  */
trait EventCard:
  def event: EventSummary
  def geometry: Option[EventGeometry.Geojson]
  def parent: Option[EventSummary]
  def children: List[EventSummary]
  def datings: List[EventDating]
  def edges: List[EventEdge]
  def participants: List[EventParticipant]
  def wikidata: Option[EventEntry.Wikidata]
  def tier: EventGeo.Tier
  def names: List[EventEntry.Names]
  def catalogue: List[EventCatalogueRef]
  def sources: List[EventSourceRef]
  def findings: List[EventFindingRef]
  def articles: List[EventArticleRef]
  def candidates: List[EventLinkCandidate]
  def leads: List[EventLead]
  def corrections: List[Correction]
