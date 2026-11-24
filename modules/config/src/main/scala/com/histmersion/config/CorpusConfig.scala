package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

@movesTo(FeaturePlace.Config)
final case class CorpusConfig(dir: CorpusConfig.Dir) derives ConfigReader

object CorpusConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
