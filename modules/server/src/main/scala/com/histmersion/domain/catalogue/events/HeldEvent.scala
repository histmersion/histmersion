package com.histmersion
package domain.catalogue.events
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{
  EventDating,
  EventEdge,
  EventGeo,
  EventParticipant,
  EventPrecision,
  EventScale,
  EventSummary,
  EventTier,
  Year
}
import com.histmersion.{Latitude, Longitude}
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Event

/** An event as the in-memory catalogue keeps it: the columns of its `events` row, with its datings, edges and
  * participants. `geometry` is its own GeoJSON geometry; `attested` whether an attestation names it (its tier).
  */
final case class HeldEvent(
    id: HeldEvent.Id,
    label: HeldEvent.Label,
    kind: HeldEvent.Kind,
    timeSpan: TimeSpan,
    historicity: HeldEvent.Historicity,
    geometry: Option[HeldEvent.Geometry],
    placeId: Option[Place.Id],
    parentId: Option[HeldEvent.ParentId],
    description: Option[HeldEvent.Description],
    citekey: Option[Citekey],
    scale: Option[HeldEvent.Scale],
    wikidata: Option[HeldEvent.Wikidata] = None,
    names: List[HeldEvent.Names] = Nil,
    catalogue: Option[HeldEvent.Catalogue] = None,
    central: Option[HeldEvent.Central] = None,
    plusMinus: Option[HeldEvent.PlusMinus] = None,
    datingMethod: Option[HeldEvent.DatingMethod] = None,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil
):
  /** The GeoJSON type of its own geometry, as PostGIS names it (`POINT`, `POLYGON`, …). */
  def geometryType: Option[EventRow.GeometryType] = geometry.flatMap(HeldEvent.typeOf)

  /** Its outer span: the earliest and the latest year it may cover. */
  def outer: Option[(Year, Year)] = timeSpan.outer

  def uncertainty: Option[EventPrecision.Uncertainty] =
    EventPrecision.uncertainty(timeSpan, plusMinus.map(p => EventSummary.PlusMinus(p.value)))

  def summary(attested: Boolean): EventSummary =
    EventSummary(
      Event.Id(id.value),
      EventSummary.Label(label.value),
      EventSummary.Kind(kind.value),
      timeSpan,
      EventSummary.Historicity(historicity.value),
      placeId.map(p => com.histmersion.api.catalogue.Place.Id(p.value)),
      parentId.map(p => Event.Id(p.value)),
      description.map(d => EventSummary.Description(d.value)),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
      EventScale.effective(
        scale.map(s => EventSummary.Scale(s.value)),
        EventScale.EffectiveKind(kind.value),
        parentId.isDefined,
        None,
        geometryType.map(g => EventScale.GeometryType(g.value))
      ),
      EventSummary.ScaleStated(scale.isDefined),
      EventSummary.Tier(if citekey.isDefined || attested then EventTier.Attested else EventTier.Catalogue),
      EventPrecision.of(uncertainty),
      central.map(c => EventSummary.Central(c.value)),
      plusMinus.map(p => EventSummary.PlusMinus(p.value)),
      datingMethod.map(m => EventSummary.DatingMethod(m.value))
    )

object HeldEvent:
  export domain.catalogue.Event.Id
  export EventRow.{Kind, Label}
  export domain.catalogue.Historicity.Wire as Historicity
  export EventDetailRow.Geometry
  export EventRow.ParentId
  export BEventRow.Description
  export EventRow.Scale
  export domain.catalogue.places.Wikidata.Id as Wikidata
  export BEventRow.{Catalogue, Central, Names}
  export EventRow.PlusMinus
  export BEventRow.DatingMethod

  /** The `type` of a GeoJSON geometry, upper case. */
  def typeOf(geojson: EventDetailRow.Geometry): Option[EventRow.GeometryType] =
    RawJson
      .parse(geojson.value)
      .toOption
      .flatMap(_.cursor.get[String]("type").toOption)
      .map(t => EventRow.GeometryType(t.toUpperCase))

  /** The point of a GeoJSON `Point`: (lat, lon). */
  def pointOf(geojson: EventDetailRow.Geometry): Option[(Latitude, Longitude)] =
    RawJson.parse(geojson.value).toOption.flatMap { j =>
      val c = j.cursor
      if !c.get[String]("type").toOption.contains("Point") then None
      else
        c.get[List[Double]]("coordinates")
          .toOption
          .collect { case lon :: lat :: _ => (lat, lon) }
          .flatMap((lat, lon) =>
            for
              la <- Latitude.option(lat)
              lo <- Longitude.option(lon)
            yield (la, lo)
          )
    }
