package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Shapes drawn by the map itself (areas, outlines). Updates are DIFFS: a shape whose geometry is the same keeps its
  * tile data, its colour and visibility change in place — so a timeline drag recolours 1 600 states without the map
  * cutting a single tile again.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
trait ShapeSet:
  def id: NoMap.Shapes.Id

  /** Draw exactly these (by id): new ones added, gone ones removed, kept ones restyled. */
  def set(shapes: Seq[ShapeFeature]): Unit

  /** One shape's colour and visibility, in place. */
  def restyle(id: ShapeFeature.Id, colour: ShapeFeature.Colour, alpha: ShapeFeature.Alpha): Unit
  def clear(): Unit = set(Nil)

  /** The ids drawn now. */
  def ids: Seq[ShapeFeature.Id]

  /** How many times the shapes' outlines were sent to the map (a restyle sends none; diagnostics). */
  def uploads: ShapeSet.UploadCount = ShapeSet.UploadCount(0)

  /** A drawn shape's colour and visibility (diagnostics). */
  def styleOf(id: ShapeFeature.Id): Option[(ShapeStyle.Colour, ShapeFeature.Alpha)] = None

object ShapeSet:
  type UploadCount = UploadCount.T
  object UploadCount extends Newtype[Int, Pure]
