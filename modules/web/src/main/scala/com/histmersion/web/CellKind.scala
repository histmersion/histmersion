package com.histmersion
package web

import com.histmersion.api.{Geo, Years}
import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.UiLabels

/** The cells of a tiled layer drawn as bubbles ([[MapView.dots]] draws a cell alone as the bubble of its count): what
  * they count for and where a click goes — into their box.
  */
final class CellKind(val dot: DotKind) extends PointKind[TileCellPoint]:
  def shape: Shape = Shape.Circle
  def icon: Icon = Icon.PointSite
  def layerLabel: UiLabels.Text = UiLabels.Text(dot.many.value)
  def meaning: UiLabels.Text = UiLabels.Text(s"several ${dot.many} nearby")
  def box: (PointKind.BoxPixels, PointKind.BoxPixels) = (PointKind.BoxPixels(0), PointKind.BoxPixels(0))
  def key(c: TileCellPoint): PointKind.PointKey = PointKind.PointKey(c.key.value)
  def at(c: TileCellPoint): (Geo.Latitude, Geo.Longitude) = (Geo.Latitude(c.lat.value), Geo.Longitude(c.lon.value))
  def inner(c: TileCellPoint): String = c.n.value.toString
  def fill(c: TileCellPoint, span: (Years.Astronomical, Years.Astronomical)): String =
    MapView.rampEnds((c.midLo.toList.map(m => MapView.Mid(m.value)) ++ c.midHi.map(m => MapView.Mid(m.value))), span, Certainty.NoSay)._1.value
  def member(c: TileCellPoint): Member =
    Member(
      dot,
      Member.N(c.n.value),
      (c.midLo.toList.map(m => Member.Mids(m.value)) ++ c.midHi.map(m => Member.Mids(m.value))).distinct,
      Certainty.NoSay,
      cell = Some(c.box),
      undated = Member.Undated(c.undated.value)
    )
  override def classes(c: TileCellPoint): String = if c.undated.value == c.n.value then MapView.UndatedClass else ""
  def label(c: TileCellPoint): UiLabels.Text =
    UiLabels.Text(DotCluster.summary(Map(dot -> DotCluster.KindCount(c.n.value)), DotCluster.Undated(c.undated.value)).value)
  def action(c: TileCellPoint): UiLabels.Text = UiLabels.Text("zoom in")
  def target(c: TileCellPoint, asOf: Option[Years.Astronomical]): Option[Preview.Target] = None
  def activate(c: TileCellPoint, how: Activation): Unit = MapView.zoomInto(List(c.box))
  def open(key: PointKind.PointKey, c: TileCellPoint, row: dom.Element): Unit = MapView.zoomInto(List(c.box))
  override def recolour(m: DomMarker, c: TileCellPoint, span: (Years.Astronomical, Years.Astronomical)): Unit = MapView.repaintCell(m, member(c))

