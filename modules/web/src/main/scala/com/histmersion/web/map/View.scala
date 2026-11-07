package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The view as the markers are placed in it, read once a frame. */
final case class View(surface: MapSurface, centreLon: LatLon.Lon, w: MapSurface.Dimension, h: MapSurface.Dimension)

object View:
  def of(s: MapSurface): View =
    val (w, h) = s.size
    View(s, s.center.lon, w, h)
