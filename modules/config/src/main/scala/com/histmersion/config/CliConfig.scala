package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `calibre` is the host-side Calibre integration (see [[CalibreCliConfig]]); the server never uses it. */
final case class CliConfig(serverUrl: CliConfig.ServerUrl, calibre: CalibreCliConfig, plugins: CliPluginsConfig)
    derives ConfigReader

object CliConfig:
  type ServerUrl = ServerUrl.T
  object ServerUrl extends Newtype[String, Pure]
