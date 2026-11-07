package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A map event: where the pointer was (pointer events), and the browser's own event. */
final case class MapEvent(point: Option[ScreenPt] = None, at: Option[LatLon] = None, native: Option[dom.Event] = None):
  /** Was it over one of the app's markers (a point, a bubble, a name, a popup, a control)? Then the marker answers it,
    * not the map.
    */
  def overMarker: Boolean = native.flatMap(e => Option(e.target)).exists { t =>
    t.isInstanceOf[dom.Element] && t.asInstanceOf[dom.Element].closest(".hd-marker, .hd-popup, .hd-map-control") != null
  }
  def clientXY: Option[(com.histmersion.web.map.MapEvent.ClientCoordinate, com.histmersion.web.map.MapEvent.ClientCoordinate)] = native.collect { case e: dom.MouseEvent => (e.clientX, e.clientY) }

object MapEvent:
  type ClientCoordinate = ClientCoordinate.T
  object ClientCoordinate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
