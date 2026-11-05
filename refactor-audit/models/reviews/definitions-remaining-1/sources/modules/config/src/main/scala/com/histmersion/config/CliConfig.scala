package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `calibre` is the host-side Calibre integration (see [[CalibreCliConfig]]); the server never uses it. */
final case class CliConfig(serverUrl: CliConfig.ServerUrl, calibre: CalibreCliConfig, plugins: CliPluginsConfig) derives ConfigReader

object CliConfig:
  type ServerUrl = ServerUrl.T
  object ServerUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
