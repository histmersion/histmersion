package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

import scala.concurrent.duration.{DurationLong, FiniteDuration}

import CliConfig.{FollowTimeoutSeconds, ServerUrl, TokenFile}

final case class CliConfig(
    serverUrl: ServerUrl,
    tokenFile: TokenFile,
    followTimeoutSeconds: FollowTimeoutSeconds,
    calibre: CalibreCliConfig,
    plugins: CliPluginsConfig
) derives ConfigReader

object CliConfig:
  type ServerUrl = ServerUrl.T
  object ServerUrl extends Newtype[String, Pure]
  type TokenFile = TokenFile.T
  object TokenFile extends Newtype[String, Pure]
  type FollowTimeoutSeconds = FollowTimeoutSeconds.T
  object FollowTimeoutSeconds extends Newtype[Long, Pure]:
    extension (seconds: FollowTimeoutSeconds)
      def duration: Option[FiniteDuration] = Option.when(seconds.value > 0)(seconds.value.seconds)
