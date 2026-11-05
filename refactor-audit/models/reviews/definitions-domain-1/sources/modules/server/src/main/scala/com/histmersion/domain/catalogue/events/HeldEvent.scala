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
  EventTier
}

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
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Geometry = Geometry.T
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentId = ParentId.T
  object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Scale = Scale.T
  object Scale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Catalogue = Catalogue.T
  object Catalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Central = Central.T
  object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def typeOf(geojson: String): Option[String] =
    io.circe.parser.parse(geojson).toOption.flatMap(_.hcursor.get[String]("type").toOption).map(_.toUpperCase)

  /** The point of a GeoJSON `Point`: (lat, lon). */
  def pointOf(geojson: String): Option[(Double, Double)] =
    io.circe.parser.parse(geojson).toOption.flatMap { j =>
      val c = j.hcursor
      if !c.get[String]("type").toOption.contains("Point") then None
      else c.get[List[Double]]("coordinates").toOption.collect { case lon :: lat :: _ => (lat, lon) }
    }
