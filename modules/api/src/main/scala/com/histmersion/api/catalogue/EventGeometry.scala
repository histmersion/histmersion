package com.histmersion
package api.catalogue
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventGeometry:
  type Geojson = Geojson.T
  object Geojson extends Newtype[String, Pure]
  type Type = Type.T
  object Type extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def point(lat: Latitude, lon: Longitude): Either[Problem, Geojson] =
    if (lat).value.isNaN || (lat).value < -90 || (lat).value > 90 then
      Left(
        Invalid.OutOfRange(
          TypeName.of(Latitude),
          Raw(s"$lat"),
          Invalid.Bounds(Some(Raw("-90")), Some(Raw("90"))),
          Vector(Field("lat"))
        )
      )
    else if (lon).value.isNaN || (lon).value < -180 || (lon).value > 180 then
      Left(
        Invalid.OutOfRange(
          TypeName.of(Longitude),
          Raw(s"$lon"),
          Invalid.Bounds(Some(Raw("-180")), Some(Raw("180"))),
          Vector(Field("lon"))
        )
      )
    else Right(Geojson(s"""{"type":"Point","coordinates":[${fmt(((lon).value))},${fmt(((lat).value))}]}"""))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(geometry: Option[Geojson], lat: Option[Latitude], lon: Option[Longitude]): Either[Problem, Option[Geojson]] =
    (geometry.map((_).value.trim).filter(_.nonEmpty), lat, lon) match
      case (Some(_), Some(_), _) | (Some(_), _, Some(_)) =>
        Left(
          Invalid.Inconsistent.here(NonEmptyVector.of(Field("geometry"), Field("lat/lon")), Invalid.Exclusion.AtMostOne)
        )
      case (g @ Some(_), None, None) => Right((g).map(refactorValue => Geojson(refactorValue)))
      case (None, Some(a), Some(o))  => point(a, o).map(Some(_))
      case (None, None, None)        => Right(None)
      case _                         =>
        Left(Invalid.Inconsistent.here(NonEmptyVector.of(Field("lat"), Field("lon")), Invalid.Exclusion.Together))

  /** The GeoJSON geometry types of RFC 7946 (a Feature is not one). */
  val GeometryTypes: Set[Type] =
    Set(
      Type("Point"),
      Type("MultiPoint"),
      Type("LineString"),
      Type("MultiLineString"),
      Type("Polygon"),
      Type("MultiPolygon"),
      Type("GeometryCollection")
    )

  private val WktShape =
    "(?is)^(SRID=\\d+;\\s*)?(POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION)\\b.*".r

  private val Example = """{"type":"Point","coordinates":[<lon>,<lat>]}"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def check(g: Geojson): Either[Problem, Geojson] =
    val t = (g).value.trim
    if WktShape.matches(t) then
      Left(Invalid.Unreadable(TypeName.of(Geojson), Raw(s"$t"), Vector(Raw(Example)), Vector(Field("geometry"))))
    else
      RawJson.parse(t) match
        case Left(_) =>
          Left(Invalid.Unreadable(TypeName.of(Geojson), Raw(s"$t"), Vector(Raw(Example)), Vector(Field("geometry"))))
        case Right(j) =>
          j.asObject.flatMap(_("type")).flatMap(_.asString) match
            case Some(tp) if GeometryTypes(Type(tp))          => Right(Geojson(t))
            case Some(tp @ ("Feature" | "FeatureCollection")) =>
              Left(Invalid.Wrapped(TypeName.of(Geojson), Raw(s"$tp"), Field("geometry"), Vector(Field("geometry"))))
            case Some(tp) =>
              Left(
                Invalid.Unknown(
                  TypeName.of(Type),
                  Raw(s"$tp"),
                  GeometryTypes.toVector.sorted.map(k => Raw(s"$k")),
                  Vector(Field("geometry"))
                )
              )
            case None =>
              Left(Invalid.Missing(Field("type"), Vector(Field("geometry"))))

  // a plain decimal, never `1.0E-4`, and never with a locale's comma
  private def fmt(d: Double): String = (BigDecimal((d)).bigDecimal.stripTrailingZeros.toPlainString)
