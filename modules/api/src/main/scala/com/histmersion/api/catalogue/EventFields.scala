package com.histmersion
package api.catalogue
import io.circe.Json
import io.circe.syntax.*
import com.histmersion.api.Span
import com.histmersion.api.library.Label
import com.histmersion.api.given

object EventFields:
  val Label = "label"
  val Kind = "kind"
  val Span = "timeSpan"
  val Historicity = "historicity"
  val Description = "description"
  val Geometry = "geometry"
  val all: List[String] = List(Label, Kind, Span, Historicity, Description, Geometry)

  def point(lat: Latitude, lon: Longitude): Json =
    Json.obj("type" -> "Point".asJson, "coordinates" -> Json.arr((lon.value: Double).asJson, (lat.value: Double).asJson))

  def geometry(g: Option[EventGeometry.Geojson]): Json =
    g.map((_).value.trim).filter(_.nonEmpty).fold(Json.Null)(t => io.circe.parser.parse(t).getOrElse(t.asJson))

  def upstream(r: CatalogRecord): Map[String, Json] =
    Map(
      Label -> (r.label.value: String).asJson,
      Kind -> (EventTaxonomy.canonical(EventSummary.Kind(r.kind.value)).value: String).asJson,
      Span -> r.timeSpan.asJson,
      Historicity -> (r.historicity.value: String).asJson,
      Description -> r.description.map(d => d.value: String).asJson,
      Geometry -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  def ofPatch(p: EventPatch): Map[String, Json] =
    List(
      p.label.map(l => Label -> (l.value: String).asJson),
      p.kind.map(k => Kind -> (EventTaxonomy.canonical(k).value: String).asJson),
      p.timeSpan.map(Span -> _.asJson),
      p.historicity.map(h => Historicity -> (h.value: String).asJson),
      p.description.map(d => Description -> Option(d.value: String).filter(_.nonEmpty).asJson),
      p.geometry.map(g => Geometry -> geometry(Some(EventGeometry.Geojson(g.value))))
    ).flatten.toMap

  def ofPut(p: EventPut): Map[String, Json] =
    Map(
      Label -> (p.label.value: String).asJson,
      Kind -> (EventTaxonomy.canonical(p.kind).value: String).asJson,
      Span -> p.timeSpan.asJson,
      Historicity -> (p.historicity.value: String).asJson,
      Description -> p.description.map(d => d.value: String).filter(_.nonEmpty).asJson,
      Geometry -> geometry(p.geometry.map(g => EventGeometry.Geojson(g.value)))
    )
