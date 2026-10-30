package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec as CirceCodec, Decoder, Encoder}
import sttp.tapir.Schema

/** A lon/lat viewport (WGS84). `minLon > maxLon` means the box crosses the antimeridian and is evaluated as two
  * envelopes (see `FilterSql`).
  */
final case class BBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)
    derives KindlingsCodecAsObject,
      KindlingsSchema:
  def crossesAntimeridian: Boolean = minLon > maxLon

  /** Point-in-box test with the antimeridian rule. Mirrors the SQL exactly. */
  def contains(lon: Double, lat: Double): Boolean =
    val latOk = lat >= minLat && lat <= maxLat
    val lonOk = if crossesAntimeridian then lon >= minLon || lon <= maxLon else lon >= minLon && lon <= maxLon
    latOk && lonOk

object BBox:
  given Schema[BBox] = summon[KindlingsSchema[BBox]].schema

  /** `minLon,minLat,maxLon,maxLat` (the CLI/UI spelling). */
  def parse(s: String): Option[BBox] =
    val parts = s.split(",").map(_.trim).toList
    parts.traverseDouble.filter(_.length == 4).map(a => BBox(a(0), a(1), a(2), a(3)))

  extension (xs: List[String])
    private def traverseDouble: Option[Array[Double]] =
      val ds = xs.flatMap(_.toDoubleOption)
      if ds.length == xs.length then Some(ds.toArray) else None
