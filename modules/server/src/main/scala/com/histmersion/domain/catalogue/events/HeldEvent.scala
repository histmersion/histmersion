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
  def geometryType: Option[com.histmersion.domain.catalogue.events.HeldEvent.GeometryTypeResult] = geometry.flatMap(HeldEvent.typeOf)

  /** Its outer span: the earliest and the latest year it may cover. */
  def outer: Option[(com.histmersion.domain.catalogue.events.HeldEvent.OuterResultPart1, com.histmersion.domain.catalogue.events.HeldEvent.OuterResultPart2)] = timeSpan.outer

  def uncertainty: Option[com.histmersion.domain.catalogue.events.HeldEvent.UncertaintyResult] = EventPrecision.uncertainty(timeSpan, plusMinus)

  def summary(attested: com.histmersion.domain.catalogue.events.HeldEvent.SummaryAttested): EventSummary =
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
  type GeometryTypeResult = GeometryTypeResult.T
  object GeometryTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OuterResultPart1 = OuterResultPart1.T
  object OuterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OuterResultPart2 = OuterResultPart2.T
  object OuterResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncertaintyResult = UncertaintyResult.T
  object UncertaintyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryAttested = SummaryAttested.T
  object SummaryAttested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeOfResult = TypeOfResult.T
  object TypeOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeOfGeojson = TypeOfGeojson.T
  object TypeOfGeojson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointOfResultPart1 = PointOfResultPart1.T
  object PointOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointOfResultPart2 = PointOfResultPart2.T
  object PointOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointOfGeojson = PointOfGeojson.T
  object PointOfGeojson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  def typeOf(geojson: com.histmersion.domain.catalogue.events.HeldEvent.TypeOfGeojson): Option[com.histmersion.domain.catalogue.events.HeldEvent.TypeOfResult] =
    io.circe.parser.parse(geojson).toOption.flatMap(_.hcursor.get[String]("type").toOption).map(_.toUpperCase)

  /** The point of a GeoJSON `Point`: (lat, lon). */
  def pointOf(geojson: com.histmersion.domain.catalogue.events.HeldEvent.PointOfGeojson): Option[(com.histmersion.domain.catalogue.events.HeldEvent.PointOfResultPart1, com.histmersion.domain.catalogue.events.HeldEvent.PointOfResultPart2)] =
    io.circe.parser.parse(geojson).toOption.flatMap { j =>
      val c = j.hcursor
      if !c.get[String]("type").toOption.contains("Point") then None
      else c.get[List[Double]]("coordinates").toOption.collect { case lon :: lat :: _ => (lat, lon) }
    }
