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
  def layerLabel: String = dot.many
  def meaning: String = s"several ${dot.many} nearby"
  def box: (Int, Int) = (0, 0)
  def key(c: TileCellPoint): String = c.key
  def at(c: TileCellPoint): (Double, Double) = (c.lat, c.lon)
  def inner(c: TileCellPoint): String = c.n.toString
  def fill(c: TileCellPoint, span: (Long, Long)): String =
    MapView.rampEnds(c.midLo.toList ++ c.midHi, span, Certainty.NoSay)._1
  def member(c: TileCellPoint): Member =
    Member(dot, c.n, (c.midLo.toList ++ c.midHi).distinct, Certainty.NoSay, cell = Some(c.box), undated = c.undated)
  override def classes(c: TileCellPoint): String = if c.undated == c.n then MapView.UndatedClass else ""
  def label(c: TileCellPoint): String = DotCluster.summary(Map(dot -> c.n), c.undated)
  def action(c: TileCellPoint): String = "zoom in"
  def target(c: TileCellPoint, asOf: Option[Long]): Option[Preview.Target] = None
  def activate(c: TileCellPoint, how: Activation): Unit = MapView.zoomInto(List(c.box))
  def open(key: String, c: TileCellPoint, row: dom.Element): Unit = MapView.zoomInto(List(c.box))
  override def recolour(m: DomMarker, c: TileCellPoint, span: (Long, Long)): Unit = MapView.repaintCell(m, member(c))
