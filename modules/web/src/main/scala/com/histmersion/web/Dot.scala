package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** One point on the map, of any kind: what the map's ONE cluster layer ([[MapView.dots]]) holds. Its item and span
  * change in place when only its colour moved with the span.
  */
final class Dot(val key: String, val layer: PointLayer[?], var item: Any, var span: (Long, Long), var member: Member):
  def at: LatLon = layer.at(this)
