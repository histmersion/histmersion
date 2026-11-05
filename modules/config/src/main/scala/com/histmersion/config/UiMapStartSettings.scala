package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Where the map opens (`ui.map-start`): `mode` "last" (where the browser left it; here the first time) or "place"
  * (always here).
  */
final case class UiMapStartSettings(mode: UiMapStartSettings.Mode, lat: UiMapStartSettings.Lat, lon: UiMapStartSettings.Lon, zoom: UiMapStartSettings.Zoom, label: UiMapStartSettings.Label)
    derives ConfigReader

object UiMapStartSettings:
  type Mode = Mode.T
  object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lat = Lat.T
  object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lon = Lon.T
  object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
