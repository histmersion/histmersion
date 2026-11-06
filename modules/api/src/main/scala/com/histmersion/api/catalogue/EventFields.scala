package com.histmersion.api.catalogue
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

  def point(lat: com.histmersion.api.catalogue.Latitude, lon: com.histmersion.api.catalogue.Longitude): Json =
    Json.obj("type" -> "Point".asJson, "coordinates" -> Json.arr(lon.value.asJson, lat.value.asJson))

  def geometry(g: Option[com.histmersion.api.catalogue.EventGeometry.Geojson]): Json =
    g.map((_).value.trim).filter(_.nonEmpty).fold(Json.Null)(t => io.circe.parser.parse(t).getOrElse(t.asJson))

  def upstream(r: CatalogRecord): Map[String, Json] =
    Map(
      Label -> r.label.value.asJson,
      Kind -> EventTaxonomy.canonical(EventSummary.Kind(r.kind.value)).value.asJson,
      Span -> r.timeSpan.asJson,
      Historicity -> r.historicity.value.asJson,
      Description -> r.description.map(_.value).asJson,
      Geometry -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  def ofPatch(p: EventPatch): Map[String, Json] =
    List(
      p.label.map(l => Label -> l.value.asJson),
      p.kind.map(k => Kind -> EventTaxonomy.canonical(k).value.asJson),
      p.timeSpan.map(Span -> _.asJson),
      p.historicity.map(h => Historicity -> h.value.asJson),
      p.description.map(d => Description -> Option(d.value).filter(_.nonEmpty).asJson),
      p.geometry.map(g => Geometry -> geometry(Some(EventGeometry.Geojson(g.value))))
    ).flatten.toMap

  def ofPut(p: EventPut): Map[String, Json] =
    Map(
      Label -> p.label.value.asJson,
      Kind -> EventTaxonomy.canonical(p.kind).value.asJson,
      Span -> p.timeSpan.asJson,
      Historicity -> p.historicity.value.asJson,
      Description -> p.description.map(_.value).filter(_.nonEmpty).asJson,
      Geometry -> geometry(p.geometry.map(g => EventGeometry.Geojson(g.value)))
    )
