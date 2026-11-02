package com.histmersion.api.catalogue
import io.circe.Json
import io.circe.syntax.*
import com.histmersion.domain.catalogue.Historicity
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

  def point(lat: Double, lon: Double): Json =
    Json.obj("type" -> "Point".asJson, "coordinates" -> Json.arr(lon.asJson, lat.asJson))

  def geometry(g: Option[String]): Json =
    g.map(_.trim).filter(_.nonEmpty).fold(Json.Null)(t => io.circe.parser.parse(t).getOrElse(t.asJson))

  def upstream(r: CatalogRecord): Map[String, Json] =
    Map(
      Label -> r.label.asJson,
      Kind -> EventTaxonomy.canonical(r.kind).asJson,
      Span -> r.timeSpan.asJson,
      Historicity -> r.historicity.asJson,
      Description -> r.description.asJson,
      Geometry -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  def ofPatch(p: EventPatch): Map[String, Json] =
    List(
      p.label.map(Label -> _.asJson),
      p.kind.map(k => Kind -> EventTaxonomy.canonical(k).asJson),
      p.timeSpan.map(Span -> _.asJson),
      p.historicity.map(Historicity -> _.asJson),
      p.description.map(d => Description -> Option(d).filter(_.nonEmpty).asJson),
      p.geometry.map(g => Geometry -> geometry(Some(g)))
    ).flatten.toMap

  def ofPut(p: EventPut): Map[String, Json] =
    Map(
      Label -> p.label.asJson,
      Kind -> EventTaxonomy.canonical(p.kind).asJson,
      Span -> p.timeSpan.asJson,
      Historicity -> p.historicity.asJson,
      Description -> p.description.filter(_.nonEmpty).asJson,
      Geometry -> geometry(p.geometry)
    )
