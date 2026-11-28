package com.histmersion
package api.catalogue

import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventPatch(
    label: Option[EventSummary.Label] = None,
    kind: Option[EventSummary.Kind] = None,
    timeSpan: Option[TimeSpan] = None,
    historicity: Option[EventSummary.Historicity] = None,
    geometry: Option[EventPatch.Geometry] = None,
    placeId: Option[Place.Id] = None,
    parentId: Option[Event.Id] = None,
    description: Option[EventSummary.Description] = None,
    citekey: Option[Citekey] = None,
    datings: Option[List[EventDating]] = None,
    edges: Option[List[EventEdge]] = None,
    participants: Option[List[EventParticipant]] = None,
    scale: Option[EventSummary.Scale] = None,
    wikidata: Option[EventPatch.Wikidata] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema, CanEqual
object EventPatch:

  type Geometry = Geometry.T
  object Geometry extends Newtype[String, Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends Newtype[String, Pure]

  val Label = "label"

  val Kind = "kind"

  val Span = "timeSpan"

  val Historicity = "historicity"

  val Description = "description"

  val GeometryKey = "geometry"

  val all: List[String] = List(Label, Kind, Span, Historicity, Description, GeometryKey)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def point(lat: Latitude, lon: Longitude): Json =
    Json.obj(
      "type" -> "Point".asJson,
      "coordinates" -> Json.arr((lon.value: Double).asJson, (lat.value: Double).asJson)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def geometryJson(g: Option[EventGeometry.Geojson]): Json =
    g.map((_).value.trim).filter(_.nonEmpty).fold(Json.Null)(t => RawJson.parse(t).getOrElse(t.asJson))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def upstream(r: CatalogRecord): Map[String, Json] =
    Map(
      Label -> (r.label.value: String).asJson,
      Kind -> (EventTaxonomy.canonical(r.kind.summary).value: String).asJson,
      Span -> r.timeSpan.asJson,
      Historicity -> (r.historicity.value: String).asJson,
      Description -> r.description.map(d => d.value: String).asJson,
      GeometryKey -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  extension (p: EventPatch)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def ofPatch: Map[String, Json] =
      List(
        p.label.map(l => Label -> (l.value: String).asJson),
        p.kind.map(k => Kind -> (EventTaxonomy.canonical(k).value: String).asJson),
        p.timeSpan.map(Span -> _.asJson),
        p.historicity.map(h => Historicity -> (h.value: String).asJson),
        p.description.map(d => Description -> Option(d.value: String).filter(_.nonEmpty).asJson),
        p.geometry.map(g => GeometryKey -> geometryJson(Some(EventGeometry.Geojson(g.value))))
      ).flatten.toMap

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ofPut(p: EventPut): Map[String, Json] =
    Map(
      Label -> (p.label.value: String).asJson,
      Kind -> (EventTaxonomy.canonical(p.kind).value: String).asJson,
      Span -> p.timeSpan.asJson,
      Historicity -> (p.historicity.value: String).asJson,
      Description -> p.description.map(d => d.value: String).filter(_.nonEmpty).asJson,
      GeometryKey -> geometryJson(p.geometry.map(g => EventGeometry.Geojson(g.value)))
    )
