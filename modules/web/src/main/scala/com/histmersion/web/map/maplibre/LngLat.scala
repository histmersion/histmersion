package com.histmersion.web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A longitude and latitude. */
@js.native
trait LngLat extends js.Object:
  val lng: com.histmersion.web.map.maplibre.LngLat.Lng = js.native
  val lat: com.histmersion.web.map.maplibre.LngLat.Lat = js.native
  def wrap(): LngLat = js.native

object LngLat:
  type Lng = Lng.T
  object Lng extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lat = Lat.T
  object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
