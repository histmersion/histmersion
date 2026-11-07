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
  def layerLabel: com.histmersion.web.DotKind.Many = dot.many
  def meaning: com.histmersion.web.CellKind.Meaning = s"several ${dot.many} nearby"
  def box: (com.histmersion.web.CellKind.BoxX, com.histmersion.web.CellKind.BoxY) = (com.histmersion.web.CellKind.BoxX(0), com.histmersion.web.CellKind.BoxY(0))
  def key(c: TileCellPoint): com.histmersion.web.CellKind.Key = c.key
  def at(c: TileCellPoint): (com.histmersion.api.catalogue.Latitude, com.histmersion.api.catalogue.Longitude) = (c.lat, c.lon)
  def inner(c: TileCellPoint): com.histmersion.web.CellKind.CountLabel = c.n.toString
  def fill(c: TileCellPoint, span: (com.histmersion.api.catalogue.Period.Start, com.histmersion.api.catalogue.Period.End)): com.histmersion.web.CellKind.FillStyle =
    MapView.rampEnds(c.midLo.toList ++ c.midHi, span, Certainty.NoSay)._1
  def member(c: TileCellPoint): Member =
    Member(dot, c.n, (c.midLo.toList ++ c.midHi).distinct, Certainty.NoSay, cell = Some(c.box), undated = c.undated)
  override def classes(c: TileCellPoint): String = if c.undated == c.n then MapView.UndatedClass else ""
  def label(c: TileCellPoint): com.histmersion.web.CellKind.Label = DotCluster.summary(Map(dot -> c.n), c.undated)
  def action(c: TileCellPoint): com.histmersion.web.CellKind.ActionLabel = com.histmersion.web.CellKind.ActionLabel("zoom in")
  def target(c: TileCellPoint, asOf: Option[com.histmersion.api.catalogue.Year]): Option[Preview.Target] = None
  def activate(c: TileCellPoint, how: Activation): Unit = MapView.zoomInto(List(c.box))
  def open(key: com.histmersion.web.CellKind.Key, c: TileCellPoint, row: dom.Element): Unit = MapView.zoomInto(List(c.box))
  override def recolour(m: DomMarker, c: TileCellPoint, span: (Long, Long)): Unit = MapView.repaintCell(m, member(c))

object CellKind:
  type ActionLabel = ActionLabel.T
  object ActionLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoxX = BoxX.T
  object BoxX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoxY = BoxY.T
  object BoxY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLabel = CountLabel.T
  object CountLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillStyle = FillStyle.T
  object FillStyle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Meaning = Meaning.T
  object Meaning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
