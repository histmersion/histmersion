package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventGeometry:
  type PointResultPart1 = PointResultPart1.T
  object PointResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointResultPart2 = PointResultPart2.T
  object PointResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointLat = PointLat.T
  object PointLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointLon = PointLon.T
  object PointLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart2 = OfResultPart2.T
  object OfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfGeometry = OfGeometry.T
  object OfGeometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLat = OfLat.T
  object OfLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfLon = OfLon.T
  object OfLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeometryTypes = GeometryTypesValue
  type GeometryTypesValue = GeometryTypesValue.T
  object GeometryTypesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart2 = CheckResultPart2.T
  object CheckResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckG = CheckG.T
  object CheckG extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FmtResult = FmtResult.T
  object FmtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FmtD = FmtD.T
  object FmtD extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def point(lat: com.histmersion.api.catalogue.EventGeometry.PointLat, lon: com.histmersion.api.catalogue.EventGeometry.PointLon): Either[com.histmersion.api.catalogue.EventGeometry.PointResultPart1, com.histmersion.api.catalogue.EventGeometry.PointResultPart2] =
    if lat.isNaN || lat < -90 || lat > 90 then Left(_root_.com.histmersion.api.catalogue.EventGeometry.PointResultPart1(s"lat $lat is not between -90 and 90"))
    else if lon.isNaN || lon < -180 || lon > 180 then Left(_root_.com.histmersion.api.catalogue.EventGeometry.PointResultPart1(s"lon $lon is not between -180 and 180"))
    else Right(_root_.com.histmersion.api.catalogue.EventGeometry.PointResultPart2(s"""{"type":"Point","coordinates":[${fmt(_root_.com.histmersion.api.catalogue.EventGeometry.FmtD((lon).value))},${fmt(_root_.com.histmersion.api.catalogue.EventGeometry.FmtD((lat).value))}]}"""))

  def of(geometry: Option[com.histmersion.api.catalogue.EventGeometry.OfGeometry], lat: Option[com.histmersion.api.catalogue.EventGeometry.OfLat], lon: Option[com.histmersion.api.catalogue.EventGeometry.OfLon]): Either[com.histmersion.api.catalogue.EventGeometry.OfResultPart1, Option[com.histmersion.api.catalogue.EventGeometry.OfResultPart2]] =
    (geometry.map((_).value.trim).filter(_.nonEmpty), lat, lon) match
      case (Some(_), Some(_), _) | (Some(_), _, Some(_)) => Left(_root_.com.histmersion.api.catalogue.EventGeometry.OfResultPart1("give geometry or lat/lon, not both"))
      case (g @ Some(_), None, None)                     => Right((g).map(refactorValue => _root_.com.histmersion.api.catalogue.EventGeometry.OfResultPart2(refactorValue)))
      case (None, Some(a), Some(o))                      => ((point(_root_.com.histmersion.api.catalogue.EventGeometry.PointLat((a).value), _root_.com.histmersion.api.catalogue.EventGeometry.PointLon((o).value)).map(Some(_))).left.map(refactorError => _root_.com.histmersion.api.catalogue.EventGeometry.OfResultPart1((refactorError).value))).map(refactorValue2 => (refactorValue2).map(refactorValue3 => _root_.com.histmersion.api.catalogue.EventGeometry.OfResultPart2((refactorValue3).value)))
      case (None, None, None)                            => Right(None)
      case _                                             => Left(_root_.com.histmersion.api.catalogue.EventGeometry.OfResultPart1("lat and lon go together"))

  /** The GeoJSON geometry types of RFC 7946 (a Feature is not one). */
  val GeometryTypes: Set[com.histmersion.api.catalogue.EventGeometry.GeometryTypes] =
    Set(_root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("Point"), _root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("MultiPoint"), _root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("LineString"), _root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("MultiLineString"), _root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("Polygon"), _root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("MultiPolygon"), _root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypesValue("GeometryCollection"))

  private val WktShape =
    "(?is)^(SRID=\\d+;\\s*)?(POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION)\\b.*".r

  private val Example = """{"type":"Point","coordinates":[<lon>,<lat>]}"""

  def check(g: com.histmersion.api.catalogue.EventGeometry.CheckG): Either[com.histmersion.api.catalogue.EventGeometry.CheckResultPart1, com.histmersion.api.catalogue.EventGeometry.CheckResultPart2] =
    val t = (g).value.trim
    if WktShape.matches(t) then Left(_root_.com.histmersion.api.catalogue.EventGeometry.CheckResultPart1(s"geometry is GeoJSON, not WKT: write $Example (or give lat/lon)"))
    else
      io.circe.parser.parse(t) match
        case Left(_)  => Left(_root_.com.histmersion.api.catalogue.EventGeometry.CheckResultPart1(s"geometry is not GeoJSON: expected an object such as $Example"))
        case Right(j) =>
          j.asObject.flatMap(_("type")).flatMap(_.asString) match
            case Some(tp) if GeometryTypes(_root_.com.histmersion.api.catalogue.EventGeometry.GeometryTypes(tp))                => Right(_root_.com.histmersion.api.catalogue.EventGeometry.CheckResultPart2(t))
            case Some(tp @ ("Feature" | "FeatureCollection")) =>
              Left(_root_.com.histmersion.api.catalogue.EventGeometry.CheckResultPart1(s"geometry is a GeoJSON geometry, not a $tp: give its \"geometry\" member"))
            case Some(tp) => Left(s"geometry type '$tp' is not one of ${GeometryTypes.toList.sorted.mkString(", ")}")
            case None     => Left(_root_.com.histmersion.api.catalogue.EventGeometry.CheckResultPart1(s"geometry has no \"type\": expected an object such as $Example"))

  // a plain decimal, never `1.0E-4`, and never with a locale's comma
  private def fmt(d: com.histmersion.api.catalogue.EventGeometry.FmtD): com.histmersion.api.catalogue.EventGeometry.FmtResult = _root_.com.histmersion.api.catalogue.EventGeometry.FmtResult(BigDecimal((d).value).bigDecimal.stripTrailingZeros.toPlainString)
