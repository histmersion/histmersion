package com.histmersion.plugins.events

import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** What `events-gvp` reads of `histmersion.events`: the earliest year an import starts from, and its own block. */
final case class GvpEvents(minYear: Long, gvp: GvpSettings) derives ConfigReader
