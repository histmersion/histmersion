package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A library-wide or per-area manager with nothing but its runs at once to set: the enrich manager
  * (`agents.managers.enrich`) and the seeding managers (`agents.managers.seed`).
  */
final case class SimpleManagerConfig(maxRuns: SimpleManagerConfig.MaxRuns) derives ConfigReader

object SimpleManagerConfig:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends RefinedType[Int, Pure]
