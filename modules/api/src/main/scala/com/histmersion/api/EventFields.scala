package com.histmersion.api

import io.circe.Json
import io.circe.syntax.*
import com.histmersion.values.Historicity

/** The FIELDS of an event a correction can hold, and their JSON values — the ones an import of the catalogue that
  * created the event would otherwise rewrite. Pure.
  */
object EventFields:
  val Label = "label"
  val Kind = "kind"
  val Span = "timeSpan"
  val Historicity = "historicity"
  val Description = "description"
  val Geometry = "geometry"
  val all: List[String] = List(Label, Kind, Span, Historicity, Description, Geometry)

  /** Pure: a point as the GeoJSON an event's geometry is compared as. */
  def point(lat: Double, lon: Double): Json =
    Json.obj("type" -> "Point".asJson, "coordinates" -> Json.arr(lon.asJson, lat.asJson))

  /** Pure: GeoJSON text as the JSON compared (unparseable text compares as itself; none as null). */
  def geometry(g: Option[String]): Json =
    g.map(_.trim).filter(_.nonEmpty).fold(Json.Null)(t => io.circe.parser.parse(t).getOrElse(t.asJson))

  /** Pure: what an import of `r` writes to each field. */
  def upstream(r: CatalogRecord): Map[String, Json] =
    Map(
      Label -> r.label.asJson,
      Kind -> EventTaxonomy.canonical(r.kind).asJson,
      Span -> r.timeSpan.asJson,
      Historicity -> r.historicity.asJson,
      Description -> r.description.asJson,
      Geometry -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  /** Pure: the fields a patch sets, as compared. */
  def ofPatch(p: EventPatch): Map[String, Json] =
    List(
      p.label.map(Label -> _.asJson),
      p.kind.map(k => Kind -> EventTaxonomy.canonical(k).asJson),
      p.timeSpan.map(Span -> _.asJson),
      p.historicity.map(Historicity -> _.asJson),
      p.description.map(d => Description -> Option(d).filter(_.nonEmpty).asJson),
      p.geometry.map(g => Geometry -> geometry(Some(g)))
    ).flatten.toMap

  /** Pure: the fields a whole put sets. */
  def ofPut(p: EventPut): Map[String, Json] =
    Map(
      Label -> p.label.asJson,
      Kind -> EventTaxonomy.canonical(p.kind).asJson,
      Span -> p.timeSpan.asJson,
      Historicity -> p.historicity.asJson,
      Description -> p.description.filter(_.nonEmpty).asJson,
      Geometry -> geometry(p.geometry)
    )
