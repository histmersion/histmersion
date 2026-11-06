package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object LocalFadeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionalFadeSeconds = RegionalFadeSeconds.T
  object RegionalFadeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapLimit = MapLimit.T
  object MapLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutlineToleranceDeg = OutlineToleranceDeg.T
  object OutlineToleranceDeg extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
