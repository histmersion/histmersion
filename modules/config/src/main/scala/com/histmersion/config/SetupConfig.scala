package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The first-run setup (`setup`): `completed` once it was finished or skipped. */
final case class SetupConfig(completed: SetupConfig.Completed) derives ConfigReader

object SetupConfig:
  type Completed = Completed.T
  object Completed extends RefinedType[Boolean, Pure]
