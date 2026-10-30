package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class ConfigReloadConfig(watch: Boolean, watchDebounceMs: Int, drainSeconds: Int) derives ConfigReader
