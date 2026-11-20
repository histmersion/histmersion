package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `calibre` is the host-side Calibre integration (see [[CalibreCliConfig]]); the server never uses it. `tokenFile`:
  * the file whose first line is the API key the CLI sends when `HISTMERSION_TOKEN` is not set (empty: none).
  * `followTimeoutSeconds`: how long a command waits for the job or backup it started.
  */
final case class CliConfig(
    serverUrl: CliConfig.ServerUrl,
    tokenFile: CliConfig.TokenFile,
    followTimeoutSeconds: CliConfig.FollowTimeoutSeconds,
    calibre: CalibreCliConfig,
    plugins: CliPluginsConfig
) derives ConfigReader

object CliConfig:
  type ServerUrl = ServerUrl.T
  object ServerUrl extends Newtype[String, Pure]
  type TokenFile = TokenFile.T
  object TokenFile extends Newtype[String, Pure]
  type FollowTimeoutSeconds = FollowTimeoutSeconds.T
  object FollowTimeoutSeconds extends Newtype[Long, Pure]
