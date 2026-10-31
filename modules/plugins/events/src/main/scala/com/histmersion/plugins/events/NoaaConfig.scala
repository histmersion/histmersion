package com.histmersion.plugins.events

import hearth.kindlings.sconfigderivation.ConfigReader

/** `events-noaa`'s settings: Histmersion's own `events.noaa.*`, which the host supplies. */
final case class NoaaConfig(events: NoaaEvents) derives ConfigReader
