package com.histmersion.plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** What `events-gvp` reads of `histmersion.events`: the earliest year an import starts from, and its own block. */
final case class GvpEvents(minYear: GvpEvents.MinYear, gvp: GvpSettings) derives ConfigReader

object GvpEvents:
  type MinYear = MinYear.T
  object MinYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
