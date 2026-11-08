package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import com.histmersion.api.Geo

/** A longitude and latitude. */
@js.native
trait LngLat extends js.Object:
  val lng: Geo.Longitude = js.native
  val lat: Geo.Latitude = js.native
  def wrap(): LngLat = js.native
