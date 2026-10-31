package com.histmersion.plugins.events

import hearth.kindlings.sconfigderivation.ConfigReader

/** What `events-noaa` reads of `histmersion.events`: its own block. */
final case class NoaaEvents(noaa: NoaaSettings) derives ConfigReader
