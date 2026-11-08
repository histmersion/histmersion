package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Where the map opens (`ui.map-start`): `mode` "last" (where the browser left it; here the first time) or "place"
  * (always here).
  */
final case class UiMapStartSettings(mode: UiMapStartSettings.Mode, lat: UiMapStartSettings.Lat, lon: UiMapStartSettings.Lon, zoom: UiMapStartSettings.Zoom, label: UiMapStartSettings.Label)
    derives ConfigReader

object UiMapStartSettings:
  type Mode = Mode.T
  object Mode extends Newtype[String, Pure]
  type Lat = Latitude
  val Lat: Latitude.type = Latitude
  type Lon = Longitude
  val Lon: Longitude.type = Longitude
  type Zoom = Zoom.T
  object Zoom extends Newtype[Double, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
