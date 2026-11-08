package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** One point on the map, of any kind: what the map's ONE cluster layer ([[MapView.dots]]) holds. Its item and span
  * change in place when only its colour moved with the span.
  */
final class Dot(val key: Dot.Key, val layer: PointLayer[?], var item: Any, var span: (Dot.SpanPart1, Dot.SpanPart2), var member: Member):
  def at: LatLon = layer.at(this)

object Dot:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type SpanPart1 = SpanPart1.T
  object SpanPart1 extends Newtype[Long, Pure]
  type SpanPart2 = SpanPart2.T
  object SpanPart2 extends Newtype[Long, Pure]
