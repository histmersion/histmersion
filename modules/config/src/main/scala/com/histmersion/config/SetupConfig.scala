package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** The first-run setup (`setup`): `completed` once it was finished or skipped. */
@movesTo(FeaturePlace.Config)
final case class SetupConfig(completed: SetupConfig.Completed) derives ConfigReader

object SetupConfig:
  type Completed = Completed.T
  object Completed extends Newtype[Boolean, Pure]
