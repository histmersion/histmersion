package com.histmersion.domain.catalogue.events

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
    placeId: Option[com.histmersion.domain.catalogue.Place.Id],
    parentId: Option[HeldEvent.ParentId],
    description: Option[HeldEvent.Description],
    citekey: Option[com.histmersion.domain.library.Citekey],
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
  def geometryType: Option[com.histmersion.domain.catalogue.events.EventRow.GeometryType] = geometry.flatMap(HeldEvent.typeOf)

  /** Its outer span: the earliest and the latest year it may cover. */
  def outer: Option[(Year, Year)] = timeSpan.outer

  def uncertainty: Option[EventPrecision.Uncertainty] =
    EventPrecision.uncertainty(timeSpan, plusMinus.map(p => EventSummary.PlusMinus(p.value)))

  def summary(attested: Boolean): EventSummary =
    EventSummary(
      com.histmersion.api.catalogue.Event.Id(id.value),
      EventSummary.Label(label.value),
      EventSummary.Kind(kind.value),
      timeSpan,
      EventSummary.Historicity(historicity.value),
      placeId.map(p => com.histmersion.api.catalogue.Place.Id(p.value)),
      parentId.map(p => com.histmersion.api.catalogue.Event.Id(p.value)),
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
  type Id = com.histmersion.domain.catalogue.Event.Id
  val Id: _root_.com.histmersion.domain.catalogue.Event.Id.type = _root_.com.histmersion.domain.catalogue.Event.Id
  type Label = com.histmersion.domain.catalogue.events.EventRow.Label
  val Label: _root_.com.histmersion.domain.catalogue.events.EventRow.Label.type = _root_.com.histmersion.domain.catalogue.events.EventRow.Label
  type Kind = com.histmersion.domain.catalogue.events.EventRow.Kind
  val Kind: _root_.com.histmersion.domain.catalogue.events.EventRow.Kind.type = _root_.com.histmersion.domain.catalogue.events.EventRow.Kind
  type Historicity = com.histmersion.domain.catalogue.Historicity.Wire
  val Historicity: _root_.com.histmersion.domain.catalogue.Historicity.Wire.type = _root_.com.histmersion.domain.catalogue.Historicity.Wire
  type Geometry = com.histmersion.domain.catalogue.events.EventDetailRow.Geometry
  val Geometry: _root_.com.histmersion.domain.catalogue.events.EventDetailRow.Geometry.type = _root_.com.histmersion.domain.catalogue.events.EventDetailRow.Geometry
  type ParentId = com.histmersion.domain.catalogue.events.EventRow.ParentId
  val ParentId: _root_.com.histmersion.domain.catalogue.events.EventRow.ParentId.type = _root_.com.histmersion.domain.catalogue.events.EventRow.ParentId
  type Description = com.histmersion.domain.catalogue.events.BEventRow.Description
  val Description: _root_.com.histmersion.domain.catalogue.events.BEventRow.Description.type = _root_.com.histmersion.domain.catalogue.events.BEventRow.Description
  type Scale = com.histmersion.domain.catalogue.events.EventRow.Scale
  val Scale: _root_.com.histmersion.domain.catalogue.events.EventRow.Scale.type = _root_.com.histmersion.domain.catalogue.events.EventRow.Scale
  type Wikidata = com.histmersion.domain.catalogue.places.Wikidata.Id
  val Wikidata: _root_.com.histmersion.domain.catalogue.places.Wikidata.Id.type = _root_.com.histmersion.domain.catalogue.places.Wikidata.Id
  type Names = com.histmersion.domain.catalogue.events.BEventRow.Names
  val Names: _root_.com.histmersion.domain.catalogue.events.BEventRow.Names.type = _root_.com.histmersion.domain.catalogue.events.BEventRow.Names
  type Catalogue = com.histmersion.domain.catalogue.events.BEventRow.Catalogue
  val Catalogue: _root_.com.histmersion.domain.catalogue.events.BEventRow.Catalogue.type = _root_.com.histmersion.domain.catalogue.events.BEventRow.Catalogue
  type Central = com.histmersion.domain.catalogue.events.BEventRow.Central
  val Central: _root_.com.histmersion.domain.catalogue.events.BEventRow.Central.type = _root_.com.histmersion.domain.catalogue.events.BEventRow.Central
  type PlusMinus = com.histmersion.domain.catalogue.events.EventRow.PlusMinus
  val PlusMinus: _root_.com.histmersion.domain.catalogue.events.EventRow.PlusMinus.type = _root_.com.histmersion.domain.catalogue.events.EventRow.PlusMinus
  type DatingMethod = com.histmersion.domain.catalogue.events.BEventRow.DatingMethod
  val DatingMethod: _root_.com.histmersion.domain.catalogue.events.BEventRow.DatingMethod.type = _root_.com.histmersion.domain.catalogue.events.BEventRow.DatingMethod

  /** The `type` of a GeoJSON geometry, upper case. */
  def typeOf(geojson: com.histmersion.domain.catalogue.events.EventDetailRow.Geometry): Option[com.histmersion.domain.catalogue.events.EventRow.GeometryType] =
    io.circe.parser
      .parse(geojson.value)
      .toOption
      .flatMap(_.hcursor.get[String]("type").toOption)
      .map(t => com.histmersion.domain.catalogue.events.EventRow.GeometryType(t.toUpperCase))

  /** The point of a GeoJSON `Point`: (lat, lon). */
  def pointOf(geojson: com.histmersion.domain.catalogue.events.EventDetailRow.Geometry): Option[(Latitude, Longitude)] =
    io.circe.parser.parse(geojson.value).toOption.flatMap { j =>
      val c = j.hcursor
      if !c.get[String]("type").toOption.contains("Point") then None
      else
        c.get[List[Double]]("coordinates").toOption.collect { case lon :: lat :: _ => (lat, lon) }.flatMap((lat, lon) =>
          for
            la <- Latitude.option(lat)
            lo <- Longitude.option(lon)
          yield (la, lo)
        )
    }
