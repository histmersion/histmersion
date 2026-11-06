package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** Where the map opens (`ui.map-start`): `mode` "last" (where the browser left it; here the first time) or "place"
  * (always here).
  */
final case class UiMapStartSettings(mode: UiMapStartSettings.Mode, lat: UiMapStartSettings.Lat, lon: UiMapStartSettings.Lon, zoom: UiMapStartSettings.Zoom, label: UiMapStartSettings.Label)
    derives ConfigReader

object UiMapStartSettings:
  type Mode = Mode.T
  object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lat = com.histmersion.Latitude
  val Lat: _root_.com.histmersion.Latitude.type = _root_.com.histmersion.Latitude
  type Lon = com.histmersion.Longitude
  val Lon: _root_.com.histmersion.Longitude.type = _root_.com.histmersion.Longitude
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
