package com.histmersion
package plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** What `events-gvp` reads of `histmersion.events`: the earliest year an import starts from, and its own block. */
final case class GvpEvents(minYear: GvpEvents.MinYear, gvp: GvpSettings) derives ConfigReader

object GvpEvents:
  type MinYear = MinYear.T
  object MinYear extends Newtype[Long, Pure]
