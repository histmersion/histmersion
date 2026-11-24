package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** A library-wide or per-area manager with nothing but its runs at once to set: the enrich manager
  * (`agents.managers.enrich`) and the seeding managers (`agents.managers.seed`).
  */
@movesTo(FeaturePlace.AutomationManagers)
final case class SimpleManagerConfig(maxRuns: SimpleManagerConfig.MaxRuns) derives ConfigReader

object SimpleManagerConfig:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
