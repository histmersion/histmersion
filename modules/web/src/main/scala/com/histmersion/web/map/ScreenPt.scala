package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A screen point, CSS pixels from the map container's top-left corner. */
final case class ScreenPt(x: ScreenPt.X, y: ScreenPt.Y)

object ScreenPt:
  type X = X.T
  object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = Y.T
  object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
