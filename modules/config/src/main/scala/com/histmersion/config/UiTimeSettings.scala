package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The timeline's span (`ui.time`): the earliest and latest year it reaches, and the years it opens on. */
final case class UiTimeSettings(min: Long, max: Long, defaultFrom: Long, defaultTo: Long) derives ConfigReader
