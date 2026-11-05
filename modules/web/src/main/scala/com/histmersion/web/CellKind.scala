package com.histmersion.web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** The cells of a tiled layer drawn as bubbles ([[MapView.dots]] draws a cell alone as the bubble of its count): what
  * they count for and where a click goes — into their box.
  */
final class CellKind(val dot: DotKind) extends PointKind[TileCellPoint]:
  def shape: Shape = Shape.Circle
  def icon: Icon = Icon.PointSite
  def layerLabel: com.histmersion.web.CellKind.LayerLabelResult = dot.many
  def meaning: com.histmersion.web.CellKind.MeaningResult = s"several ${dot.many} nearby"
  def box: (com.histmersion.web.CellKind.BoxResultPart1, com.histmersion.web.CellKind.BoxResultPart2) = (0, 0)
  def key(c: TileCellPoint): com.histmersion.web.CellKind.KeyResult = c.key
  def at(c: TileCellPoint): (com.histmersion.web.CellKind.AtResultPart1, com.histmersion.web.CellKind.AtResultPart2) = (c.lat, c.lon)
  def inner(c: TileCellPoint): com.histmersion.web.CellKind.InnerResult = c.n.toString
  def fill(c: TileCellPoint, span: (com.histmersion.web.CellKind.FillSpanPart1, com.histmersion.web.CellKind.FillSpanPart2)): com.histmersion.web.CellKind.FillResult =
    MapView.rampEnds(c.midLo.toList ++ c.midHi, span, Certainty.NoSay)._1
  def member(c: TileCellPoint): Member =
    Member(dot, c.n, (c.midLo.toList ++ c.midHi).distinct, Certainty.NoSay, cell = Some(c.box), undated = c.undated)
  override def classes(c: TileCellPoint): String = if c.undated == c.n then MapView.UndatedClass else ""
  def label(c: TileCellPoint): com.histmersion.web.CellKind.LabelResult = DotCluster.summary(Map(dot -> c.n), c.undated)
  def action(c: TileCellPoint): com.histmersion.web.CellKind.ActionResult = "zoom in"
  def target(c: TileCellPoint, asOf: Option[com.histmersion.web.CellKind.TargetAsOf]): Option[Preview.Target] = None
  def activate(c: TileCellPoint, how: Activation): Unit = MapView.zoomInto(List(c.box))
  def open(key: com.histmersion.web.CellKind.OpenKey, c: TileCellPoint, row: dom.Element): Unit = MapView.zoomInto(List(c.box))
  override def recolour(m: DomMarker, c: TileCellPoint, span: (Long, Long)): Unit = MapView.repaintCell(m, member(c))

object CellKind:
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
  type TargetAsOf = TargetAsOf.T
  object TargetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKey = OpenKey.T
  object OpenKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
