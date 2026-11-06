package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

/** `minLon > maxLon`: the box crosses the antimeridian. */
final case class BBox(minLon: com.histmersion.api.catalogue.Longitude, minLat: com.histmersion.api.catalogue.Latitude, maxLon: com.histmersion.api.catalogue.Longitude, maxLat: com.histmersion.api.catalogue.Latitude)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def crossesAntimeridian: com.histmersion.api.BBox.CrossesAntimeridianResult = (minLon).value > maxLon

  def contains(lon: com.histmersion.api.BBox.ContainsLon, lat: com.histmersion.api.BBox.ContainsLat): com.histmersion.api.BBox.ContainsResult =
    val latOk = (lat).value >= (minLat).value && (lat).value <= maxLat
    val lonOk = if (crossesAntimeridian).value then (lon).value >= (minLon).value || (lon).value <= maxLon else (lon).value >= (minLon).value && (lon).value <= maxLon
    latOk && lonOk

object BBox:


  type CrossesAntimeridianResult = CrossesAntimeridianResult.T
  object CrossesAntimeridianResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsResult = ContainsResult.T
  object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsLon = ContainsLon.T
  object ContainsLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsLat = ContainsLat.T
  object ContainsLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.BBox.ParseS): Option[BBox] =
    val parts = (s).value.split(",").map(_.trim).toList
    parts.traverseDouble.filter(_.length == 4).map(a => BBox(_root_.com.histmersion.api.catalogue.Longitude(a(0)), _root_.com.histmersion.api.catalogue.Latitude(a(1)), _root_.com.histmersion.api.catalogue.Longitude(a(2)), _root_.com.histmersion.api.catalogue.Latitude(a(3))))

  extension (xs: List[String])
    private def traverseDouble: Option[Array[Double]] =
      val ds = xs.flatMap(_.toDoubleOption)
      if ds.length == xs.length then Some(ds.toArray) else None
