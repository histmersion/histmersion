package com.histmersion
package plugins.media

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion.media.depicts`: pictures kept per event, P180 files looked at, and whether P1299 is read. */
final case class DepictsSettings(perEvent: DepictsSettings.PerEvent, searchLimit: DepictsSettings.SearchLimit, depictedBy: DepictsSettings.DepictedBy) derives ConfigReader

object DepictsSettings:
  type PerEvent = PerEvent.T
  object PerEvent extends RefinedType[Int, Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends RefinedType[Int, Pure]
  type DepictedBy = DepictedBy.T
  object DepictedBy extends RefinedType[Boolean, Pure]
