package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
object EventGeometry:
  type Geojson = Geojson.T
  object Geojson extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Type = Type.T
  object Type extends Newtype[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)

  def point(lat: Latitude, lon: Longitude): Either[Problem, Geojson] =
    if (lat).value.isNaN || (lat).value < -90 || (lat).value > 90 then Left(Problem(s"lat $lat is not between -90 and 90"))
    else if (lon).value.isNaN || (lon).value < -180 || (lon).value > 180 then Left(Problem(s"lon $lon is not between -180 and 180"))
    else Right(Geojson(s"""{"type":"Point","coordinates":[${fmt(((lon).value))},${fmt(((lat).value))}]}"""))

  def of(geometry: Option[Geojson], lat: Option[Latitude], lon: Option[Longitude]): Either[Problem, Option[Geojson]] =
    (geometry.map((_).value.trim).filter(_.nonEmpty), lat, lon) match
      case (Some(_), Some(_), _) | (Some(_), _, Some(_)) => Left(Problem("give geometry or lat/lon, not both"))
      case (g @ Some(_), None, None)                     => Right((g).map(refactorValue => Geojson(refactorValue)))
      case (None, Some(a), Some(o))                      => point(a, o).map(Some(_))
      case (None, None, None)                            => Right(None)
      case _                                             => Left(Problem("lat and lon go together"))

  /** The GeoJSON geometry types of RFC 7946 (a Feature is not one). */
  val GeometryTypes: Set[Type] =
    Set(Type("Point"), Type("MultiPoint"), Type("LineString"), Type("MultiLineString"), Type("Polygon"), Type("MultiPolygon"), Type("GeometryCollection"))

  private val WktShape =
    "(?is)^(SRID=\\d+;\\s*)?(POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION)\\b.*".r

  private val Example = """{"type":"Point","coordinates":[<lon>,<lat>]}"""

  def check(g: Geojson): Either[Problem, Geojson] =
    val t = (g).value.trim
    if WktShape.matches(t) then Left(Problem(s"geometry is GeoJSON, not WKT: write $Example (or give lat/lon)"))
    else
      io.circe.parser.parse(t) match
        case Left(_)  => Left(Problem(s"geometry is not GeoJSON: expected an object such as $Example"))
        case Right(j) =>
          j.asObject.flatMap(_("type")).flatMap(_.asString) match
            case Some(tp) if GeometryTypes(Type(tp))                => Right(Geojson(t))
            case Some(tp @ ("Feature" | "FeatureCollection")) =>
              Left(Problem(s"geometry is a GeoJSON geometry, not a $tp: give its \"geometry\" member"))
            case Some(tp) => Left(Problem(s"geometry type '$tp' is not one of ${GeometryTypes.toList.sorted.mkString(", ")}"))
            case None     => Left(Problem(s"geometry has no \"type\": expected an object such as $Example"))

  // a plain decimal, never `1.0E-4`, and never with a locale's comma
  private def fmt(d: Double): String = (BigDecimal((d)).bigDecimal.stripTrailingZeros.toPlainString)
