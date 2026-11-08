package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import com.histmersion.web.map.ScreenPt

/** A screen point, in CSS pixels from the map container's top-left corner. */
@js.native
trait ScreenPoint extends js.Object:
  val x: ScreenPt.X = js.native
  val y: ScreenPt.Y = js.native
