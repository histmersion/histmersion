package com.histmersion.web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A longitude and latitude. */
@js.native
trait LngLat extends js.Object:
  val lng: Double = js.native
  val lat: Double = js.native
  def wrap(): LngLat = js.native
