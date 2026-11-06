package com.histmersion.api.catalogue
import io.circe.Json
import io.circe.syntax.*
import com.histmersion.api.Span
import com.histmersion.api.library.Label
import com.histmersion.api.given

object EventFields:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Label = "label"
  val Kind = "kind"
  val Span = "timeSpan"
  val Historicity = "historicity"
  val Description = "description"
  val Geometry = "geometry"
  val all: List[com.histmersion.api.catalogue.EventFields.Wire] = List(_root_.com.histmersion.api.catalogue.EventFields.Wire(Label), _root_.com.histmersion.api.catalogue.EventFields.Wire(Kind), _root_.com.histmersion.api.catalogue.EventFields.Wire(Span), _root_.com.histmersion.api.catalogue.EventFields.Wire(Historicity), _root_.com.histmersion.api.catalogue.EventFields.Wire(Description), _root_.com.histmersion.api.catalogue.EventFields.Wire(Geometry))

  def point(lat: com.histmersion.api.catalogue.EventGeometry.Latitude, lon: com.histmersion.api.catalogue.EventGeometry.Longitude): Json =
    Json.obj("type" -> "Point".asJson, "coordinates" -> Json.arr(lon.asJson, lat.asJson))

  def geometry(g: Option[com.histmersion.api.catalogue.EventGeometry.Geojson]): Json =
    g.map((_).value.trim).filter(_.nonEmpty).fold(Json.Null)(t => io.circe.parser.parse(t).getOrElse(t.asJson))

  def upstream(r: CatalogRecord): Map[com.histmersion.api.catalogue.EventFields.Wire, Json] =
    Map(
      Label -> r.label.asJson,
      Kind -> EventTaxonomy.canonical(r.kind).asJson,
      { val refactorTuple3 = Span -> r.timeSpan.asJson; (_root_.com.histmersion.api.catalogue.EventFields.Wire(refactorTuple3._1), refactorTuple3._2) },
      Historicity -> r.historicity.asJson,
      Description -> r.description.asJson,
      Geometry -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  def ofPatch(p: EventPatch): Map[com.histmersion.api.catalogue.EventFields.Wire, Json] =
    List(
      p.label.map(Label -> _.asJson),
      p.kind.map(k => Kind -> EventTaxonomy.canonical(k).asJson),
      p.timeSpan.map(Span -> _.asJson),
      p.historicity.map(Historicity -> _.asJson),
      p.description.map(d => Description -> Option(d).filter(_.nonEmpty).asJson),
      p.geometry.map(g => Geometry -> geometry(Some(g)))
    ).flatten.toMap

  def ofPut(p: EventPut): Map[com.histmersion.api.catalogue.EventFields.Wire, Json] =
    Map(
      Label -> p.label.asJson,
      Kind -> EventTaxonomy.canonical(p.kind).asJson,
      { val refactorTuple4 = Span -> p.timeSpan.asJson; (_root_.com.histmersion.api.catalogue.EventFields.Wire(refactorTuple4._1), refactorTuple4._2) },
      Historicity -> p.historicity.asJson,
      Description -> p.description.filter(_.nonEmpty).asJson,
      Geometry -> geometry(p.geometry)
    )
