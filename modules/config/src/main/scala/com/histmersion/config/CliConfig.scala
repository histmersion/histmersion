package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

import scala.concurrent.duration.{DurationLong, FiniteDuration}

import CliConfig.{Agent, FollowTimeoutSeconds, ServerUrl, TokenFile}
import CliConfig.Agent.Name

final case class CliConfig(
    serverUrl: ServerUrl,
    tokenFile: TokenFile,
    followTimeoutSeconds: FollowTimeoutSeconds,
    calibre: CalibreCliConfig,
    plugins: CliPluginsConfig,
    agent: Agent
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

  final case class Agent(plugin: Name, workflow: Name, definition: Name, provider: Name, model: Name)
      derives ConfigReader

  object Agent:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
