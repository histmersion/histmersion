package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** How a point was activated: a click (where on the map) or the keyboard (on which element). */
final case class Activation(keyboard: Boolean, element: Option[dom.Element], latlng: Option[LatLon])
