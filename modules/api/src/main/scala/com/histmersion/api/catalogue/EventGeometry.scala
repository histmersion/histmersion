package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventGeometry:
  type Geojson = Geojson.T
  object Geojson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Type = Type.T
  object Type extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)

  def point(lat: com.histmersion.api.catalogue.Latitude, lon: com.histmersion.api.catalogue.Longitude): Either[com.histmersion.api.catalogue.EventGeometry.Problem, com.histmersion.api.catalogue.EventGeometry.Geojson] =
    if (lat).value.isNaN || (lat).value < -90 || (lat).value > 90 then Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"lat $lat is not between -90 and 90"))
    else if (lon).value.isNaN || (lon).value < -180 || (lon).value > 180 then Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"lon $lon is not between -180 and 180"))
    else Right(_root_.com.histmersion.api.catalogue.EventGeometry.Geojson(s"""{"type":"Point","coordinates":[${fmt(((lon).value))},${fmt(((lat).value))}]}"""))

  def of(geometry: Option[com.histmersion.api.catalogue.EventGeometry.Geojson], lat: Option[com.histmersion.api.catalogue.Latitude], lon: Option[com.histmersion.api.catalogue.Longitude]): Either[com.histmersion.api.catalogue.EventGeometry.Problem, Option[com.histmersion.api.catalogue.EventGeometry.Geojson]] =
    (geometry.map((_).value.trim).filter(_.nonEmpty), lat, lon) match
      case (Some(_), Some(_), _) | (Some(_), _, Some(_)) => Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem("give geometry or lat/lon, not both"))
      case (g @ Some(_), None, None)                     => Right((g).map(refactorValue => _root_.com.histmersion.api.catalogue.EventGeometry.Geojson(refactorValue)))
      case (None, Some(a), Some(o))                      => point(a, o).map(Some(_))
      case (None, None, None)                            => Right(None)
      case _                                             => Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem("lat and lon go together"))

  /** The GeoJSON geometry types of RFC 7946 (a Feature is not one). */
  val GeometryTypes: Set[com.histmersion.api.catalogue.EventGeometry.Type] =
    Set(_root_.com.histmersion.api.catalogue.EventGeometry.Type("Point"), _root_.com.histmersion.api.catalogue.EventGeometry.Type("MultiPoint"), _root_.com.histmersion.api.catalogue.EventGeometry.Type("LineString"), _root_.com.histmersion.api.catalogue.EventGeometry.Type("MultiLineString"), _root_.com.histmersion.api.catalogue.EventGeometry.Type("Polygon"), _root_.com.histmersion.api.catalogue.EventGeometry.Type("MultiPolygon"), _root_.com.histmersion.api.catalogue.EventGeometry.Type("GeometryCollection"))

  private val WktShape =
    "(?is)^(SRID=\\d+;\\s*)?(POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION)\\b.*".r

  private val Example = """{"type":"Point","coordinates":[<lon>,<lat>]}"""

  def check(g: com.histmersion.api.catalogue.EventGeometry.Geojson): Either[com.histmersion.api.catalogue.EventGeometry.Problem, com.histmersion.api.catalogue.EventGeometry.Geojson] =
    val t = (g).value.trim
    if WktShape.matches(t) then Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"geometry is GeoJSON, not WKT: write $Example (or give lat/lon)"))
    else
      io.circe.parser.parse(t) match
        case Left(_)  => Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"geometry is not GeoJSON: expected an object such as $Example"))
        case Right(j) =>
          j.asObject.flatMap(_("type")).flatMap(_.asString) match
            case Some(tp) if GeometryTypes(_root_.com.histmersion.api.catalogue.EventGeometry.Type(tp))                => Right(_root_.com.histmersion.api.catalogue.EventGeometry.Geojson(t))
            case Some(tp @ ("Feature" | "FeatureCollection")) =>
              Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"geometry is a GeoJSON geometry, not a $tp: give its \"geometry\" member"))
            case Some(tp) => Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"geometry type '$tp' is not one of ${GeometryTypes.toList.sorted.mkString(", ")}"))
            case None     => Left(_root_.com.histmersion.api.catalogue.EventGeometry.Problem(s"geometry has no \"type\": expected an object such as $Example"))

  // a plain decimal, never `1.0E-4`, and never with a locale's comma
  private def fmt(d: Double): String = (BigDecimal((d)).bigDecimal.stripTrailingZeros.toPlainString)
