package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** How a point was activated: a click (where on the map) or the keyboard (on which element). */
final case class Activation(keyboard: Activation.Keyboard, element: Option[dom.Element], latlng: Option[LatLon])

object Activation:
  type Keyboard = Keyboard.T
  object Keyboard extends RefinedType[Boolean, Pure]
