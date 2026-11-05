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
  EventSummary
}

/** What an event's page shows of the catalogue: the event, its evidence and its context. The catalogue's own record
  * ([[EventEntry]]) and the page the server answers (`EventDetail`, which adds the event's pictures) both are one, so
  * the page renders from either.
  */
trait EventCard:
  def event: EventSummary
  def geometry: Option[com.histmersion.api.catalogue.EventCard.GeometryResult]
  def parent: Option[EventSummary]
  def children: List[EventSummary]
  def datings: List[EventDating]
  def edges: List[EventEdge]
  def participants: List[EventParticipant]
  def wikidata: Option[com.histmersion.api.catalogue.EventCard.WikidataResult]
  def tier: com.histmersion.api.catalogue.EventCard.TierResult
  def names: List[com.histmersion.api.catalogue.EventCard.NamesResult]
  def catalogue: List[EventCatalogueRef]
  def sources: List[EventSourceRef]
  def findings: List[EventFindingRef]
  def articles: List[EventArticleRef]
  def candidates: List[EventLinkCandidate]
  def leads: List[EventLead]
  def corrections: List[Correction]

object EventCard:
  type GeometryResult = GeometryResult.T
  object GeometryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataResult = WikidataResult.T
  object WikidataResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierResult = TierResult.T
  object TierResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesResult = NamesResult.T
  object NamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
