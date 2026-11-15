package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `ui.events`: the events on the map in playback — how long a local and a regional event fade (seconds of playback),
  * the most one playback returns, and how far an area outline is simplified (degrees).
  */
final case class UiEventsSettings(
    localFadeSeconds: UiEventsSettings.LocalFadeSeconds,
    regionalFadeSeconds: UiEventsSettings.RegionalFadeSeconds,
    mapLimit: UiEventsSettings.MapLimit,
    outlineToleranceDeg: UiEventsSettings.OutlineToleranceDeg
) derives ConfigReader

object UiEventsSettings:
  type LocalFadeSeconds = LocalFadeSeconds.T
  object LocalFadeSeconds extends Newtype[Double, Pure]
  type RegionalFadeSeconds = RegionalFadeSeconds.T
  object RegionalFadeSeconds extends Newtype[Double, Pure]
  type MapLimit = MapLimit.T
  object MapLimit extends Newtype[Int, Pure]
  type OutlineToleranceDeg = OutlineToleranceDeg.T
  object OutlineToleranceDeg extends Newtype[Double, Pure]
