package com.histmersion
package util.settings

import com.histmersion.api.Problem
import com.histmersion.config.{AppConfig, LoadedConfig}
import ox.{forkDiscard, useInScope}

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure

/** Where a reload reads the config again and how it fits it to this process.
  *
  * @param read
  *   the config file read again — with the environment read once at startup (a native image reads the environment
  *   through StackValue, on the main thread only)
  * @param adjust
  *   the bundled runtime's rewrite (its Postgres, converter, directories), or nothing
  * @param prepare
  *   what must exist before a pool can open on a database (the bundle creates it)
  */
final case class ConfigSource(
    read: () => Either[Problem, LoadedConfig],
    adjust: AppConfig => AppConfig = identity,
    prepare: AppConfig => Either[Problem, Unit] = _ => Right(())
)

