package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** One shape: its id (unique in its set), GeoJSON geometry, colour, whether it is drawn dashed, and its visibility
  * (0…1).
  */
final case class ShapeFeature(
    id: ShapeFeature.Id,
    geometry: js.Any,
    colour: ShapeFeature.Colour = ShapeFeature.Colour(""),
    dashed: ShapeFeature.Dashed = ShapeFeature.Dashed(false),
    alpha: ShapeFeature.Alpha = ShapeFeature.Alpha(1.0)
)

object ShapeFeature:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Colour = Colour.T
  object Colour extends Newtype[String, Pure]
  type Dashed = Dashed.T
  object Dashed extends Newtype[Boolean, Pure]
  type Alpha = Alpha.T
  object Alpha extends Newtype[Double, Pure]
