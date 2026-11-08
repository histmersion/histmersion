package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type MatchScore = MatchScore.T
object MatchScore extends Newtype[Double, Pure]:
  given ConfigReader[MatchScore] = summon[ConfigReader[Double]].emap(MatchScore.either)
