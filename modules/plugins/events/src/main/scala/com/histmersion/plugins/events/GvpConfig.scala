package com.histmersion.plugins.events

import hearth.kindlings.sconfigderivation.ConfigReader

/** `events-gvp`'s settings: Histmersion's own `events.*`, which the host supplies. */
final case class GvpConfig(events: GvpEvents) derives ConfigReader
