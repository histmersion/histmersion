package com.histmersion.web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A screen point, in CSS pixels from the map container's top-left corner. */
@js.native
trait ScreenPoint extends js.Object:
  val x: com.histmersion.web.map.maplibre.ScreenPoint.X = js.native
  val y: com.histmersion.web.map.maplibre.ScreenPoint.Y = js.native

object ScreenPoint:
  type X = X.T
  object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = Y.T
  object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
