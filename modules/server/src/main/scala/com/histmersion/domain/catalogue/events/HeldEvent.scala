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
import com.histmersion.lint.{tolerated, LintRule}

/** An event as the in-memory catalogue keeps it: the columns of its `events` row, with its datings, edges and
  * participants. `geometry` is its own GeoJSON geometry; `attested` whether an attestation names it (its tier).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def uncertainty: Option[EventPrecision.Uncertainty] =
    EventPrecision.uncertainty(timeSpan, plusMinus.map(p => EventSummary.PlusMinus(p.value)))

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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
      (if scale.isDefined then EventSummary.ScaleStated.Stated else EventSummary.ScaleStated.Inferred),
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def typeOf(geojson: EventDetailRow.Geometry): Option[EventRow.GeometryType] =
    RawJson
      .parse(geojson.value)
      .toOption
      .flatMap(_.cursor.get[String]("type").toOption)
      .map(t => EventRow.GeometryType(t.toUpperCase))

  /** The point of a GeoJSON `Point`: (lat, lon). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
