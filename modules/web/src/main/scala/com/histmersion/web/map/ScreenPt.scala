package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A screen point, CSS pixels from the map container's top-left corner. */
final case class ScreenPt(x: ScreenPt.X, y: ScreenPt.Y)

object ScreenPt:
  type X = X.T
  object X extends RefinedType[Double, Pure]
  type Y = Y.T
  object Y extends RefinedType[Double, Pure]
