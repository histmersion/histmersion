package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

/** `minLon > maxLon`: the box crosses the antimeridian. */
final case class BBox(minLon: com.histmersion.api.catalogue.Longitude, minLat: com.histmersion.api.catalogue.Latitude, maxLon: com.histmersion.api.catalogue.Longitude, maxLat: com.histmersion.api.catalogue.Latitude)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def crossesAntimeridian: Boolean = (minLon).value > maxLon

  def contains(lon: com.histmersion.api.Geo.Longitude, lat: com.histmersion.api.Geo.Latitude): Boolean =
    val latOk = (lat).value >= (minLat).value && (lat).value <= maxLat
    val lonOk = if (crossesAntimeridian).value then (lon).value >= (minLon).value || (lon).value <= maxLon else (lon).value >= (minLon).value && (lon).value <= maxLon
    latOk && lonOk

object BBox:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  def parse(s: com.histmersion.api.BBox.Text): Option[BBox] =
    val parts = (s).value.split(",").map(_.trim).toList
    parts.traverseDouble.filter(_.length == 4).map(a => BBox(_root_.com.histmersion.api.catalogue.Longitude(a(0)), _root_.com.histmersion.api.catalogue.Latitude(a(1)), _root_.com.histmersion.api.catalogue.Longitude(a(2)), _root_.com.histmersion.api.catalogue.Latitude(a(3))))

  extension (xs: List[String])
    private def traverseDouble: Option[Array[Double]] =
      val ds = xs.flatMap(_.toDoubleOption)
      if ds.length == xs.length then Some(ds.toArray) else None
