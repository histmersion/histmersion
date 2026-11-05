package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A map event: where the pointer was (pointer events), and the browser's own event. */
final case class MapEvent(point: Option[ScreenPt] = None, at: Option[LatLon] = None, native: Option[dom.Event] = None):
  /** Was it over one of the app's markers (a point, a bubble, a name, a popup, a control)? Then the marker answers it,
    * not the map.
    */
  def overMarker: com.histmersion.web.map.MapEvent.OverMarkerResult = native.flatMap(e => Option(e.target)).exists { t =>
    t.isInstanceOf[dom.Element] && t.asInstanceOf[dom.Element].closest(".hd-marker, .hd-popup, .hd-map-control") != null
  }
  def clientXY: Option[(com.histmersion.web.map.MapEvent.ClientXYResultPart1, com.histmersion.web.map.MapEvent.ClientXYResultPart2)] = native.collect { case e: dom.MouseEvent => (e.clientX, e.clientY) }

object MapEvent:
  type OverMarkerResult = OverMarkerResult.T
  object OverMarkerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClientXYResultPart1 = ClientXYResultPart1.T
  object ClientXYResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClientXYResultPart2 = ClientXYResultPart2.T
  object ClientXYResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
