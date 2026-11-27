package com.histmersion
package api

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** `minLon > maxLon`: the box crosses the antimeridian. */
@movesTo(FeaturePlace.Kernel)
final case class BBox(minLon: Longitude, minLat: Latitude, maxLon: Longitude, maxLat: Latitude)
    derives JsonCodec,
      KindlingsSchema,
      json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def crossesAntimeridian: Boolean = (minLon).value > (maxLon).value

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contains(lon: Longitude, lat: Latitude): Boolean =
    val latOk = (lat).value >= (minLat).value && (lat).value <= (maxLat).value
    val lonOk = if crossesAntimeridian then (lon).value >= (minLon).value || (lon).value <= (maxLon).value
    else (lon).value >= (minLon).value && (lon).value <= (maxLon).value
    latOk && lonOk

object BBox:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Text): Option[BBox] =
    val parts = (s).value.split(",").map(_.trim).toList
    parts.traverseDouble
      .filter(_.length == 4)
      .map(a => BBox(Longitude(a(0)), Latitude(a(1)), Longitude(a(2)), Latitude(a(3))))

  extension (xs: List[String])
    private def traverseDouble: Option[Array[Double]] =
      val ds = xs.flatMap(_.toDoubleOption)
      if ds.length == xs.length then Some(ds.toArray) else None
