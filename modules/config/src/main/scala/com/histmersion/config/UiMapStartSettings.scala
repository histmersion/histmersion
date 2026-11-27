package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Where the map opens (`ui.map-start`): `mode` "last" (where the browser left it; here the first time) or "place"
  * (always here).
  */
@movesTo(FeaturePlace.ServerInstance)
final case class UiMapStartSettings(
    mode: UiMapStartSettings.Mode,
    lat: UiMapStartSettings.Lat,
    lon: UiMapStartSettings.Lon,
    zoom: UiMapStartSettings.Zoom,
    label: UiMapStartSettings.Label
) derives ConfigReader

object UiMapStartSettings:
  type Mode = Mode.T
  object Mode extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Lat = Latitude
  val Lat: Latitude.type = Latitude
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Lon = Longitude
  val Lon: Longitude.type = Longitude
  type Zoom = Zoom.T
  object Zoom extends Newtype[Double, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
