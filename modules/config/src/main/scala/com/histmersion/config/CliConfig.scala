package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `calibre` is the host-side Calibre integration (see [[CalibreCliConfig]]); the server never uses it. */
final case class CliConfig(serverUrl: String, calibre: CalibreCliConfig, plugins: CliPluginsConfig) derives ConfigReader
