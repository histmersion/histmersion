package com.histmersion
package util.settings

import com.histmersion.config.{AppConfig, LoadedConfig}
import ox.{forkDiscard, useInScope}

import scala.concurrent.duration.*
import io.github.iltotore.iron.RefinedType
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
    read: () => Either[ConfigSource.ReadResultPart1, LoadedConfig],
    adjust: AppConfig => AppConfig = identity,
    prepare: AppConfig => Either[ConfigSource.PrepareResultPart1, Unit] = _ => Right(())
)

object ConfigSource:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type ReadResultPart1 = Problem
  val ReadResultPart1: Problem.type = Problem
  type PrepareResultPart1 = Problem
  val PrepareResultPart1: Problem.type = Problem
