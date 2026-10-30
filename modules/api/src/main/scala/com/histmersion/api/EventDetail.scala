package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Full detail view of one event (GET /api/events/{id}). */
final case class EventDetail(
    event: EventSummary,
    geometry: Option[String] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    /** The Wikidata item the event is (`Q…`), when it is known — where its picture comes from. */
    wikidata: Option[String] = None,
    /** Pictures of the event from outside providers, the card's first (#1446). */
    media: List[MediaThumb] = Nil,
    /** `attested` (a source in the corpus attests it) or `catalogue` (a catalogue alone knows it) — [[EventTier]],
      * #1449.
      */
    tier: String = EventTier.Attested,
    /** Its other names (a catalogue's labels and aliases), searched for in the sources' text. */
    names: List[String] = Nil,
    /** The catalogues that list it, with their licence and citation. */
    catalogue: List[EventCatalogueRef] = Nil,
    /** The sources that attest it: its own `citekey` and the attestations naming it. */
    sources: List[EventSourceRef] = Nil,
    /** The corpus's findings near it in time and space (`events.links` distances), nearest first. */
    findings: List[EventFindingRef] = Nil,
    /** The articles that cite its attesting sources. */
    articles: List[EventArticleRef] = Nil,
    /** Sources that POSSIBLY discuss it — proposed, unconfirmed (the domain's `EventMatcher`). */
    candidates: List[EventLinkCandidate] = Nil,
    /** The questions it raises ([[EventLeads]]). */
    leads: List[EventLead] = Nil,
    /** Its fields a person corrected by hand, kept over the catalogue's on a re-import ([[Corrections]]). */
    corrections: List[Correction] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventDetail:
  given Schema[EventDetail] = summon[KindlingsSchema[EventDetail]].schema
