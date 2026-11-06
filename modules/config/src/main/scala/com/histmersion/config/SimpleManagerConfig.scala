package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A library-wide or per-area manager with nothing but its runs at once to set: the enrich manager
  * (`agents.managers.enrich`) and the seeding managers (`agents.managers.seed`).
  */
final case class SimpleManagerConfig(maxRuns: SimpleManagerConfig.MaxRuns) derives ConfigReader

object SimpleManagerConfig:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
