package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventGeometry:
  def point(lat: Double, lon: Double): Either[String, String] =
    if lat.isNaN || lat < -90 || lat > 90 then Left(s"lat $lat is not between -90 and 90")
    else if lon.isNaN || lon < -180 || lon > 180 then Left(s"lon $lon is not between -180 and 180")
    else Right(s"""{"type":"Point","coordinates":[${fmt(lon)},${fmt(lat)}]}""")

  def of(geometry: Option[String], lat: Option[Double], lon: Option[Double]): Either[String, Option[String]] =
    (geometry.map(_.trim).filter(_.nonEmpty), lat, lon) match
      case (Some(_), Some(_), _) | (Some(_), _, Some(_)) => Left("give geometry or lat/lon, not both")
      case (g @ Some(_), None, None)                     => Right(g)
      case (None, Some(a), Some(o))                      => point(a, o).map(Some(_))
      case (None, None, None)                            => Right(None)
      case _                                             => Left("lat and lon go together")

  /** The GeoJSON geometry types of RFC 7946 (a Feature is not one). */
  val GeometryTypes: Set[String] =
    Set("Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon", "GeometryCollection")

  private val WktShape =
    "(?is)^(SRID=\\d+;\\s*)?(POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION)\\b.*".r

  private val Example = """{"type":"Point","coordinates":[<lon>,<lat>]}"""

  def check(g: String): Either[String, String] =
    val t = g.trim
    if WktShape.matches(t) then Left(s"geometry is GeoJSON, not WKT: write $Example (or give lat/lon)")
    else
      io.circe.parser.parse(t) match
        case Left(_)  => Left(s"geometry is not GeoJSON: expected an object such as $Example")
        case Right(j) =>
          j.asObject.flatMap(_("type")).flatMap(_.asString) match
            case Some(tp) if GeometryTypes(tp)                => Right(t)
            case Some(tp @ ("Feature" | "FeatureCollection")) =>
              Left(s"geometry is a GeoJSON geometry, not a $tp: give its \"geometry\" member")
            case Some(tp) => Left(s"geometry type '$tp' is not one of ${GeometryTypes.toList.sorted.mkString(", ")}")
            case None     => Left(s"geometry has no \"type\": expected an object such as $Example")

  // a plain decimal, never `1.0E-4`, and never with a locale's comma
  private def fmt(d: Double): String = BigDecimal(d).bigDecimal.stripTrailingZeros.toPlainString
