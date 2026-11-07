package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** Shapes drawn by the map itself (areas, outlines). Updates are DIFFS: a shape whose geometry is the same keeps its
  * tile data, its colour and visibility change in place — so a timeline drag recolours 1 600 states without the map
  * cutting a single tile again.
  */
trait ShapeSet:
  def id: com.histmersion.web.map.NoMap.Shapes.Id

  /** Draw exactly these (by id): new ones added, gone ones removed, kept ones restyled. */
  def set(shapes: Seq[ShapeFeature]): Unit

  /** One shape's colour and visibility, in place. */
  def restyle(id: com.histmersion.web.map.ShapeFeature.Id, colour: com.histmersion.web.map.ShapeFeature.Colour, alpha: com.histmersion.web.map.ShapeFeature.Alpha): Unit
  def clear(): Unit = set(Nil)

  /** The ids drawn now. */
  def ids: Seq[com.histmersion.web.map.ShapeFeature.Id]

  /** How many times the shapes' outlines were sent to the map (a restyle sends none; diagnostics). */
  def uploads: com.histmersion.web.map.ShapeSet.UploadCount = 0

  /** A drawn shape's colour and visibility (diagnostics). */
  def styleOf(id: com.histmersion.web.map.ShapeFeature.Id): Option[(com.histmersion.web.map.ShapeStyle.Colour, com.histmersion.web.map.ShapeFeature.Alpha)] = None

object ShapeSet:
  type UploadCount = UploadCount.T
  object UploadCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
