package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.LibraryAcquisition)
type MatchScore = MatchScore.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object MatchScore extends Newtype[Double, Pure]:
  given ConfigReader[MatchScore] = summon[ConfigReader[Double]].emap(MatchScore.either)
