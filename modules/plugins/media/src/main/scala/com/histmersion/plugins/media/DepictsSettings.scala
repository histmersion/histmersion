package com.histmersion.plugins.media

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.media.depicts`: pictures kept per event, P180 files looked at, and whether P1299 is read. */
final case class DepictsSettings(perEvent: DepictsSettings.PerEvent, searchLimit: DepictsSettings.SearchLimit, depictedBy: DepictsSettings.DepictedBy) derives ConfigReader

object DepictsSettings:
  type PerEvent = PerEvent.T
  object PerEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepictedBy = DepictedBy.T
  object DepictedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
