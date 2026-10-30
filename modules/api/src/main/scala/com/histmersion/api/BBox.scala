package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

/** `minLon > maxLon`: the box crosses the antimeridian. */
final case class BBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)
    derives KindlingsCodecAsObject,
      KindlingsSchema:
  def crossesAntimeridian: Boolean = minLon > maxLon

  def contains(lon: Double, lat: Double): Boolean =
    val latOk = lat >= minLat && lat <= maxLat
    val lonOk = if crossesAntimeridian then lon >= minLon || lon <= maxLon else lon >= minLon && lon <= maxLon
    latOk && lonOk

object BBox:
  given Schema[BBox] = summon[KindlingsSchema[BBox]].schema

  def parse(s: String): Option[BBox] =
    val parts = s.split(",").map(_.trim).toList
    parts.traverseDouble.filter(_.length == 4).map(a => BBox(a(0), a(1), a(2), a(3)))

  extension (xs: List[String])
    private def traverseDouble: Option[Array[Double]] =
      val ds = xs.flatMap(_.toDoubleOption)
      if ds.length == xs.length then Some(ds.toArray) else None
