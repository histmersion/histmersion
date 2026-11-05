package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** Shapes drawn by the map itself (areas, outlines). Updates are DIFFS: a shape whose geometry is the same keeps its
  * tile data, its colour and visibility change in place — so a timeline drag recolours 1 600 states without the map
  * cutting a single tile again.
  */
trait ShapeSet:
  def id: String

  /** Draw exactly these (by id): new ones added, gone ones removed, kept ones restyled. */
  def set(shapes: Seq[ShapeFeature]): Unit

  /** One shape's colour and visibility, in place. */
  def restyle(id: String, colour: String, alpha: Double): Unit
  def clear(): Unit = set(Nil)

  /** The ids drawn now. */
  def ids: Seq[String]

  /** How many times the shapes' outlines were sent to the map (a restyle sends none; diagnostics). */
  def uploads: Int = 0

  /** A drawn shape's colour and visibility (diagnostics). */
  def styleOf(id: String): Option[(String, Double)] = None
