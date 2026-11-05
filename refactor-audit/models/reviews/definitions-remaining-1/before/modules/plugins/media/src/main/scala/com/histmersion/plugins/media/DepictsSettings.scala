package com.histmersion.plugins.media

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.media.depicts`: pictures kept per event, P180 files looked at, and whether P1299 is read. */
final case class DepictsSettings(perEvent: Int, searchLimit: Int, depictedBy: Boolean) derives ConfigReader
