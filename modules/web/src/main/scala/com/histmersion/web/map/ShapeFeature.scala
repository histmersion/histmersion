package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** One shape: its id (unique in its set), GeoJSON geometry, colour, whether it is drawn dashed, and its visibility
  * (0…1).
  */
final case class ShapeFeature(
    id: ShapeFeature.Id,
    geometry: js.Any,
    colour: ShapeFeature.Colour = com.histmersion.web.map.ShapeFeature.Colour(""),
    dashed: ShapeFeature.Dashed = com.histmersion.web.map.ShapeFeature.Dashed(false),
    alpha: ShapeFeature.Alpha = 1.0
)

object ShapeFeature:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Colour = Colour.T
  object Colour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dashed = Dashed.T
  object Dashed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Alpha = Alpha.T
  object Alpha extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
