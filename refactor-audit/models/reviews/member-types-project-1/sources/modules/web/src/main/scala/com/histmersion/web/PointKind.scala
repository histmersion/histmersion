package com.histmersion.web

import com.histmersion.api.MapStack
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** What one KIND of point is (docs/map-layers.md): everything the map does with a point that differs between findings,
  * sites, events and pictures, as data. [[PointLayer]] does the rest, the same way for all of them.
  */
trait PointKind[A]:
  /** What a cluster bubble counts it as. */
  def dot: DotKind
  def shape: Shape

  /** Its layer switch's icon: its shape, outlined (the toolbar, the map's key). */
  def icon: Icon

  /** Its name in the toolbar, and what one marker means in the map's key. */
  def layerLabel: com.histmersion.web.PointKind.LayerLabelResult
  def meaning: com.histmersion.web.PointKind.MeaningResult

  /** The marker's box, in px (the shape is drawn inside it at the same optical size for every kind). */
  def box: (com.histmersion.web.PointKind.BoxResultPart1, com.histmersion.web.PointKind.BoxResultPart2)

  /** The pane its markers are drawn in (above the areas). */
  def pane: MapStack.Pane = MapStack.Pane.Markers
  def key(a: A): com.histmersion.web.PointKind.KeyResult
  def at(a: A): (com.histmersion.web.PointKind.AtResultPart1, com.histmersion.web.PointKind.AtResultPart2)

  /** The badge's content: its glyph ([[Glyphs.badge]]; the exclamation mark when its type is unknown), or a count.
    */
  def inner(a: A): com.histmersion.web.PointKind.InnerResult

  /** Classes beyond the shape — its tier, its basis, its period-span ring. */
  def classes(a: A): com.histmersion.web.PointKind.ClassesResult = ""

  /** Data attributes (`data-…`), each with a leading space. */
  def attrs(a: A): com.histmersion.web.PointKind.AttrsResult = ""

  /** Its fill for the span shown: the period colour of its dates (a gradient over several). */
  def fill(a: A, span: (com.histmersion.web.PointKind.FillSpanPart1, com.histmersion.web.PointKind.FillSpanPart2)): com.histmersion.web.PointKind.FillResult

  /** What it counts for in a bubble, and its row in the bubble's list. */
  def member(a: A): Member

  /** Its accessible name, and what activating it does ("pin its card"). */
  def label(a: A): com.histmersion.web.PointKind.LabelResult
  def action(a: A): com.histmersion.web.PointKind.ActionResult

  /** A native tooltip, only where it says something the card does not. */
  def title(a: A): Option[com.histmersion.web.PointKind.TitleResult] = None

  /** The card its hover shows, read at the map's year. */
  def target(a: A, asOf: Option[com.histmersion.web.PointKind.TargetAsOf]): Option[Preview.Target]
  def zIndex(a: A): com.histmersion.web.PointKind.ZIndexResult = 0

  /** A click or Enter on it. */
  def activate(a: A, how: Activation): Unit

  /** Its row in a bubble's list chosen (the row given, to pin a card under); `key` its point's key in the map's cluster
    * layer (to bring it out of its bubble).
    */
  def open(key: com.histmersion.web.PointKind.OpenKey, a: A, row: dom.Element): Unit

  /** The client-side part of its filter (a menu the server is not asked about). */
  def keep(a: A): com.histmersion.web.PointKind.KeepResult = true

  /** Its marker drawn alone recoloured in place for `span` (only the colour moved): the badge's `--c`. */
  def recolour(m: DomMarker, a: A, span: (com.histmersion.web.PointKind.RecolourSpanPart1, com.histmersion.web.PointKind.RecolourSpanPart2)): Unit =
    Option(m.el.querySelector(".hd-point"))
      .foreach(_.asInstanceOf[dom.html.Element].style.setProperty("--c", fill(a, span)))

object PointKind:
  type LayerLabelResult = LayerLabelResult.T
  object LayerLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MeaningResult = MeaningResult.T
  object MeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoxResultPart1 = BoxResultPart1.T
  object BoxResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoxResultPart2 = BoxResultPart2.T
  object BoxResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AtResultPart1 = AtResultPart1.T
  object AtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AtResultPart2 = AtResultPart2.T
  object AtResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InnerResult = InnerResult.T
  object InnerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassesResult = ClassesResult.T
  object ClassesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttrsResult = AttrsResult.T
  object AttrsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillResult = FillResult.T
  object FillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillSpanPart1 = FillSpanPart1.T
  object FillSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillSpanPart2 = FillSpanPart2.T
  object FillSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionResult = ActionResult.T
  object ActionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetAsOf = TargetAsOf.T
  object TargetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZIndexResult = ZIndexResult.T
  object ZIndexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKey = OpenKey.T
  object OpenKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepResult = KeepResult.T
  object KeepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecolourSpanPart1 = RecolourSpanPart1.T
  object RecolourSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecolourSpanPart2 = RecolourSpanPart2.T
  object RecolourSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
