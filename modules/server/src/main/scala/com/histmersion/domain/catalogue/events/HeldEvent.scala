package com.histmersion.domain.catalogue.events

import com.histmersion.api.{
  EventDating,
  EventEdge,
  EventGeo,
  EventParticipant,
  EventPrecision,
  EventScale,
  EventSummary,
  EventTier,
  TimeSpan
}

/** An event as the in-memory catalogue keeps it: the columns of its `events` row, with its datings, edges and
  * participants. `geometry` is its own GeoJSON geometry; `attested` whether an attestation names it (its tier).
  */
final case class HeldEvent(
    id: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    historicity: String,
    geometry: Option[String],
    placeId: Option[String],
    parentId: Option[String],
    description: Option[String],
    citekey: Option[String],
    scale: Option[String],
    wikidata: Option[String] = None,
    names: List[String] = Nil,
    catalogue: Option[String] = None,
    central: Option[Long] = None,
    plusMinus: Option[Long] = None,
    datingMethod: Option[String] = None,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil
):
  /** The GeoJSON type of its own geometry, as PostGIS names it (`POINT`, `POLYGON`, …). */
  def geometryType: Option[String] = geometry.flatMap(HeldEvent.typeOf)

  /** Its outer span: the earliest and the latest year it may cover. */
  def outer: Option[(Long, Long)] = timeSpan.outer

  def uncertainty: Option[Long] = EventPrecision.uncertainty(timeSpan, plusMinus)

  def summary(attested: Boolean): EventSummary =
    EventSummary(
      id,
      label,
      kind,
      timeSpan,
      historicity,
      placeId,
      parentId,
      description,
      citekey,
      EventScale.effective(scale, kind, parentId.isDefined, None, geometryType),
      scale.isDefined,
      if citekey.isDefined || attested then EventTier.Attested else EventTier.Catalogue,
      EventPrecision.of(uncertainty),
      central,
      plusMinus,
      datingMethod
    )

object HeldEvent:
  /** The `type` of a GeoJSON geometry, upper case. */
  def typeOf(geojson: String): Option[String] =
    io.circe.parser.parse(geojson).toOption.flatMap(_.hcursor.get[String]("type").toOption).map(_.toUpperCase)

  /** The point of a GeoJSON `Point`: (lat, lon). */
  def pointOf(geojson: String): Option[(Double, Double)] =
    io.circe.parser.parse(geojson).toOption.flatMap { j =>
      val c = j.hcursor
      if !c.get[String]("type").toOption.contains("Point") then None
      else c.get[List[Double]]("coordinates").toOption.collect { case lon :: lat :: _ => (lat, lon) }
    }
