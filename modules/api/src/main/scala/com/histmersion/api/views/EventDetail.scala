package com.histmersion
package api.views
import com.histmersion.util.json.JsonCodec
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
import com.histmersion.api.catalogue.EventGeometry
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.EventsMarkdown
import io.github.iltotore.iron.constraint.any.Pure

/** An event's page as the server answers it: the catalogue's [[EventCard]] with the event's pictures. */
final case class EventDetail(
    event: EventSummary,
    geometry: Option[EventGeometry.Geojson] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    wikidata: Option[EventEntry.Wikidata] = None,
    media: List[MediaThumb] = Nil,
    tier: EventGeo.Tier = EventGeo.Tier(EventTier.Attested),
    names: List[EventEntry.Names] = Nil,
    catalogue: List[EventCatalogueRef] = Nil,
    sources: List[EventSourceRef] = Nil,
    findings: List[EventFindingRef] = Nil,
    articles: List[EventArticleRef] = Nil,
    candidates: List[EventLinkCandidate] = Nil,
    leads: List[EventLead] = Nil,
    corrections: List[Correction] = Nil
) extends EventCard derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema:
  /** The page in Markdown, with its pictures. */
  def markdown: EventDetail.Markdown =
    EventDetail.Markdown(EventsMarkdown.event(this, media.map(_.markdownLine.value)))

object EventDetail:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]
