package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** Shapes drawn by the map itself (areas, outlines). Updates are DIFFS: a shape whose geometry is the same keeps its
  * tile data, its colour and visibility change in place — so a timeline drag recolours 1 600 states without the map
  * cutting a single tile again.
  */
trait ShapeSet:
  def id: com.histmersion.web.map.ShapeSet.IdResult

  /** Draw exactly these (by id): new ones added, gone ones removed, kept ones restyled. */
  def set(shapes: Seq[ShapeFeature]): Unit

  /** One shape's colour and visibility, in place. */
  def restyle(id: com.histmersion.web.map.ShapeSet.RestyleId, colour: com.histmersion.web.map.ShapeSet.RestyleColour, alpha: com.histmersion.web.map.ShapeSet.RestyleAlpha): Unit
  def clear(): Unit = set(Nil)

  /** The ids drawn now. */
  def ids: Seq[com.histmersion.web.map.ShapeSet.IdsResult]

  /** How many times the shapes' outlines were sent to the map (a restyle sends none; diagnostics). */
  def uploads: com.histmersion.web.map.ShapeSet.UploadsResult = 0

  /** A drawn shape's colour and visibility (diagnostics). */
  def styleOf(id: com.histmersion.web.map.ShapeSet.StyleOfId): Option[(com.histmersion.web.map.ShapeSet.StyleOfResultPart1, com.histmersion.web.map.ShapeSet.StyleOfResultPart2)] = None

object ShapeSet:
  type IdResult = IdResult.T
  object IdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestyleId = RestyleId.T
  object RestyleId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestyleColour = RestyleColour.T
  object RestyleColour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestyleAlpha = RestyleAlpha.T
  object RestyleAlpha extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsResult = IdsResult.T
  object IdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadsResult = UploadsResult.T
  object UploadsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StyleOfResultPart1 = StyleOfResultPart1.T
  object StyleOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StyleOfResultPart2 = StyleOfResultPart2.T
  object StyleOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StyleOfId = StyleOfId.T
  object StyleOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
